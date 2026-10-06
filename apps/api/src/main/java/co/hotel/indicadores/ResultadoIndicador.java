package co.hotel.indicadores;

/**
 * Resultado de un indicador en un periodo.
 *
 * La distinción entre "vale cero" y "no hay dato" es el punto de todo el módulo:
 * - {@code valor == 0} con {@code tieneResultado()} = un cero real, medido.
 * - {@code valor == null} = no hay datos; {@code motivoFaltante} explica cuál falta.
 */
public record ResultadoIndicador(DefinicionIndicador definicion, String periodo, Double valor,
                                 Long numerador, Long denominador, String motivoFaltante, String nota) {

  public boolean tieneResultado() { return valor != null; }

  public static ResultadoIndicador medido(DefinicionIndicador d, String periodo, double valor,
                                          Long numerador, Long denominador, String nota) {
    return new ResultadoIndicador(d, periodo, valor, numerador, denominador, null, nota);
  }

  /** Sin datos: nunca 0, siempre la razón. */
  public static ResultadoIndicador faltante(DefinicionIndicador d, String periodo, String motivo) {
    return new ResultadoIndicador(d, periodo, null, null, null, motivo, null);
  }
}