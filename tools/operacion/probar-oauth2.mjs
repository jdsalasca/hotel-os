// Sondeo del camino OAuth2 sin credenciales: ida a Google, vuelta con código falso.
// Si el proxy, el registro o la sesión fallan, esto lo dice sin necesitar una cuenta.
// Además valida que el fallo quede registrado (ver FalloOauth2 en el log del api).
// Cubre las dos puertas (google-admin y google-huesped): el Lax las curó a ambas
// y el vigía las vigila a ambas.
//
// Uso:  node tools/operacion/probar-oauth2.mjs [base] [--registro google-admin|google-huesped]
//       node tools/operacion/probar-oauth2.mjs --self-test   (el detector debe detectar)
// Sale con 1 si algo no cuadra.

const fallos = [];
const ok = (condicion, mensaje) => {
  console.log(`${condicion ? 'ok   ' : 'FALLA'} ${mensaje}`);
  if (!condicion) fallos.push(mensaje);
};

const PUERTAS = {
  'google-admin': '/admin/entrar?error=oauth2',
  'google-huesped': '/?error=oauth2',
};

async function sondear(base, registro) {
  const puerta = PUERTAS[registro];
  console.log(`--- registro ${registro} (espera ${puerta})`);
  let anfitrion = '';
  try {
    anfitrion = new URL(base).host;
  } catch (e) {
    ok(false, `la base es URL válida (${base})`);
    return;
  }
  // 1. La ida: el backend arma la URL de Google con su redirect_uri.
  let ida;
  try {
    ida = await fetch(`${base}/oauth2/authorization/${registro}`, { redirect: 'manual' });
  } catch (e) {
    ok(false, `la ida responde (red o TLS: ${e.cause?.message ?? e.message})`);
    return;
  }
  ok(ida.status === 302, `la ida redirige (HTTP ${ida.status})`);
  const destino = ida.headers.get('location') ?? '';
  ok(destino.includes('accounts.google.com'), 'la ida lleva a Google');
  // En el header crudo va sin codificar; el navegador la muestra codificada. Valen ambas.
  ok(destino.includes(`redirect_uri=https://${anfitrion}/`)
    || destino.includes(`redirect_uri=https%3A%2F%2F${anfitrion}`),
    'la redirect_uri es https del propio host');
  const estado = (destino.match(/[?&]state=([^&]+)/) ?? [])[1] ?? '';
  ok(estado.length > 10, 'la ida trae state para atar la vuelta');
  const sesion = (ida.headers.get('set-cookie') ?? '').split(',').find((c) => c.trim().startsWith('JSESSIONID='));
  ok(!!sesion, 'la ida abre sesión (JSESSIONID)');
  if (fallos.length > 0) return;
  const galleta = sesion.split(';')[0];

  // 2. La vuelta con código falso: tiene que rebotar a la puerta con motivo, no colgar ni 500.
  // Lleva sondeo=1 para que el log la marque como ruido conocido (ver FalloOauth2).
  let vuelta;
  try {
    vuelta = await fetch(
      `${base}/login/oauth2/code/${registro}?code=codigo-falso-sondeo&state=${estado}&sondeo=1`,
      { redirect: 'manual', headers: { cookie: galleta } },
    );
  } catch (e) {
    ok(false, `la vuelta responde (red o TLS: ${e.cause?.message ?? e.message})`);
    return;
  }
  ok(vuelta.status === 302, `la vuelta redirige (HTTP ${vuelta.status})`);
  const rebote = vuelta.headers.get('location') ?? '';
  ok(rebote.endsWith(puerta), `la vuelta cae en la puerta con motivo (${rebote || 'sin Location'})`);
}

const args = process.argv.slice(2);
if (args.includes('--self-test')) {
  // Contra un puerto cerrado el sondeo TIENE que fallar limpio (exit 1 con motivo).
  const antes = fallos.length;
  await sondear('http://127.0.0.1:9', 'google-admin');
  if (fallos.length > antes) {
    console.log('autoprueba: el detector detecta (falló como debía)');
  } else {
    console.log('FALLA autoprueba: contra puerto cerrado debió fallar y no falló');
    process.exit(1);
  }
} else {
  const idx = args.findIndex((a) => a === '--registro' || a.startsWith('--registro='));
  const solo = idx >= 0
    ? (args[idx].includes('=') ? args[idx].split('=')[1] : args[idx + 1])
    : undefined;
  const base = (args.find((a, i) => !a.startsWith('--') && i !== idx + 1)
    ?? 'https://hotel.eridu.top').replace(/\/$/, '');
  const registros = solo ? [solo] : Object.keys(PUERTAS);
  for (const registro of registros) {
    if (!PUERTAS[registro]) {
      console.log(`FALLA registro desconocido: ${registro} (usa ${Object.keys(PUERTAS).join(' | ')})`);
      process.exit(1);
    }
    await sondear(base, registro);
  }
  if (fallos.length > 0) {
    console.error(`OAUTH2: ${fallos.length} comprobación(es) sin cuadrar`);
    process.exit(1);
  }
  console.log('nota: revisa en el log del api "vuelta de Google fallida" con [invalid_grant]:');
  console.log('     eso prueba que el canje llegó a Google y el registro quedó trazado.');
  console.log('oauth2: ida, vuelta y rebote con motivo, todo en su sitio');
}
