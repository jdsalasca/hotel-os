package co.hotel.util;

/** Celdas CSV. Un solo sitio para que todas las exportaciones neutralicen lo mismo. */
public final class Csv {

  private Csv() {}

  /**
   * Celda entre comillas con las comillas internas duplicadas. Y con una más: entrecomillar NO
   * impide que Excel ejecute una fórmula, así que si el texto empieza por = + - @ se le antepone
   * una comilla simple para que quede como texto.
   */
  public static String celda(String valor) {
    if (valor == null) return "";
    String seguro = valor.isEmpty() || "=+-@".indexOf(valor.charAt(0)) < 0 ? valor : "'" + valor;
    return "\"" + seguro.replace("\"", "\"\"") + "\"";
  }

  public static String celda(long valor) {
    return String.valueOf(valor);
  }
}
