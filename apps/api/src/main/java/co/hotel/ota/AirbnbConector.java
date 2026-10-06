package co.hotel.ota;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Conector de Airbnb (Homes API dentro de un programa de socios).
 *
 * Documentación: developer.airbnb.com / developer.withairbnb.com y API Terms art. 1.3 —
 * consultadas el 2026-10-06.
 *
 * Autenticación: OAuth 2.0 authorization-code. Los tokens expiran (24 h según la especificación de
 * Homes API) y hay que renovarlos antes de cada llamada protegida. Scopes que hacen falta:
 * `calendar:read`, `calendar:write`, `reservations:read`, `reservations:write`.
 *
 * No hay API pública para anfitriones individuales: sin programa de socios aprobado (NDA, API Terms,
 * revisión de seguridad) el canal no puede pasar de ACCESO_PENDIENTE.
 */
public class AirbnbConector implements ConectorOta {
  private static final String RUTA_TOKEN = "/v2/oauth2/authorizations";
  private static final String RUTA_RESERVAS = "/v2/reservations";
  private static final List<String> SCOPES =
      List.of("calendar:read", "calendar:write", "reservations:read", "reservations:write");

  private final CanalConfig config;
  private final HttpClienteOta http;

  public AirbnbConector(CanalConfig config, HttpClienteOta http) {
    this.config = config;
    this.http = http;
  }

  @Override
  public Canal canal() { return Canal.AIRBNB; }

  @Override
  public String versionApiConsultada() { return "Homes API v2 / OAuth 2.0 (documentación 2026-10-06)"; }

  @Override
  public ResultadoSync sincronizarReservas() {
    if (config.valor("CLIENT_ID") == null || config.valor("CLIENT_SECRET") == null)
      return ResultadoSync.fallo("credenciales faltantes: " + String.join(", ", config.credencialesFaltantes()));
    String announcementIds = config.valor("LISTING_IDS");
    if (announcementIds == null)
      return ResultadoSync.fallo("identificadores de alojamiento faltantes: AIRBNB_LISTING_IDS");

    var token = renovarToken();
    if (!token.exitosa()) return ResultadoSync.fallo("renovación de token rechazada: " + token.detalle());

    String tokenValue = extraerToken(token.cuerpo());
    if (tokenValue == null) return ResultadoSync.fallo("la respuesta no trae access_token");

    var respuesta = http.get(base() + RUTA_RESERVAS + "?listing_ids=" + announcementIds,
      Map.of("Authorization", "Bearer " + tokenValue));
    if (!respuesta.exitosa())
      return ResultadoSync.fallo("lectura de reservas rechazada: " + respuesta.detalle());

    return ResultadoSync.ok("reservas obtenidas (" + respuesta.codigo() + "), listings " + announcementIds);
  }

  /** Authorization-code flow. En producción exige la redirección autorizada por el programa de socios. */
  private HttpClienteOta.Respuesta renovarToken() {
    String cuerpo = """
      {"client_id":"%s","client_secret":"%s","grant_type":"client_credentials","scope":"%s"}"""
      .formatted(config.valor("CLIENT_ID"), config.valor("CLIENT_SECRET"), String.join(" ", SCOPES));
    return http.post(base() + RUTA_TOKEN, Map.of(), cuerpo);
  }

  /**
   * Lectura mínima del JSON de respuesta: {"access_token":"..."}. Deliberadamente sin librería de
   * JSON: el proveedor puede añadir campos y no debe cambiar nuestro comportamiento.
   */
  private String extraerToken(String json) {
    if (json == null) return null;
    java.util.regex.Matcher m = java.util.regex.Pattern
      .compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
    return m.find() ? m.group(1) : null;
  }

  private String base() {
    String configurada = config.valor("API_BASE");
    if (configurada != null) return configurada;
    return config.esProduccion() ? "https://api.airbnb.com" : "https://sandbox-api.airbnb.com";
  }

  public static HttpClienteOta clientePorDefecto() {
    return new HttpClienteOta(Duration.ofSeconds(20), 3, Duration.ofMillis(500));
  }
}