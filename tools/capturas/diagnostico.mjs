// Diagnóstico de desbordamiento horizontal: mide el documento y señala los elementos
// que se salen del viewport. Un scroll horizontal en móvil casi siempre viene de un elemento
// con ancho fijo, y adivinarlo a ojo es peor que medirlo.
import { chromium, devices } from 'playwright';

const BASE = process.env.BASE_URL ?? 'http://web:80';
const navegador = await chromium.launch();
const contexto = await navegador.newContext(devices['iPhone 13']);
const pagina = await contexto.newPage();

await pagina.goto(`${BASE}/admin/entrar`);
await pagina.fill('#admin-email', 'admin');
await pagina.fill('#admin-clave', 'admin');
await pagina.click('button[type=submit]');
await pagina.waitForResponse((r) => r.url().includes('/api/admin/login'));

const rutas = ['/admin/reservas', '/admin/inventario', '/admin/integraciones', '/admin/indicadores', '/admin/auditoria', '/'];

for (const ruta of rutas) {
  await pagina.goto(BASE + ruta);
  await pagina.waitForLoadState('load');
  await pagina.waitForTimeout(600);
  const informe = await pagina.evaluate(() => {
    const limite = document.documentElement.clientWidth;
    const culpables = [];
    for (const el of document.querySelectorAll('*')) {
      const caja = el.getBoundingClientRect();
      if (caja.right <= limite + 1 || caja.width === 0) continue;

      // Un elemento dentro de un ancestro con scroll no ensancha el documento: se cuenta el
      // ancestro que realmente desborda, no el hijo que está desplazado.
      let ancestro = el.parentElement;
      let desbordante = null;
      while (ancestro && ancestro !== document.documentElement) {
        const estilo = getComputedStyle(ancestro);
        const cajaPadre = ancestro.getBoundingClientRect();
        if (cajaPadre.right > limite + 1 && estilo.overflowX === 'visible') {
          desbordante = ancestro;
          break;
        }
        ancestro = ancestro.parentElement;
      }
      if (desbordante && culpables.every((c) => c.etiqueta !== desbordante.tagName.toLowerCase()
        + '.' + String(desbordante.className).split(' ').join('.'))) {
        const cajaD = desbordante.getBoundingClientRect();
        culpables.push({
          etiqueta: desbordante.tagName.toLowerCase() + '.' + String(desbordante.className).split(' ').join('.'),
          ancho: Math.round(cajaD.width),
          derecha: Math.round(cajaD.right),
          padre: desbordante.parentElement
            ? desbordante.parentElement.tagName.toLowerCase() + '.' + String(desbordante.parentElement.className).split(' ').join('.')
            : '',
        });
      }
    }
    return {
      viewport: limite,
      scrollWidth: document.documentElement.scrollWidth,
      culpables: culpables.slice(0, 6),
    };
  });
  console.log(`\n${ruta}  viewport=${informe.viewport} scrollWidth=${informe.scrollWidth}`);

  if (ruta === '/admin/inventario') {
    // Ningún elemento visible sobresale, pero el documento sí. Se localiza por ocultación:
    // si al esconder un contenedor el scrollWidth baja a 390, ese contenedor es el culpable.
    const binaria = await pagina.evaluate(() => {
      const antes = document.documentElement.scrollWidth;
      const candidatos = ['.tabla-envoltura', '.rejilla', '.campo', '.tarjeta', 'section.seccion', 'main'];
      const resultados = [`scrollWidth inicial=${antes}`];
      for (const sel of candidatos) {
        const els = [...document.querySelectorAll(sel)];
        if (els.length === 0) continue;
        const previo = els.map((e) => e.style.display);
        els.forEach((e) => { e.style.display = 'none'; });
        resultados.push(`sin ${sel} -> ${document.documentElement.scrollWidth}`);
        els.forEach((e, i) => { e.style.display = previo[i]; });
      }
      return resultados;
    });
    console.log('\nBUSQUEDA POR OCULTACION:');
    for (const l of binaria) console.log('   ' + l);
  }
}

await navegador.close();