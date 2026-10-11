/**
 * Titulo de la pestaña por ruta.
 *
 * `index.html` traia un titulo fijo, asi que la SPA tenia el mismo nombre en todas sus
 * paginas: quien abria «Mis reservas» en una pestana nueva, o la guardaba, se quedaba sin
 * saber donde estaba. El titulo se recalcula en cada ruta, incluido el 404.
 *
 * El nombre del hotel va delante porque es lo que identifica la pestana de un vistazo.
 */
const TITULOS: Record<string, string> = {
  '/': '',
  '/reserva': 'Reserva tus fechas',
  '/consulta': 'Consulta tu reserva',
  '/mis-reservas': 'Mis reservas',
  '/privacidad': 'Privacidad',
  '/terminos': 'Términos',
  '/admin': 'Panel',
  '/admin/entrar': 'Entrar al panel',
  '/admin/reservas': 'Reservas',
  '/admin/hoy': 'Hoy',
  '/admin/inventario': 'Inventario',
  '/admin/hotel': 'Hotel',
  '/admin/integraciones': 'Integraciones',
  '/admin/indicadores': 'Indicadores',
  '/admin/auditoria': 'Actividad',
  '/admin/lugares': 'Mapa',
};

const BASE = 'Hotel Eridu';
const SUFIJO = ' · Reserva directa';

/**
 * Ruta exacta o la mas larga que le sea prefijo. Una ruta nueva del panel que no este en
 * el mapa no se queda sin titulo: hereda el de su seccion en vez de quedarse en blanco.
 */
function tituloDe(ruta: string): string {
  const exacto = TITULOS[ruta];
  if (exacto !== undefined) return exacto;
  const prefijo = Object.keys(TITULOS)
    .filter((clave) => clave !== '/' && ruta.startsWith(clave))
    .sort((a, b) => b.length - a.length)[0];
  if (prefijo !== undefined) return TITULOS[prefijo] ?? '';
  return 'Página no encontrada';
}

/** El titulo completo de la pestana, tal como queda en el navegador. */
export function tituloDeRuta(ruta: string): string {
  const propio = tituloDe(ruta);
  return propio ? `${BASE} · ${propio}` : `${BASE}${SUFIJO}`;
}