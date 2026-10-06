/** Formato de fechas y dinero en español. La moneda la manda el hotel: no hay una por defecto. */

export function fechaCorta(iso: string): string {
  return new Date(iso + 'T12:00:00').toLocaleDateString('es-CO', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  });
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