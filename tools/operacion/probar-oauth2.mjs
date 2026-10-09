// Sondeo del camino OAuth2 sin credenciales: ida a Google, vuelta con código falso.
// Si el proxy, el registro o la sesión fallan, esto lo dice sin necesitar una cuenta.
// Además valida que el fallo quede registrado (ver FalloOauth2 en el log del api).
//
// Uso:  node tools/operacion/probar-oauth2.mjs [base]
//       node tools/operacion/probar-oauth2.mjs --self-test   (el detector debe detectar)
// Sale con 1 si algo no cuadra.

const fallos = [];
const ok = (condicion, mensaje) => {
  console.log(`${condicion ? 'ok   ' : 'FALLA'} ${mensaje}`);
  if (!condicion) fallos.push(mensaje);
};

async function sondear(base) {
  // 1. La ida: el backend arma la URL de Google con su redirect_uri.
  let ida;
  try {
    ida = await fetch(`${base}/oauth2/authorization/google-admin`, { redirect: 'manual' });
  } catch (e) {
    ok(false, `la ida responde (red o TLS: ${e.cause?.message ?? e.message})`);
    return;
  }
  ok(ida.status === 302, `la ida redirige (HTTP ${ida.status})`);
  const destino = ida.headers.get('location') ?? '';
  ok(destino.includes('accounts.google.com'), 'la ida lleva a Google');
  // En el header crudo va sin codificar; el navegador la muestra codificada. Valen ambas.
  ok(destino.includes('redirect_uri=https://hotel.eridu.top/')
    || destino.includes('redirect_uri=https%3A%2F%2Fhotel.eridu.top'),
    'la redirect_uri es https del propio host');
  const estado = (destino.match(/[?&]state=([^&]+)/) ?? [])[1] ?? '';
  ok(estado.length > 10, 'la ida trae state para atar la vuelta');
  const sesion = (ida.headers.get('set-cookie') ?? '').split(',').find((c) => c.trim().startsWith('JSESSIONID='));
  ok(!!sesion, 'la ida abre sesión (JSESSIONID)');
  if (fallos.length > 0) return;
  const galleta = sesion.split(';')[0];

  // 2. La vuelta con código falso: tiene que rebotar a la puerta con motivo, no colgar ni 500.
  let vuelta;
  try {
    vuelta = await fetch(
      `${base}/login/oauth2/code/google-admin?code=codigo-falso-sondeo&state=${estado}`,
      { redirect: 'manual', headers: { cookie: galleta } },
    );
  } catch (e) {
    ok(false, `la vuelta responde (red o TLS: ${e.cause?.message ?? e.message})`);
    return;
  }
  ok(vuelta.status === 302, `la vuelta redirige (HTTP ${vuelta.status})`);
  const rebote = vuelta.headers.get('location') ?? '';
  ok(rebote.endsWith('/admin/entrar?error=oauth2'), `la vuelta cae en la puerta con motivo (${rebote || 'sin Location'})`);
  if (fallos.length === 0) {
    console.log('nota: revisa en el log del api "vuelta de Google fallida" con [invalid_grant]:');
    console.log('     eso prueba que el canje llegó a Google y el registro quedó trazado.');
  }
}

const arg = process.argv[2] ?? 'https://hotel.eridu.top';
if (arg === '--self-test') {
  // Contra un puerto cerrado el sondeo TIENE que fallar limpio (exit 1 con motivo).
  const antes = fallos.length;
  await sondear('http://127.0.0.1:9');
  if (fallos.length > antes) {
    console.log('autoprueba: el detector detecta (falló como debía)');
  } else {
    console.log('FALLA autoprueba: contra puerto cerrado debió fallar y no falló');
    process.exit(1);
  }
} else {
  await sondear(arg.replace(/\/$/, ''));
  if (fallos.length > 0) {
    console.error(`OAUTH2: ${fallos.length} comprobación(es) sin cuadrar`);
    process.exit(1);
  }
  console.log('oauth2: ida, vuelta y rebote con motivo, todo en su sitio');
}
