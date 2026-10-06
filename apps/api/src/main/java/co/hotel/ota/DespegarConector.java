package co.hotel.ota;

import java.time.Duration;
import java.util.Map;

/**
 * Conector de Despegar para conexión hotelera.
 *
 * Documentación: channel.despegar.com/portal/documentation y api-docs.despegar.com (API B2B `/v3`)
 * — consultadas el 2026-10-06.
 *
 * Autenticación: cabecera `x-apikey`. El canal hotelero además exige acuerdo de channel manager,
 * mTLS, HotelCode y certificaciones del proveedor; la API key por sí sola no habilita nada de eso,
 * y por eso el canal no se declara conectado solo por tener credenciales.
 */
public class DespegarConector implements ConectorOta {
  private static final String RUTA_BOOKINGS = "/v3/bookings";

  private final CanalConfig config;
  private final HttpClienteOta http;

  public DespegarConector(CanalConfig config, HttpClienteOta http) {
    this.config = config;
    this.http = http;
  }

  @Override
  public Canal canal() { return Canal.DESPEGAR; }

  @Override
  public String versionApiConsultada() { return "Channel API hotelera + API B2B v3 (documentación 2026-10-06)"; }

  @Override
  public ResultadoSync sincronizarReservas() {
    if (config.valor("API_KEY") == null)
      return ResultadoSync.fallo("credenciales faltantes: " + String.join(", ", config.credencialesFaltantes()));
    String hotelCode = config.valor("HOTEL_CODE");
    if (hotelCode == null)
      return ResultadoSync.fallo("identificador de alojamiento faltante: DESPEGAR_HOTEL_CODE");

    var respuesta = http.get(base() + RUTA_BOOKINGS + "?hotelCode=" + hotelCode,
      Map.of("x-apikey", config.valor("API_KEY")));
    if (!respuesta.exitosa())
      return ResultadoSync.fallo("lectura de reservas rechazada: " + respuesta.detalle());

    return ResultadoSync.ok("reservas obtenidas (" + respuesta.codigo() + "), hotel " + hotelCode);
  }

  private String base() {
    String configurada = config.valor("API_BASE");
    if (configurada != null) return configurada;
    return config.esProduccion() ? "https://api.despegar.com" : "https://sandbox-api.despegar.com";
  }

  public static HttpClienteOta clientePorDefecto() {
    return new HttpClienteOta(Duration.ofSeconds(20), 3, Duration.ofMillis(500));
  }
}