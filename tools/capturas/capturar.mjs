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
// El 401 al preguntar por la sesión antes de entrar es el comportamiento correcto del panel, y
// el ruido de consola que deja ya se filtra por nombre en el listener de respuestas de abajo.
const IGNORAR = [/Failed to load resource.*401/];
const ignorables = (t) => IGNORAR.some((r) => r.test(t));

const problemas = [];

// Las únicas respuestas que NO son un fallo, y son las tres misma cosa: preguntar si hay sesión
// cuando no la hay. El 401 es la respuesta correcta, no un error del hotel. Con el 401 ya no se
// filtra "todo 401" (tapaba también los de las rutas de verdad), sino estos y solo mientras no se
// ha entrado: si la sesión se cae después, ese mismo 401 sí hay que verlo.
// El contador de mensajes responde igual a un admin, que no es huésped y por eso no tiene hilo:
// se espera siempre un 401 ahí.
const PROBES_SESION = ['/api/yo', '/api/admin/sesion'];
const SOLO_HUESPE = '/api/mis-reservas/mensajes/nuevos';
let sesionAdmin = false;
const esperada = (ruta) =>
  ruta.endsWith(SOLO_HUESPE) || (!sesionAdmin && PROBES_SESION.some((p) => ruta.endsWith(p)));

