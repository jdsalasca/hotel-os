package co.hotel.ota;

import java.time.Duration;
import java.util.Map;

/**
 * Conector de Booking.com Connectivity APIs.
 *
 * Documentación: developers.booking.com/connectivity/docs (Authentication, token-based
 * authentication, Reservations API) — consultada el 2026-10-06.
 *
 * Decisión importante: el esquema de autenticación credential-based (Basic) fue apagado por el
 * proveedor el 31 de diciembre de 2025. Este conector usa solo el esquema vigente: intercambio de
 * token en .../token-based-authentication/exchange y Authorization: Bearer en el resto.
 *
 * Requiere, además del código: ser Connectivity Partner, credenciales de máquina, ID de
 * alojamiento y certificaciones del proveedor. Sin eso el canal queda en NO_CONFIGURADO/ACCESO
 * PENDIENTE; nunca "conectado".
 */
public class BookingConector implements ConectorOta {
  private static final String RUTA_INTERCAMBIO = "/token-based-authentication/exchange";
  private static final String RUTA_RESERVAS = "/hotels/ota/OTA_HotelResNotif";

  private final CanalConfig config;
  private final HttpClienteOta http;

  public BookingConector(CanalConfig config, HttpClienteOta http) {
    this.config = config;
    this.http = http;
  }

  @Override
  public Canal canal() { return Canal.BOOKING; }

  @Override
  public String versionApiConsultada() { return "Connectivity APIs (documentación 2026-10-06)"; }

  /**
   * Intercambia credenciales por token y consulta las reservas pendientes.
   * Un fallo en el intercambio no se reintenta: el proveedor ya respondió sobre las credenciales.
   */
  public ResultadoSync sincronizarReservas() {
    if (config.credencialesFaltantes().size() > 1 || config.valor("CLIENT_ID") == null
        || config.valor("CLIENT_SECRET") == null) {
      return ResultadoSync.fallo("credenciales faltantes: " + String.join(", ",
        config.credencialesFaltantes()));
    }
    String hotelId = config.valor("HOTEL_ID");
    if (hotelId == null) return ResultadoSync.fallo("identificador de alojamiento faltante: BOOKING_HOTEL_ID");

    var token = intercambiarToken();
    if (!token.exitosa()) return ResultadoSync.fallo("intercambio de token rechazado: " + token.detalle());

    var respuesta = http.get(base() + RUTA_RESERVAS, Map.of("Authorization", "Bearer " + token.cuerpo()));
    if (!respuesta.exitosa()) return ResultadoSync.fallo("lectura de reservas rechazada: " + respuesta.detalle());

    return ResultadoSync.ok("reservas obtenidas (" + respuesta.codigo() + "), hotel " + hotelId);
  }

  private HttpClienteOta.Respuesta intercambiarToken() {
    String cuerpo = """
      {"client_id":"%s","client_secret":"%s"}"""
      .formatted(config.valor("CLIENT_ID"), config.valor("CLIENT_SECRET"));
    return http.post(base() + RUTA_INTERCAMBIO, Map.of(), cuerpo);
  }

  /** URL base del entorno configurado. Sin variable de entorno, los hosts oficiales por defecto. */
  private String base() {
    String configurada = config.valor("API_BASE");
    if (configurada != null) return configurada;
    return config.esProduccion() ? "https://secure-supply-xml.booking.com" : "https://connectivity-test.booking.com";
  }

  /** Cliente con la política de reintentos del conector: prudente, y solo para errores del servidor. */
  public static HttpClienteOta clientePorDefecto() {
    return new HttpClienteOta(Duration.ofSeconds(20), 3, Duration.ofMillis(500));
  }
}