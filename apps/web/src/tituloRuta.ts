/**
 * Titulo de la pestaña por ruta.
 *
 * `index.html` traia un titulo fijo, asi que la SPA tenia el mismo nombre en todas sus
 * paginas: quien abria «Mis reservas» en una pestana nueva, o la guardaba, se quedaba sin
 * saber donde estaba. El titulo se recalcula en cada ruta, incluido el 404.
 *
 * El nombre del hotel va delante porque es lo que identifica la pestana de un vistazo.
 *
 * El mapa es exhaustivo a proposito: las rutas son exactas y el wildcard atiende el resto,
 * asi que lo que no este aqui es una pagina que no existe y se anuncia como tal.
 */
const TITULOS: Record<string, string> = {
  '/': 'Inicio',
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

/**
 * Solo coincidencia exacta. Las rutas de esta SPA son exactas y el wildcard atiende el
 * resto, así que cualquier ruta que no esté en el mapa es una página que no existe.
 *
 * La herencia por prefijo mentía: `/admin/hotelaria` se hacía pasar por Hotel y
 * `/admin/promociones` por Panel, aunque ninguna de las dos existe. Aquí se dice la verdad:
 * o hay ruta, o es la de "no encontrada".
 */
function tituloDe(ruta: string): string {
  return TITULOS[ruta] ?? 'Página no encontrada';
}

/** El titulo completo de la pestana, tal como queda en el navegador. */
export function tituloDeRuta(ruta: string): string {
  const propio = tituloDe(ruta);
  return `${BASE} · ${propio}`;
}
