package co.hotel.indicadores;

/**
 * Definición de un indicador. Sin línea base ni meta inventadas: ambas quedan en null hasta que
 * el hotel las fije con datos propios.
 */
public record DefinicionIndicador(String clave, int fase, String nombre, String definicion, String formula,
                                  String fuente, String unidad, String periodoPorDefecto,
                                  String lineaBase, String meta, String responsable) {}