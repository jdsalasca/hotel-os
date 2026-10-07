package co.hotel.util;

/** Celdas CSV. Un solo sitio para que todas las exportaciones neutralicen lo mismo. */
public final class Csv {

  private Csv() {}

  /**
   * Celda entre comillas con las comillas internas duplicadas. Y con una más: entrecomillar NO
   * impide que Excel ejecute una fórmula, así que si el primer carácter no-blanco es = + - @ se
   * le antepone una comilla simple para que quede como texto. Mirar el primer carácter no
   * basta: un espacio o tabulador antes del `=` la escondería igual.
   */
  public static String celda(String valor) {
    if (valor == null) return "";
    int i = 0;
    while (i < valor.length() && Character.isWhitespace(valor.charAt(i))) i++;
    String seguro = (i < valor.length() && "=+-@".indexOf(valor.charAt(i)) >= 0) ? "'" + valor : valor;
    return "\"" + seguro.replace("\"", "\"\"") + "\"";
  }

  public static String celda(long valor) {
    return String.valueOf(valor);
  }
}
