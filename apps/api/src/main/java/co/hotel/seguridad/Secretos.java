package co.hotel.seguridad;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/** Comparación de secretos sin filtrar por tiempo. */
public final class Secretos {
  private Secretos() {}

  /**
   * `String.equals` corta en el primer carácter distinto, así que el tiempo de respuesta depende
   * de cuántos caracteres del token aciertan. Con un secreto corto eso acorta la búsqueda por fuerza
   * bruta. `MessageDigest.isEqual` compara del tiempo todo, sea cual sea el resultado.
   */
  public static boolean iguales(String esperado, String recibido) {
    if (esperado == null || recibido == null) return false;
    return MessageDigest.isEqual(esperado.getBytes(StandardCharsets.UTF_8),
      recibido.getBytes(StandardCharsets.UTF_8));
  }
}