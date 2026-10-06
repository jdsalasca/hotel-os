// Capturas de las pantallas principales, en móvil y escritorio.
// Datos de demostración: no hay información real de huéspedes ni reservas reales.
import { chromium, devices } from 'playwright';
import { mkdirSync } from 'node:fs';

const BASE = process.env.BASE_URL ?? 'http://web:80';
const SALIDA = process.env.SALIDA ?? '/salida';
mkdirSync(SALIDA, { recursive: true });

const ESCRITORIO = { width: 1440, height: 1000 };
const MOVIL = devices['iPhone 13'];

// Errores de consola y de red: si algo falla al cargar estilos o datos, hay que enterarse,
// no guardar una captura bonita de una pantalla rota.
// El 401 al preguntar por la sesión antes de entrar es el comportamiento correcto del panel.
const IGNORAR = [/Failed to load resource.*401/];
const ignorables = (t) => IGNORAR.some((r) => r.test(t));

const problemas = [];

async function nuevaPagina(browser, opciones) {
  const contexto = await browser.newContext(opciones);
  const pagina = await contexto.newPage();
  pagina.on('console', (m) => {
    if (m.type() === 'error' && !ignorables(m.text())) problemas.push(`consola: ${m.text()}`);
  });
  pagina.on('response', async (r) => {
    if (r.url().includes('/api/') && r.request().method() !== 'GET') {
      const cuerpo = await r.text().catch(() => '');
      if (r.status() >= 400) {
        problemas.push(`api ${r.status()} ${r.url()}: ${cuerpo.slice(0, 200)}`);
      } else {
        console.log(`api ok ${r.status()} ${r.url()} -> ${cuerpo.slice(0, 120)}`);
      }
    }
  });
  return { contexto, pagina };
}

async function guardar(pagina, nombre) {
  // 'networkidle' es frágil en una SPA: una petición abierta puede impedirlo para siempre.
  // Se espera 'load' y la llegada del elemento concreto ya la garantizan los esperares de arriba.
  await pagina.waitForLoadState('load');
  await pagina.waitForTimeout(400);
  await pagina.screenshot({ path: `${SALIDA}/${nombre}.png`, fullPage: true });
  console.log(`ok ${nombre}.png`);
}

/** Si un paso falla, guarda lo que se estaba viendo: un error sin pantalla no se diagnostica. */
async function esperar(pagina, selector, nombre) {
  try {
    await pagina.waitForSelector(selector, { timeout: 20000 });
  } catch (e) {
    await pagina.screenshot({ path: `${SALIDA}/FALLO-${nombre}.png`, fullPage: true });
    const texto = (await pagina.locator('body').innerText()).slice(0, 400);
    throw new Error(`no apareció "${selector}" en ${nombre}. Pantalla:\n${texto}`);
  }
}

async function iniciarSesion(pagina) {
  await pagina.goto(`${BASE}/admin/entrar`);
  await pagina.fill('#admin-email', 'admin');
  await pagina.fill('#admin-clave', 'admin');
  await pagina.click('button[type=submit]');
  await pagina.waitForResponse((r) => r.url().includes('/api/admin/login'));
}

const navegador = await chromium.launch();

for (const [nombre, opciones] of [
  ['escritorio', { viewport: ESCRITORIO }],
  ['movil', MOVIL],
]) {
  const { contexto, pagina } = await nuevaPagina(navegador, opciones);

  // 1. Inicio y búsqueda
  await pagina.goto(BASE);
  await guardar(pagina, `${nombre}-01-inicio`);

  await pagina.fill('#llegada', '2026-12-10');
  await pagina.fill('#salida', '2026-12-13');
  await pagina.fill('#huespedes', '2');
  await pagina.click('button[type=submit]');
  await esperar(pagina, 'text=Habitaciones disponibles', "${nombre}-02-disponibilidad");
  await guardar(pagina, `${nombre}-02-disponibilidad`);

// 2. Selección y formulario
  // Se elige la primera oferta por índice: el texto del botón se repite en varias tarjetas.
  const botones = pagina.locator('text=Elegir esta habitación');
  const cuantas = await botones.count();
  if (cuantas === 0) throw new Error('no hay ofertas que elegir: el inventario de demostración está agotado');
  await botones.first().click();
  await esperar(pagina, '#email', `${nombre}-03-formulario`);
  await guardar(pagina, `${nombre}-03-formulario`);

  await pagina.fill('#email', 'huesped.demo@example.com');
  await pagina.fill('#nombre', 'Huésped Demo');
  await pagina.click('button[type=submit]');
  await esperar(pagina, '.codigo-reserva', `${nombre}-04-confirmacion`);
  await guardar(pagina, `${nombre}-04-confirmacion`);

  // 3. Panel: login y reservas
  await pagina.goto(`${BASE}/admin/entrar`);
  await guardar(pagina, `${nombre}-05-login`);

  await iniciarSesion(pagina);
  await pagina.goto(`${BASE}/admin/reservas`);
  await esperar(pagina, '.tabla, .vacio', "${nombre}-06-panel");
  await guardar(pagina, `${nombre}-06-panel-reservas`);

  // 4. Inventario
  await pagina.goto(`${BASE}/admin/inventario`);
  await esperar(pagina, '#tipo-codigo', `${nombre}-07-inventario`);
  await guardar(pagina, `${nombre}-07-inventario`);

  // La rejilla de precios solo aparece cuando hay plan y tipo elegidos: se eligen los primeros
  // disponibles, que son los que el propio guion acaba de registrar.
  const plan = pagina.locator('#tar-plan option').nth(1);
  const tipo = pagina.locator('#tar-tipo option').nth(1);
  if ((await plan.count()) > 0 && (await tipo.count()) > 0) {
    await pagina.selectOption('#tar-plan', await plan.getAttribute('value'));
    await pagina.selectOption('#tar-tipo', await tipo.getAttribute('value'));
    await esperar(pagina, '[id^="precio-"]', `${nombre}-07b-precios`);
    await guardar(pagina, `${nombre}-07b-precios`);
  }

  // 5. Integraciones: estado real de los canales
  await pagina.goto(`${BASE}/admin/integraciones`);
  await esperar(pagina, 'text=Booking.com', "${nombre}-08-integraciones");
  await guardar(pagina, `${nombre}-08-integraciones`);

  // 6. Indicadores
  await pagina.goto(`${BASE}/admin/indicadores`);
  await esperar(pagina, 'text=Operación estabilizada', "${nombre}-09-indicadores");
  await guardar(pagina, `${nombre}-09-indicadores`);

  await contexto.close();
}

await navegador.close();

if (problemas.length > 0) {
  console.error('PROBLEMAS DETECTADOS:');
  for (const p of [...new Set(problemas)]) console.error(` - ${p}`);
  process.exit(1);
}
console.log('capturas completas sin errores de consola ni de API');