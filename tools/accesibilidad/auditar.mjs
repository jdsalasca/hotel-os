// Auditoría de accesibilidad con axe-core: páginas públicas y panel del hotel.
//
// Uso: docker compose -f compose.yaml -f compose.accesibilidad.yaml up --build --abort-on-container-exit accesibilidad
// Escribe informe.json y una captura por página en $SALIDA. Sale 1 si hay
// violaciones serias o críticas: en verde no hay ninguna.
//
// El panel se audita entrando con la cuenta demo (ADMIN/CLAVE). Antes solo se miraban las
// páginas públicas: el hotel pasa el día en el panel y esa era justo la parte sin cubrir.
// Sin esas variables el panel se omite y el script lo dice (no finge cobertura).
//
// No borra datos. Crear el usuario demo ocurre al arrancar la API con HOTEL_DEMO_ADMIN,
// no aquí: este script no escribe nada.
import { writeFileSync, mkdirSync } from 'node:fs';
import { chromium } from 'playwright';
import AxeBuilder from '@axe-core/playwright';

const BASE = process.env.BASE_URL ?? 'http://web:80';
const SALIDA = process.env.SALIDA ?? './salida';
const ADMIN = process.env.ADMIN ?? '';
const CLAVE = process.env.CLAVE ?? '';
// WCAG 2.0/2.1 A y AA. Se deja fuera 'best-practice': reporta cosas de estilo que no
// son un incumplimiento y añadirían ruido que nadie iba a corregir.
const ETIQUETAS = { wcag2a: {}, wcag2aa: {}, wcag21a: {}, wcag21aa: {} };
mkdirSync(SALIDA, { recursive: true });

const RUTAS_PUBLICAS = ['/', '/consulta', '/reserva', '/mis-reservas', '/privacidad', '/terminos'];
// El panel se audita con sesión demo. Antes el script solo miraba las rutas públicas: el
// panel, donde el hotel pasa el día, quedaba sin medir y sin que nadie se enterara.
// Sin credenciales el panel se omite y se avisa: el informe no presume cobertura.
const RUTAS_PANEL = [
  '/admin/hoy',
  '/admin/reservas',
  '/admin/inventario',
  '/admin/indicadores',
  '/admin/actividad',
  '/admin/integraciones',
];

// @axe-core/playwright rechaza trabajar sobre una página suelta: exige un BrowserContext.
// Con browser.newPage() el script moría con "Please use browser.newContext()" en la
// primera página, y como nadie lo ejecutaba llevaba así desde que se escribió.
const navegador = await chromium.launch();
const contexto = await navegador.newContext();
const informe = { base: BASE, panelAuditado: false, paginas: [] };
let fallos = 0;

/** Violaciones que sí rompen el contrato: serias o criticas. */
function gravesDe(violations) {
  return violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
}

/**
 * Espera a que la página esté de verdad, no a que la red se calle. `networkidle` no se
 * cumple nunca aquí: la web mantiene conexiones abiertas, así que el auditor se quedaba
 * 30 s por página y moría en la primera. Se espera al cuerpo y se da un margen para que
 * React termine de pintar y lleguen los datos (importes, disponibilidad, sala).
 */
async function esperarContenido(pagina) {
  await pagina.waitForLoadState('domcontentloaded');
  await pagina.waitForTimeout(2500);
}

async function medir(pagina, ruta) {
  const { violations } = await new AxeBuilder({ page: pagina }).withTags(ETIQUETAS).analyze();
  const graves = gravesDe(violations);
  fallos += graves.length;
  const slug = ruta === '/' ? '-inicio' : ruta.replaceAll('/', '-');
  await pagina.screenshot({ path: `${SALIDA}/a11y${slug}.png` });
  informe.paginas.push({
    ruta,
    violaciones: violations.map((v) => ({
      id: v.id,
      impacto: v.impact,
      ayuda: v.help,
      nodos: v.nodes.map((n) => n.target.join(' ')),
    })),
    graves: graves.length,
  });
  console.log(`${ruta}: ${violations.length} violaciones (${graves.length} graves)`);
  // El detalle va a la salida y no solo al JSON: si alguien pega la consola en un issue,
  // tiene el nodo exacto sin tener que abrir informe.json y correlacionar.
  for (const v of graves) {
    console.log(`    [${v.impact}] ${v.id}: ${v.help}`);
    for (const n of v.nodes.slice(0, 4)) {
      console.log(`      ${n.target.join(' ')}`);
      const porQue = (n.failureSummary ?? '').split('\n').filter(Boolean)[1];
      if (porQue) console.log(`        ${porQue.trim()}`);
    }
    if (v.nodes.length > 4) console.log(`      ... y ${v.nodes.length - 4} nodos más`);
  }
}

for (const ruta of RUTAS_PUBLICAS) {
  const pagina = await contexto.newPage();
  const erroresConsola = [];
  pagina.on('pageerror', (e) => erroresConsola.push(String(e)));
  await pagina.goto(BASE + ruta, { waitUntil: 'domcontentloaded' });
  await esperarContenido(pagina);
  await medir(pagina, ruta);
  informe.paginas[informe.paginas.length - 1].erroresConsola = erroresConsola;
  await pagina.close();
}

// El panel necesita sesion: se entra una vez y se reutiliza la misma pestana.
if (ADMIN && CLAVE) {
  const pagina = await contexto.newPage();
  await pagina.goto(`${BASE}/admin/entrar`, { waitUntil: 'domcontentloaded' });
  await pagina.waitForSelector('#admin-email', { timeout: 20000 });
  await pagina.fill('#admin-email', ADMIN);
  await pagina.fill('#admin-clave', CLAVE);
  await Promise.all([
    pagina.waitForResponse((r) => r.url().includes('/api/admin/login')),
    pagina.click('button[type=submit]'),
  ]);
  for (const ruta of RUTAS_PANEL) {
    await pagina.goto(BASE + ruta, { waitUntil: 'domcontentloaded' });
    await esperarContenido(pagina);
    await medir(pagina, ruta);
  }
  await pagina.close();
  informe.panelAuditado = true;
} else {
  console.log('AVISO: sin ADMIN/CLAVE el panel no se audita (sin acceso)');
}

await navegador.close();
writeFileSync(`${SALIDA}/informe.json`, JSON.stringify(informe, null, 2));
console.log(fallos === 0 ? 'accesibilidad: sin violaciones graves' : `accesibilidad: ${fallos} graves`);
process.exit(fallos === 0 ? 0 : 1);