export const CATEGORIAS_LUGAR = [
  { valor: 'COMER', etiqueta: 'Qué comer' },
  { valor: 'VISITAR', etiqueta: 'Qué visitar' },
  { valor: 'ALOJARSE', etiqueta: 'Dónde alojarse' },
] as const;

export type CategoriaLugar = (typeof CATEGORIAS_LUGAR)[number]['valor'];

export function etiquetaCategoriaLugar(categoria: CategoriaLugar | undefined): string {
  return CATEGORIAS_LUGAR.find((opcion) => opcion.valor === (categoria ?? 'VISITAR'))?.etiqueta
    ?? 'Qué visitar';
}
