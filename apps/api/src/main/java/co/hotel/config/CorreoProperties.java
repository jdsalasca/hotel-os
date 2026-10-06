package co.hotel.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Envío de correo mediante OAuth 2.0 de Google (Gmail API).
 *
 * Sin valores por defecto y sin secretos: los valores vienen del entorno. Si el hotel no
 * configura nada, el servicio de correo queda desactivado y lo dice, en vez de fingir un envío.
 *
 * El flujo es refresh-token → access-token, porque un refresh token no caduca mientras el hotel
 * no revoque el acceso. El access token de corta duración nunca se guarda: se pide en cada envío.
 */
public record CorreoProperties(
    @DefaultValue("false") boolean habilitado,
    /** Correo remitente visible en el mensaje. */
    @DefaultValue("") String remitente,
    /** Nombre que ve el huésped como remitente. */
    @DefaultValue("Hotel") String remitenteNombre,
    @DefaultValue("") String clientId,
    @DefaultValue("") String clientSecret,
    @DefaultValue("") String refreshToken,
    /** Solo para pruebas con servidor local. Apunta el conector a otro host. */
    @DefaultValue("") String endpointOAuth,
    @DefaultValue("") String endpointGmail) {

  /** Configuración completa: sin estos cuatro campos no hay envío. */
  public boolean estaConfigurado() {
    return habilitado && !isVacio(remitente) && !isVacio(clientId)
      && !isVacio(clientSecret) && !isVacio(refreshToken);
  }

  /** Por qué no se puede enviar. Vacío cuando sí se puede. */
  public String motivoDeshabilitado() {
    if (!habilitado) return "envío de correo deshabilitado (hotel.correo.habilitado=false)";
    if (isVacio(remitente)) return "falta hotel.correo.remitente";
    if (isVacio(clientId)) return "falta hotel.correo.client-id";
    if (isVacio(clientSecret)) return "falta hotel.correo.client-secret";
    if (isVacio(refreshToken)) return "falta hotel.correo.refresh-token";
    return "";
  }

  private static boolean isVacio(String v) { return v == null || v.isBlank(); }
}