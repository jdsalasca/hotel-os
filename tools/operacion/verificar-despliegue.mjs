// Comprobación de la configuración de despliegue. Corre sin desplegar nada y sin Docker.
//
// Existe porque el flujo de Google ya se rompió dos veces por lo mismo: se añadió una ruta al
// backend (/oauth2/... y /login/oauth2/...) y el proxy seguía reescribiendo solo /api/*. El botón
// daba 200 con el index.html del SPA y nadie se enteró hasta que se probó a mano. Ni las pruebas
// del backend ni el guion de capturas lo detectan, porque el backend respondía bien: lo que
// faltaba era el camino.
//
// La configuración de nginx se lee del Dockerfile, que es donde vive (va incrustada con un
// heredoc), así que esto no necesita construir la imagen.
//
// Uso:  node tools/operacion/verificar-despliegue.mjs
// Sale con 1 si algo no cuadra.

import { readFileSync } from 'node:fs';

const fallos = [];
const comprobar = (ok, mensaje) => {
  console.log(`${ok ? 'ok   ' : 'FALLA'} ${mensaje}`);
  if (!ok) fallos.push(mensaje);
};

/** Lo que nginx tiene que reenviar al backend. Añadir una ruta aquí laObliga a proxearla. */
const RUTAS = ['/api/', '/oauth2/', '/login/oauth2/'];
const CABECERAS = ['Content-Security-Policy', 'X-Content-Type-Options', 'X-Frame-Options',
  'Referrer-Policy', 'Permissions-Policy'];

// 1. La configuración de nginx, tal cual está en el Dockerfile.
let dockerfile = '';
try {
  dockerfile = readFileSync('apps/web/Dockerfile', 'utf8');
} catch (e) {
  comprobar(false, 'se encuentra apps/web/Dockerfile');
}

if (dockerfile) {
  // Sin comentarios: el Dockerfile explica en uno por qué NO se usa unsafe-inline, y buscar la
  // palabra en todo el texto daría un fallo falso.
  const config = dockerfile
    .split('\n')
    .filter((linea) => !linea.trimStart().startsWith('#'))
    .join('\n');

  for (const ruta of RUTAS) {
    comprobar(config.includes(`location ${ruta}`),
      `el proxy reenvía ${ruta} al backend (si no, el SPA contesta con su index.html)`);
  }
  for (const cabecera of CABECERAS) {
    comprobar(config.includes(cabecera), `nginx envía ${cabecera}`);
  }
  comprobar(!config.includes('unsafe-inline') && !config.includes('unsafe-eval'),
    'la CSP no relaja la protección con unsafe-inline ni unsafe-eval');
  comprobar(config.includes("'self'"), "la CSP deja pasar el propio origen (connect-src 'self')");
}

// 2. Vercel: sin los reescrituras, el frontend desplegado ahí no habla con el backend.
let vercel = {};
try {
  vercel = JSON.parse(readFileSync('vercel.json', 'utf8'));
  comprobar(true, 'vercel.json es JSON válido');
} catch (e) {
  comprobar(false, 'vercel.json es JSON válido');
}

if (vercel.rewrites) {
  const destinos = vercel.rewrites.map((r) => r.destination);
  for (const ruta of RUTAS) {
    const prefijo = ruta === '/api/' ? '/api/:path*' : `${ruta.replace(/\/$/, '')}/:path*`;
    comprobar(destinos.some((d) => d.includes(ruta)), `vercel.json reescribe ${prefijo}`);
  }
  comprobar(destinos.some((d) => d.includes('/index.html')),
    'vercel.json tiene el fallback a index.html para las rutas de React Router');
}

if (vercel.headers) {
  const texto = JSON.stringify(vercel.headers);
  comprobar(texto.includes('Content-Security-Policy'), 'vercel.json envía la CSP');
  comprobar(!texto.includes('unsafe-inline') && !texto.includes('unsafe-eval'),
    'la CSP de Vercel tampoco relaja la protección');
} else {
  comprobar(false, 'vercel.json declara cabeceras');
}

// 3. El mismo origen, siempre: una base cruzada rompe el panel (ver round-29).
const base = (process.env.VITE_API_BASE ?? '').trim();
comprobar(base === '', 'VITE_API_BASE vacía: el panel depende de la reescritura, no de otro origen');

