package co.hotel.ota;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Conector de Booking.com contra un servidor local que imita las respuestas documentadas.
 *
 * Lo que se fija, según developers.booking.com (consultado 2026-10-06):
 * - El intercambio de token ocurre en .../token-based-authentication/exchange con client_id y
 *   client_secret, y devuelve un token que luego viaja como Authorization: Bearer.
 * - El esquema credential-based se apagó el 31/12/2025: no se implementa.
 * - Un 401 en el intercambio no reintenta y no deja el canal "conectado".
 */
class BookingConectorTest {

  private HttpServer servidor;
  private String base;
  private final List<String> peticiones = new ArrayList<>();
  private volatile int codigoIntercambio = 200;
  private volatile String cuerpoIntercambio = "{\"access_token\":\"jwt-de-prueba\",\"expires_in\":3600}";
  private volatile String cuerpoReservas = "<OTA_HotelResNotifResponse></OTA_HotelResNotifResponse>";

  @BeforeEach
  void arrancar() throws IOException {
    servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    servidor.createContext("/token-based-authentication/exchange", this::intercambio);
    servidor.createContext("/hotels/ota/OTA_HotelResNotif", this::reservas);
    servidor.start();
    base = "http://127.0.0.1:" + servidor.getAddress().getPort();
  }

  @AfterEach
  void apagar() { servidor.stop(0); }

  private void intercambio(HttpExchange e) throws IOException {
    peticiones.add("intercambio " + e.getRequestMethod());
    responder(e, codigoIntercambio, cuerpoIntercambio);
  }

  private void reservas(HttpExchange e) throws IOException {
    String auth = e.getRequestHeaders().getFirst("Authorization");
    peticiones.add("reservas auth=" + (auth == null ? "ausente" : "presente"));
    responder(e, 200, cuerpoReservas);
  }

  private void responder(HttpExchange e, int codigo, String cuerpo) throws IOException {
    byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
    e.sendResponseHeaders(codigo, bytes.length);
    e.getResponseBody().write(bytes);
    e.close();
  }

  private BookingConector conector() {
    CanalConfig cfg = CanalConfig.de(Canal.BOOKING, Map.of(
      "BOOKING_CLIENT_ID", "id-de-prueba",
      "BOOKING_CLIENT_SECRET", "secreto-de-prueba",
      "BOOKING_HOTEL_ID", "8135188",
      "BOOKING_ENV", "sandbox",
      "BOOKING_API_BASE", base));
    return new BookingConector(cfg, new HttpClienteOta(Duration.ofSeconds(3), 1, Duration.ZERO));
  }

  @Test
  @DisplayName("el token se pide una vez y las reservas viajan con Authorization: Bearer")
  void pideTokenYLoEnviaComoBearer() {
    assertTrue(conector().sincronizarReservas().exitosa());
    assertEquals("intercambio POST", peticiones.get(0));
    assertEquals("reservas auth=presente", peticiones.get(1));
  }

  @Test
  @DisplayName("un 401 en el intercambio falla la sincronización y no se reintenta")
  void un401NoDejaElCanalConectado() {
    codigoIntercambio = 401;
    cuerpoIntercambio = "{\"error\":\"invalid client_id and/or client_secret\"}";

    var resultado = conector().sincronizarReservas();
    assertFalse(resultado.exitosa());
    assertTrue(resultado.detalle().contains("401"));
    assertFalse(resultado.detalle().contains("secreto-de-prueba"), "la bitácora no filtra el secreto");
  }

  @Test
  @DisplayName("sin credenciales el conector no intenta llamar y lo explica")
  void sinCredencialesNoIntentaLlamar() {
    CanalConfig cfg = CanalConfig.de(Canal.BOOKING, Map.of("BOOKING_ENV", "sandbox"));
    var resultado = new BookingConector(cfg, new HttpClienteOta(Duration.ofSeconds(3), 1, Duration.ZERO))
      .sincronizarReservas();
    assertFalse(resultado.exitosa());
    assertTrue(resultado.detalle().toLowerCase().contains("credencial"));
    assertTrue(peticiones.isEmpty(), "no debe haber ninguna llamada al proveedor");
  }

  @Test
  @DisplayName("el endpoint de reservas es el documentado para el entorno configurado")
  void usaElEndpointDocumentado() {
    CanalConfig cfg = CanalConfig.de(Canal.BOOKING, Map.of(
      "BOOKING_CLIENT_ID", "id", "BOOKING_CLIENT_SECRET", "s", "BOOKING_HOTEL_ID", "1",
      "BOOKING_ENV", "sandbox", "BOOKING_API_BASE", base));
    assertEquals("sandbox", cfg.valor("ENV"));
    assertTrue(new BookingConector(cfg, new HttpClienteOta(Duration.ofSeconds(1), 1, Duration.ZERO))
      .sincronizarReservas().exitosa());
    assertEquals(2, peticiones.size());
  }
}