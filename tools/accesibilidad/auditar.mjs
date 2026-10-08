// Auditoría de accesibilidad de las páginas públicas con axe-core.
//
// Uso: docker compose -f compose.yaml -f compose.accesibilidad.yaml up --build --abort-on-container-exit accesibilidad
// Escribe informe.json y una captura por página en $SALIDA. Sale 1 si hay
// violaciones serias o críticas: en verde no hay ninguna.
//
// Solo lee páginas que no piden sesión; no crea ni borra datos.
import { writeFileSync, mkdirSync } from 'node:fs';
import { chromium } from 'playwright';
import AxeBuilder from '@axe-core/playwright';

const BASE = process.env.BASE_URL ?? 'http://web:80';
const SALIDA = process.env.SALIDA ?? './salida';
mkdirSync(SALIDA, { recursive: true });

const RUTAS = ['/', '/consulta', '/reserva', '/mis-reservas', '/privacidad', '/terminos'];

const navegador = await chromium.launch();
const informe = { base: BASE, paginas: [] };
let fallos = 0;

for (const ruta of RUTAS) {
  const pagina = await navegador.newPage();
  const erroresConsola = [];
  pagina.on('pageerror', (e) => erroresConsola.push(String(e)));
  await pagina.goto(BASE + ruta, { waitUntil: 'networkidle' });
  await pagina.screenshot({ path: `${SALIDA}/a11y${ruta === '/' ? '-inicio' : ruta.replaceAll('/', '-')}.png` });
  const { violations } = await new AxeBuilder({ page: pagina }).analyze();
  const graves = violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
  fallos += graves.length;
  informe.paginas.push({
    ruta,
    violaciones: violations.map((v) => ({
      id: v.id,
      impacto: v.impact,
      ayuda: v.help,
      nodos: v.nodes.map((n) => n.target.join(' ')),
    })),
    erroresConsola,
  });
  console.log(`${ruta}: ${violations.length} violaciones (${graves.length} graves)`);
  await pagina.close();
}

await navegador.close();
writeFileSync(`${SALIDA}/informe.json`, JSON.stringify(informe, null, 2));
console.log(fallos === 0 ? 'accesibilidad: sin violaciones graves' : `accesibilidad: ${fallos} graves`);
process.exit(fallos === 0 ? 0 : 1);
