/**
 * Cliente HTTP del frontend.
 *
 * Dos cosas que no son opcionales:
 * - Envía el token CSRF que el backend deja en la cookie XSRF-TOKEN. Sin él, el backend rechaza
 *   toda escritura con 403.
 * - Usa `credentials: same-origin` para enviar la sesión: sin cookies no hay panel.
 */

/**
 * Base de la API. Vacía = mismo origen, que es lo correcto en todos los despliegues: docker
 * compose y Vercel con la reescritura de `/api` (ver `vercel.json`).
 *
 * Si se rellena con OTRO origen, el panel deja de funcionar, y no por una razón sino por cuatro a
 * la vez: el navegador no manda la cookie de sesión entre sitios distintos con
 * `credentials: same-origin`, la CSP `connect-src 'self'` bloquea la llamada, la cookie
 * SameSite=Strict tampoco viaja, y la API no permite ese origen con CORS. Por eso aquí se avisa en
 * voz alta en vez de dejar un panel que entra pero se cae al pedir datos.
 */
const BASE_API = (import.meta.env.VITE_API_BASE ?? '').replace(/\/$/, '');

const CRUZANDO_ORIGEN =
  BASE_API !== '' &&
  typeof window !== 'undefined' &&
  !BASE_API.startsWith(window.location.origin) &&
  !BASE_API.startsWith('/');

if (CRUZANDO_ORIGEN && typeof window !== 'undefined') {
  console.error(
    `[cliente] VITE_API_BASE=${BASE_API} apunta a otro origen y el panel no va a funcionar: ` +
      'la cookie de sesión no se envía, la CSP bloquea la llamada y la API no permite el origen. ' +
      'Déjala vacía y deja que la reescritura de /api de vercel.json haga su trabajo.',
  );
}

function urlApi(ruta: string): string {
  return `${BASE_API}${ruta}`;
}

/** Se exporta porque la comprobación de sesión vive en su propio módulo. */
export { urlApi };

export class ErrorApi extends Error {
  constructor(
    readonly estado: number,
    mensaje: string,
    /** Cuerpo del error tal cual lo mandó la API (p. ej. el nuevo importe en un 409). */
    readonly datos: unknown = null,
  ) {
    super(mensaje);
    this.name = 'ErrorApi';
  }
}

function leerTokenCsrf(): string {
  const deCookie = document.cookie
    .split('; ')
    .find((f) => f.startsWith('XSRF-TOKEN='));
  return deCookie ? decodeURIComponent(deCookie.split('=').slice(1).join('=')) : '';
}

async function peticion<T>(ruta: string, opciones: RequestInit = {}): Promise<T> {
  const esEscritura = (opciones.method ?? 'GET') !== 'GET';
  const cabeceras = new Headers(opciones.headers);
  if (opciones.body) cabeceras.set('Content-Type', 'application/json');
  if (esEscritura) cabeceras.set('X-XSRF-TOKEN', leerTokenCsrf());

  let respuesta: Response;
  try {
    respuesta = await fetch(urlApi(ruta), {
      ...opciones,
      headers: cabeceras,
      credentials: 'same-origin',
    });
  } catch {
    // Sin red no hay estado ni cuerpo: el TypeError de fetch no le dice nada a nadie.
    throw new ErrorApi(0, 'No hay conexión con el hotel. Revisa tu internet e inténtalo de nuevo.', null);
  }

  const texto = await respuesta.text();
  let cuerpo: unknown = null;
  if (texto) {
    try {
      cuerpo = JSON.parse(texto);
    } catch {
      // El proxy y el contenedor responden HTML en sus errores (502, 401 del entry point):
      // antes reventaba con un SyntaxError ilegible en cada pantalla que lo mostrara.
      cuerpo = null;
    }
  }

  if (!respuesta.ok) {
    const error = (cuerpo as { error?: unknown; mensaje?: unknown } | null)?.error
      ?? (cuerpo as { mensaje?: unknown } | null)?.mensaje;
    const mensaje = typeof error === 'string' && error.length > 0
      ? error
      : respuesta.status === 401
        ? 'La sesión venció. Entra de nuevo.'
        : 'Error inesperado del hotel. Inténtalo de nuevo.';
    throw new ErrorApi(respuesta.status, mensaje, cuerpo);
  }
  return cuerpo as T;
}

export const api = {
  get: <T>(ruta: string) => peticion<T>(ruta),
  post: <T>(ruta: string, cuerpo?: unknown) =>
    peticion<T>(ruta, { method: 'POST', body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo) }),
};

/**
 * Clave de idempotencia estable por intento: repetir el envío no duplica la reserva.
 *
 * crypto.randomUUID() solo existe en contextos seguros (HTTPS o localhost). En una red local por
 * HTTP no está, y llamarlo sin más rompe el flujo de reserva entero. Por eso hay respaldo.
 */
export function nuevaClaveIdempotencia(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return 'id-' + Math.random().toString(36).slice(2) + '-' + Date.now().toString(36);
}