// 4. Respaldos en todos los composes que sirven: el modo túnel los perdió una vez (ver
// round-78) porque nada los exigía. El drill y el de capturas no sirven tráfico y no llevan.
for (const compose of ['compose.yaml', 'compose.production.yaml', 'compose.tunnel.yaml']) {
  let texto = '';
  try {
    texto = readFileSync(compose, 'utf8');
  } catch (e) {
    comprobar(false, `${compose} se puede leer`);
    continue;
  }
  const servicio = new RegExp(`^  respaldo:`, 'm').test(texto);
  comprobar(servicio, `${compose} define el servicio respaldo`);
  comprobar(texto.includes('hotel-data:/data') && texto.includes('hotel-backups:/backups'),
    `${compose} monta datos y destino del respaldo`);
  // 4b. La sesión NO puede ser SameSite=Strict en prod: la vuelta de Google es una
  // navegación iniciada en otro sitio y el navegador retiene la cookie estricta, con lo
  // que el callback llega sin sesión (authorization_request_not_found, ver ronda 151).
  // Lax deja pasar ese GET de vuelta y el CSRF lo sigue cubriendo el token XSRF.
  if (compose !== 'compose.yaml') {
    comprobar(!texto.includes('SAME_SITE: "strict"'),
      `${compose} sin SameSite=Strict (rompe el login con Google)`);
  }
}

// 4c. Vigía OAuth en el túnel (prod): un sondeo diario del camino ida-vuelta para que la
// próxima rotura (como el SameSite de la ronda 151) quede registrada sin esperar al dueño.
// Solo el túnel vigila prod; el resto de composes no sirven tráfico público.
let tunel = '';
try {
  tunel = readFileSync('compose.tunnel.yaml', 'utf8');
} catch (e) {
  comprobar(false, 'compose.tunnel.yaml se puede leer');
}
if (tunel) {
  comprobar(new RegExp(`^  vigia:`, 'm').test(tunel),
    'compose.tunnel.yaml define el servicio vigia (sondeo OAuth diario)');
  // Simulacro semanal: la integridad solo se sabe pasándole integrity_check de verdad;
  // si nadie lo corre en prod, la corrupción se descubre el día del desastre.
  comprobar(new RegExp(`^  simulacro:`, 'm').test(tunel),
    'compose.tunnel.yaml define el servicio simulacro (drill semanal de restauración)');
}

// 5. Base SEO/compartir: sin robots.txt el SPA contesta su index.html a los
// rastreadores, y sin OG el enlace compartido sale pelado (ver round-144).
let robots = '';
try {
  robots = readFileSync('apps/web/public/robots.txt', 'utf8');
} catch (e) {
  comprobar(false, 'existe apps/web/public/robots.txt');
}
if (robots) {
  comprobar(robots.includes('Disallow: /admin/'), 'robots.txt cierra el panel a rastreadores');
  comprobar(robots.includes('Sitemap:'), 'robots.txt anuncia el sitemap');
}
let mapa = '';
try {
  mapa = readFileSync('apps/web/public/sitemap.xml', 'utf8');
} catch (e) {
  comprobar(false, 'existe apps/web/public/sitemap.xml');
}
if (mapa) {
  for (const ruta of ['/', '/consulta', '/mis-reservas', '/privacidad', '/terminos']) {
    comprobar(mapa.includes(`<loc>https://hotel.eridu.top${ruta}</loc>`),
      `el sitemap lista ${ruta}`);
  }
}
let raiz = '';
try {
  raiz = readFileSync('apps/web/index.html', 'utf8');
} catch (e) {
  comprobar(false, 'se encuentra apps/web/index.html');
}
if (raiz) {
  for (const etiqueta of ['og:title', 'og:description', 'og:type', 'og:image']) {
    comprobar(raiz.includes(etiqueta), `index.html trae ${etiqueta} para compartir`);
  }
}

console.log('');
if (fallos.length > 0) {
  console.error(`DESPLIEGUE: ${fallos.length} comprobación(es) sin cuadrar`);
  process.exit(1);
}
console.log('despliegue: rutas de proxy, cabeceras y CSP coherentes entre nginx y Vercel');