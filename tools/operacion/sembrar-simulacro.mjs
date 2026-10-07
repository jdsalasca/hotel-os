// Siembra mínima para el simulacro de respaldo: inventario, tarifa y una reserva.
// Uso (contra el compose del simulacro):
//   docker run --rm --network hotel-drill_default -v ./tools/operacion:/w -w /w \
//     node:22-alpine node sembrar-simulacro.mjs
// Solo habla con la API; no toca volúmenes ni archivos.

const base = 'http://api:8080';
let galletas = '';

const xsrfDe = () => (galletas.match(/XSRF-TOKEN=([^;]+)/) || [])[1] || '';

async function paso(metodo, ruta, cuerpo) {
  const r = await fetch(base + ruta, {
    method: metodo,
    headers: { 'Content-Type': 'application/json', Cookie: galletas, 'X-XSRF-TOKEN': xsrfDe() },
    body: cuerpo ? JSON.stringify(cuerpo) : undefined,
    redirect: 'manual',
  });
  for (const set of r.headers.getSetCookie()) {
    const par = set.split(';')[0];
    const nombre = par.split('=')[0];
    galletas = galletas.split('; ').filter((c) => !c.startsWith(nombre + '=')).concat(par).join('; ');
  }
  const texto = await r.text();
  return { estado: r.status, cuerpo: texto ? JSON.parse(texto) : null };
}

function exigir(pasoHecho, nombre) {
  if (pasoHecho.estado >= 300) {
    throw new Error(`${nombre}: HTTP ${pasoHecho.estado} ${JSON.stringify(pasoHecho.cuerpo)}`);
  }
  return pasoHecho.cuerpo;
}

const salud = await paso('GET', '/api/health');
exigir(salud, 'salud');
const acceso = await paso('POST', '/api/admin/login', { email: 'admin', password: 'admin' });
exigir(acceso, 'login');
const tipo = exigir(await paso('POST', '/api/admin/tipos',
  { codigo: 'DBL', nombre: 'Doble', capacidadMax: 2 }), 'tipo');
const hab = exigir(await paso('POST', '/api/admin/habitaciones',
  { codigo: '101', roomTypeId: tipo.id, nombre: 'Habitación 101' }), 'habitación');
const plan = exigir(await paso('POST', '/api/admin/planes',
  { codigo: 'FLEX', nombre: 'Flexible', moneda: 'EUR' }), 'plan');
for (const fecha of ['2027-06-01', '2027-06-02']) {
  exigir(await paso('POST', '/api/admin/tarifas',
    { ratePlanId: plan.id, roomTypeId: tipo.id, fecha, precioCents: 10000, moneda: 'EUR' }),
    'tarifa ' + fecha);
}
const reserva = exigir(await paso('POST', '/api/reservas',
  { email: 'drill@hotel.local', nombre: 'Simulacro', llegada: '2027-06-01', salida: '2027-06-03',
    huespedes: 2, roomId: hab.id, idempotencia: 'drill-1' }), 'reserva');
console.log(JSON.stringify({ salud: salud.estado, acceso: acceso.estado, codigo: reserva.codigo }));