async function nuevaPagina(browser, opciones) {
  const contexto = await browser.newContext(opciones);
  const pagina = await contexto.newPage();
  // Cada contexto nace sin cookies: la siguiente vuelta (móvil) vuelve a estar sin sesión, así
  // que el 401 de la pregunta inicial vuelve a ser lo esperado.
  sesionAdmin = false;
  pagina.on('console', (m) => {
    if (m.type() === 'error' && !ignorables(m.text())) problemas.push(`consola: ${m.text()}`);
  });
  pagina.on('response', async (r) => {
    const url = r.url();
    if (!url.includes('/api/')) return;
    const ruta = url.split('?')[0];
    // Antes solo miraba las escrituras: un GET caido (datos que no llegan, tabla vacía) pasaba
    // desapercibido y la captura documentaba una pantalla rota como si fuera correcta.
    if (r.status() < 400) {
      if (r.request().method() === 'GET') return;
      const cuerpo = await r.text().catch(() => '');
      console.log(`api ok ${r.status()} ${url} -> ${cuerpo.slice(0, 120)}`);
      return;
    }
    if (r.status() === 401 && r.request().method() === 'GET' && esperada(ruta)) {
      console.log(`api esperado ${r.status()} GET ${ruta} (pregunta por la sesión)`);
      return;
    }
    const cuerpo = await r.text().catch(() => '');
    problemas.push(`api ${r.status()} ${r.request().method()} ${url}: ${cuerpo.slice(0, 200)}`);
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

async function guardarRegion(pagina, nombre, selector) {
  await pagina.waitForLoadState('load');
  await pagina.waitForTimeout(400);
  await pagina.locator(selector).screenshot({ path: `${SALIDA}/${nombre}.png` });
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
  // A partir de aquí ya hay sesión: un 401 en /api/admin/sesion sería una sesión caída, no la
  // pregunta inicial, y el guion tiene que enterarse.
  sesionAdmin = true;
}

// La CSP solo vale si está presente Y muerde. Comprobar el encabezado es fácil y no demuestra
// nada; lo que importa es que el navegador la haga cumplir.
async function comprobarCsp(navegador) {
  // Página aparte a propósito: el script inyectado genera a propósito un error de consola que el
  // resto del guion considera un fallo. Aquí se espera, y solo se mira si se ejecutó o no.
  const contexto = await navegador.newContext();
  const pagina = await contexto.newPage();

  const respuesta = await contexto.request.get(BASE);
  const cabecera = respuesta.headers()['content-security-policy'];
  if (!cabecera) {
    problemas.push('no hay cabecera Content-Security-Policy en la respuesta');
    await contexto.close();
    return;
  }
  if (cabecera.includes('unsafe-inline')) problemas.push('la CSP usa unsafe-inline');
  if (cabecera.includes('unsafe-eval')) problemas.push('la CSP usa unsafe-eval');

  await pagina.goto(BASE);
  const trasInyectar = await pagina.evaluate(() => {
    window.__csp = 'original';
    const s = document.createElement('script');
    s.textContent = 'window.__csp = "ejecutado";';
    document.head.appendChild(s);
    return window.__csp;
  });
  await contexto.close();
  if (trasInyectar !== 'original') {
    problemas.push(`la CSP dejó ejecutar un script en línea (quedó: ${trasInyectar})`);
  }
  console.log(`csp activa y efectiva: ${cabecera.slice(0, 58)}...`);
}

const navegador = await chromium.launch();
await comprobarCsp(navegador);

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

  // 2b. Comprobante: se consulta con el código recién creado para verificar el total acordado.
  const codigo = (await pagina.locator('.codigo-reserva').first().innerText()).trim();
  await pagina.goto(`${BASE}/consulta`);
  await pagina.fill('#codigo', codigo);
  await pagina.fill('#email-consulta', 'huesped.demo@example.com');
  await pagina.click('button[type=submit]');
  await esperar(pagina, '.comprobante', `${nombre}-04b-comprobante`);
  await guardarRegion(pagina, `${nombre}-04b-comprobante`, '.comprobante');

  // 3. Panel: login y reservas
  await pagina.goto(`${BASE}/admin/entrar`);
  await guardar(pagina, `${nombre}-05-login`);

  await iniciarSesion(pagina);
  await pagina.goto(`${BASE}/admin/reservas`);
  await esperar(pagina, '.tabla, .vacio', `${nombre}-06-panel`);
  await guardar(pagina, `${nombre}-06-panel-reservas`);

  // Una confirmación real: deja historial con el usuario de la sesión y una fila en el rastro de
  // acciones, que es lo que hacen las secciones siguientes. Sin esto la evidencia sale vacía.
  const confirmar = pagina.locator('button[aria-label^="Confirmar la reserva"]');
  if ((await confirmar.count()) > 0) {
    await confirmar.first().click();
    await pagina.waitForResponse((r) => r.url().includes('/api/admin/reservas/') && r.url().endsWith('/estado'));
    await esperar(pagina, 'table', `${nombre}-06c-confirmada`);
    await guardar(pagina, `${nombre}-06c-confirmada`);
  }

  // El detalle del panel usa el comprobante: habitación y total acordado visibles.
  const detalles = pagina.locator('button:text-is("Ver detalle")');
  if ((await detalles.count()) > 0) {
    await detalles.first().click();
    await esperar(pagina, '#titulo-detalle', `${nombre}-06b-detalle`);
    await guardarRegion(pagina, `${nombre}-06b-detalle`, 'aside');
  }

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

  // El calendario se verifica con las reservas de demostración del propio guion: siempre deja
  // dos habitaciones ocupadas del 10 al 12 de diciembre, así que diciembre es el mes estable.
  await pagina.fill('#mes-inv', '2026-12');
  await esperar(pagina, 'td[title*="reserva H-"]', `${nombre}-07c-calendario`);
  // La región puede empezar en el día 1 en pantallas estrechas: se centra en la primera ocupación
  // para que la evidencia muestre el estado ocupado y no solo una cuadrícula vacía.
  await pagina.locator('td[title*="reserva H-"]').first().scrollIntoViewIfNeeded();
  await guardarRegion(pagina, `${nombre}-07c-calendario`, '.ocupacion');

  // 5. Integraciones: estado real de los canales
  await pagina.goto(`${BASE}/admin/integraciones`);
  await esperar(pagina, 'text=Booking.com', `${nombre}-08-integraciones`);
  await guardar(pagina, `${nombre}-08-integraciones`);

  // El mapeo de demostración usa un identificador único y se elimina al final: la base de
  // desarrollo no acumula enlaces de prueba y el guion sigue siendo repetible.
  const externo = `BOOKING-E2E-${Date.now()}`;
  const formulario = pagina.locator('form[aria-label="Nuevo mapeo en Booking.com"]');
  await formulario.locator('select').nth(1).selectOption({ index: 1 });
  await formulario.getByLabel('Identificador externo').fill(externo);
  await formulario.getByRole('button', { name: 'Registrar mapeo' }).click();
  await esperar(pagina, `code:text-is("${externo}")`, `${nombre}-08b-mapeo`);
  const tarjeta = pagina.locator('article:has(h2:text-is("Booking.com"))');
  await tarjeta.locator(`code:text-is("${externo}")`).scrollIntoViewIfNeeded();
  await guardarRegion(pagina, `${nombre}-08b-mapeo`, 'article:has(h2:text-is("Booking.com"))');
  const elemento = pagina.locator(`li:has(code:text-is("${externo}"))`);
  await elemento.getByRole('button', { name: 'Eliminar' }).click();
  await elemento.getByRole('button', { name: 'Confirmar eliminación' }).click();
  await elemento.waitFor({ state: 'detached' });

  // 6. Indicadores
  await pagina.goto(`${BASE}/admin/indicadores`);
  await esperar(pagina, 'text=Operación estabilizada', `${nombre}-09-indicadores`);
  await guardar(pagina, `${nombre}-09-indicadores`);

  // 7. Datos del hotel
  await pagina.goto(`${BASE}/admin/hotel`);
  await esperar(pagina, '#hotel-nombre', `${nombre}-10-hotel`);
  await guardar(pagina, `${nombre}-10-hotel`);

  // 8. Actividad del panel. Antes del goto hay acciones reales de las secciones anteriores.
  await pagina.goto(`${BASE}/admin/auditoria`);
  await esperar(pagina, 'table', `${nombre}-11-auditoria`);
  await guardar(pagina, `${nombre}-11-auditoria`);

  await contexto.close();
}

await navegador.close();

if (problemas.length > 0) {
  console.error('PROBLEMAS DETECTADOS:');
  for (const p of [...new Set(problemas)]) console.error(` - ${p}`);
  process.exit(1);
}
console.log('capturas completas sin errores de consola ni de API');