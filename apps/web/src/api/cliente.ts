/**
 * Cliente HTTP del frontend.
 *
 * Dos cosas que no son opcionales:
 * - Envía el token CSRF que el backend deja en la cookie XSRF-TOKEN. Sin él, el backend rechaza
 *   toda escritura con 403.
 * - Usa `credentials: same-origin` para enviar la sesión: sin cookies no hay panel.
 */

export class ErrorApi extends Error {
  constructor(
    readonly estado: number,
    mensaje: string,
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

  const respuesta = await fetch(ruta, {
    ...opciones,
    headers: cabeceras,
    credentials: 'same-origin',
  });

  const texto = await respuesta.text();
  const cuerpo = texto ? JSON.parse(texto) : null;

  if (!respuesta.ok) {
    throw new ErrorApi(respuesta.status, cuerpo?.error ?? cuerpo?.mensaje ?? 'Error inesperado');
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