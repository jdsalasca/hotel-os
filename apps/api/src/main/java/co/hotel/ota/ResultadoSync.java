package co.hotel.ota;

/**
 * Resultado de una sincronización. El detalle ya viene saneado: se puede guardar en la bitácora
 * sin filtrar secretos ni datos personales.
 */
public record ResultadoSync(boolean exitosa, String detalle) {

  public static ResultadoSync ok(String detalle) { return new ResultadoSync(true, Bitacora.sanear(detalle)); }

  public static ResultadoSync fallo(String detalle) { return new ResultadoSync(false, Bitacora.sanear(detalle)); }
}