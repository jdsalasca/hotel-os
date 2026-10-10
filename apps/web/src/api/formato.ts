/** Formato de fechas y dinero en español. La moneda la manda el hotel: no hay una por defecto. */

export function fechaCorta(iso: string): string {
  const fecha = new Date(iso + 'T12:00:00');
  const opciones = {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  } as const;
  if (Number.isNaN(fecha.getTime())) return fecha.toLocaleDateString('es-CO', opciones);

  const partes = new Intl.DateTimeFormat('es-CO', opciones).formatToParts(fecha);
  const valor = (tipo: 'day' | 'month' | 'year') => partes.find((parte) => parte.type === tipo)?.value ?? '';
  return `${valor('day')} ${valor('month')} ${valor('year')}`;
}

export function noches(llegada: string, salida: string): number {
  const d = (Date.parse(salida) - Date.parse(llegada)) / 86_400_000;
  return Number.isFinite(d) && d > 0 ? d : 0;
}

/**
 * Centavos a moneda legible. La moneda la fija el hotel; aquí no hay ninguna por defecto.
 * Con `currencyDisplay: 'code'` el importe no se confunde: "$ 4.500" no dice de qué moneda.
 */
export function monto(centavos: number, moneda: string): string {
  return new Intl.NumberFormat('es-CO', {
    style: 'currency',
    currency: moneda,
    currencyDisplay: 'code',
    minimumFractionDigits: 0,
    maximumFractionDigits: moneda === 'COP' ? 0 : 2,
  }).format(centavos / 100);
}

export function hoyIso(): string {
  return new Date().toISOString().slice(0, 10);
}

export function mananaIso(): string {
  return new Date(Date.now() + 86_400_000).toISOString().slice(0, 10);
}

/**
 * Enlace de WhatsApp para hablar con el hotel. Devuelve null sin teléfono usable: el botón
 * que lo usa no se muestra en vez de apuntar a ningún lado. Regla de país mínima y
 * documentada: el móvil colombiano de 10 dígitos lleva el 57 delante; si ya trae
 * indicativo (más de 10 dígitos) se respeta tal cual.
 */
export function enlaceWhatsApp(telefono: string, texto: string): string | null {
  const digitos = (telefono ?? '').replace(/\D/g, '');
  if (digitos.length < 7) return null;
  const numero = digitos.length === 10 ? `57${digitos}` : digitos;
  return `https://wa.me/${numero}?text=${encodeURIComponent(texto)}`;
}

/**
 * Quién saluda en la cabecera: el nombre si hay, si no lo de antes del @darle forma de
 * nombre, porque "savatar62@gmail.com" suelto en la cabecera se lee como un identificador
 * y no como un saludo. El correo completo queda en el `title` para no perderlo.
 */
export function nombreCorto(nombre: string | null | undefined, email: string): string {
  const limpio = (nombre ?? '').trim();
  if (limpio.length > 0) return limpio;
  const correo = (email ?? '').trim();
  const arroba = correo.indexOf('@');
  return comoNombre(arroba > 0 ? correo.slice(0, arroba) : correo);
}

/**
 * Convierte el usuario de un correo en algo que se pueda leer como nombre: separadores
 *--"puntos", guiones, signos más y guiones bajos-- pasan a espacio y cada palabra empieza
 * en mayúscula. Los números y letras sueltas se conservan: "savatar62" es mejor que
 * "Savatar". Si no queda nada legible, se devuelve el original.
 */
function comoNombre(parte: string): string {
  const palabras = parte
    .split(/[.\-_+]+/)
    .map((p) => p.trim())
    .filter((p) => p.length > 0)
    .map((p) => p.charAt(0).toUpperCase() + p.slice(1));
  if (palabras.length === 0) return parte;
  return palabras.join(' ');
}
