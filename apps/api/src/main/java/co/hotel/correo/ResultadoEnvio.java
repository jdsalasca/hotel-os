package co.hotel.correo;

/** Resultado de intentar enviar un correo. Nunca dice "enviado" si el proveedor lo rechazó. */
public record ResultadoEnvio(boolean enviado, String motivo, String idMensaje) {

  public static ResultadoEnvio ok(String idMensaje) { return new ResultadoEnvio(true, "enviado", idMensaje); }

  public static ResultadoEnvio fallo(String motivo) {
    return new ResultadoEnvio(false, co.hotel.ota.Bitacora.sanear(motivo), null);
  }
}