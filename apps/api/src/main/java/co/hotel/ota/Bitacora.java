package co.hotel.ota;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Redactor de la bitácora de sincronización. Regla única: lo que sirve para diagnosticar se conserva
 * (código HTTP, nombre del endpoint), lo que sirve para autenticarse o identificar a alguien no.
 */
public final class Bitacora {
  private static final int MAX = 500;

  /** Cada regla: grupo 1 = contexto que se conserva, resto = valor oculto.
   *  El orden importa: primero tokens estructurados, después clave=valor. Si "clave=valor" corre
   *  primero, se come el prefijo ("Bearer") y deja el token suelto. */
  private static final List<Pattern> REGLAS = List.of(
    // JWT suelto (tres segmentos base64url) — antes de cualquier regla por palabra clave.
    Pattern.compile("\\beyJ[A-Za-z0-9_-]{6,}\\.[A-Za-z0-9_-]{6,}\\.[A-Za-z0-9_-]{6,}"),
    // sk_live_, pk_test_, ghp_, ghp_..., glpat-, xoxb-...
    Pattern.compile("\\b(?:sk|pk|rk|ghp|glpat|xoxb|xoxp)[-_][A-Za-z0-9_-]{6,}"),
    // Bearer <token> — conserva la palabra, oculta el token.
    Pattern.compile("(?i)(\\bbearer\\s+)[A-Za-z0-9._~+/=-]{8,}"),
    // clave=valor / clave: valor — conserva "clave=" para diagnosticar.
    Pattern.compile("(?i)(\\b[a-z_]*(?:secret|api[_-]?key|apikey|token|password|passwd|credential|authorization)[a-z_]*\\s*[=:]\\s*)\\S+"),
    // correos
    Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"),
    // teléfonos largos
    Pattern.compile("(?<!\\d)\\+?\\d{8,15}(?!\\d)"));

  private Bitacora() {}

  public static String sanear(String texto) {
    if (texto == null) return null;
    String limpio = texto;
    for (Pattern regla : REGLAS) limpio = aplicar(regla, limpio);
    return limpio.length() > MAX ? limpio.substring(0, MAX) + "…" : limpio;
  }

  private static String aplicar(Pattern regla, String texto) {
    Matcher m = regla.matcher(texto);
    StringBuilder salida = new StringBuilder();
    while (m.find()) {
      String sustituto = "[REDACTADO]";
      StringBuilder conContexto = new StringBuilder();
      for (int g = 1; g <= m.groupCount(); g++) {
        String grupo = m.group(g);
        if (grupo == null || grupo.equals("[REDACTADO]")) continue;
        if (conContexto.length() > 0) conContexto.append(' ');
        conContexto.append(grupo);
      }
      if (conContexto.length() > 0) sustituto = conContexto + " " + sustituto;
      m.appendReplacement(salida, Matcher.quoteReplacement(sustituto));
    }
    m.appendTail(salida);
    return salida.toString();
  }
}