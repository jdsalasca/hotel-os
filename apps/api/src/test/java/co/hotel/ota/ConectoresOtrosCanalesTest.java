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
 * Conectores de Despegar y Airbnb contra servidores locales que imitan sus respuestas.
 *
 * Lo que se fija según la documentación oficial (consultada 2026-10-06):
 * - Despegar: autenticación por cabecera `x-apikey` (ecosistema B2B `/v3`) y, según el canal hotelero,
 *   mTLS. La API key sola no habilita el canal hotelero.
 * - Airbnb: OAuth 2.0 authorization-code; los tokens expiran (24 h según la especificación de Homes
 *   API) y hay que renovarlos. Scopes necesarios: calendar:read/write, reservations:read/write.
 */
class ConectoresOtrosCanalesTest {

  private HttpServer servidor;
  private String base;
  private final List<String> peticiones = new ArrayList<>();
  private volatile String cabeceraRecibida;
  private volatile int codigo = 200;
  private volatile String cuerpo = "{}";

  @BeforeEach
  void arrancar() throws IOException {
    servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    servidor.createContext("/", this::responder);
    servidor.start();
    base = "http://127.0.0.1:" + servidor.getAddress().getPort();
  }

  @AfterEach
  void apagar() { servidor.stop(0); }

  private void responder(HttpExchange e) throws IOException {
    peticiones.add(e.getRequestMethod() + " " + e.getRequestURI().getPath()
      + " apikey=" + (e.getRequestHeaders().getFirst("x-apikey") != null ? "presente" : "ausente"));
    cabeceraRecibida = e.getRequestHeaders().getFirst("Authorization");
    byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
    e.sendResponseHeaders(codigo, bytes.length);
    e.getResponseBody().write(bytes);
    e.close();
  }

  private DespegarConector despegar() {
    return new DespegarConector(CanalConfig.de(Canal.DESPEGAR, Map.of(
      "DESPEGAR_API_KEY", "clave-de-prueba", "DESPEGAR_HOTEL_CODE", "H12345",
      "DESPEGAR_ENV", "sandbox", "DESPEGAR_API_BASE", base)),
      new HttpClienteOta(Duration.ofSeconds(3), 1, Duration.ZERO));
  }

  private AirbnbConector airbnb() {
    return new AirbnbConector(CanalConfig.de(Canal.AIRBNB, Map.of(
      "AIRBNB_CLIENT_ID", "id-de-prueba", "AIRBNB_CLIENT_SECRET", "secreto-de-prueba",
      "AIRBNB_LISTING_IDS", "111,222", "AIRBNB_ENV", "sandbox", "AIRBNB_API_BASE", base)),
      new HttpClienteOta(Duration.ofSeconds(3), 1, Duration.ZERO));
  }

  @Test
  @DisplayName("Despegar envía la API key en x-apikey, no como bearer")
  void despegarUsaCabeceraXApiKey() {
    assertTrue(despegar().sincronizarReservas().exitosa());
    assertTrue(peticiones.get(0).endsWith("apikey=presente"), "la cabecera x-apikey debe viajar");
    assertNull(cabeceraRecibida, "Despegar no usa Authorization");
  }

  @Test
  @DisplayName("Despegar sin API key no llama y lo explica")
  void despegarSinCredencialesNoLlama() {
    var resultado = new DespegarConector(CanalConfig.de(Canal.DESPEGAR, Map.of("DESPEGAR_ENV", "sandbox")),
      new HttpClienteOta(Duration.ofSeconds(1), 1, Duration.ZERO)).sincronizarReservas();
    assertFalse(resultado.exitosa());
    assertTrue(resultado.detalle().toLowerCase().contains("credencial"));
    assertTrue(peticiones.isEmpty());
  }

  @Test
  @DisplayName("Despegar con 403 reporta error y no se marca conectado")
  void despegarCon403ReportaError() {
    codigo = 403;
    cuerpo = "{\"error\":\"forbidden\"}";
    var resultado = despegar().sincronizarReservas();
    assertFalse(resultado.exitosa());
    assertTrue(resultado.detalle().contains("403"));
  }

  @Test
  @DisplayName("Airbnb usa OAuth 2.0: pide token y llama con bearer")
  void airbnbUsaOAuth2() {
    cuerpo = "{\"access_token\":\"tok\",\"expires_in\":86400}";
    assertTrue(airbnb().sincronizarReservas().exitosa());
    assertTrue(peticiones.get(0).contains("/oauth2/authorizations"), "primero el intercambio de token");
    assertEquals("Bearer tok", cabeceraRecibida, "luego bearer sobre el token obtenido");
  }

  @Test
  @DisplayName("Airbnb sin token no llama al recurso protegido")
  void airbnbSinTokenNoLlamaAlRecurso() {
    codigo = 401;
    cuerpo = "{\"error\":\"invalid_grant\"}";
    var resultado = airbnb().sincronizarReservas();
    assertFalse(resultado.exitosa());
    assertTrue(resultado.detalle().contains("401"));
    assertEquals(1, peticiones.size(), "solo el intento de token, no llamadas con un token inexistente");
  }

  @Test
  @DisplayName("cada conector declara el canal y la versión de API que consultó")
  void cadaConectorDeclaraSuVersion() {
    assertEquals(Canal.DESPEGAR, despegar().canal());
    assertEquals(Canal.AIRBNB, airbnb().canal());
    assertTrue(despegar().versionApiConsultada().contains("2026"));
    assertTrue(airbnb().versionApiConsultada().contains("2026"));
  }

  @Test
  @DisplayName("un secreto nunca aparece en el detalle del fallo")
  void losFallosNoFiltranSecretos() {
    codigo = 401;
    cuerpo = "{\"error\":\"bad client_secret: valor-real-secreto\"}";
    var resultado = airbnb().sincronizarReservas();
    assertFalse(resultado.detalle().contains("valor-real-secreto"));
    assertFalse(resultado.detalle().contains("secreto-de-prueba"));
  }
}