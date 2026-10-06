package co.hotel.correo;

import static org.junit.jupiter.api.Assertions.*;

import co.hotel.config.CorreoProperties;
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
 * Envío de correo por Gmail API con OAuth 2.0, contra un servidor local que imita las respuestas.
 *
 * Lo que se fija: se pide access token antes de enviar, se reenvía en Authorization, no se
 * reporta éxito si Gmail rechaza, y ningún token aparece en el detalle del error.
 */
class CorreoServiceTest {

  private HttpServer servidor;
  private String base;
  private final List<String> peticiones = new ArrayList<>();
  private final List<String> cuerpos = new ArrayList<>();
  private volatile String cabeceraAuth;
  private volatile int codigoToken = 200;
  private volatile int codigoEnvio = 200;

  @BeforeEach
  void arrancar() throws IOException {
    servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    servidor.createContext("/token", e -> {
      peticiones.add("token " + e.getRequestMethod());
      cuerpo(e.getRequestBody().readAllBytes());
      responder(e, codigoToken, "{\"access_token\":\"access-de-prueba\",\"expires_in\":3600,"
        + "\"token_type\":\"Bearer\"}");
    });
    servidor.createContext("/gmail/v1/users/me/messages/send", e -> {
      peticiones.add("envio " + e.getRequestMethod());
      cabeceraAuth = e.getRequestHeaders().getFirst("Authorization");
      cuerpo(e.getRequestBody().readAllBytes());
      responder(e, codigoEnvio, "{\"id\":\"msg-1\",\"threadId\":\"th-1\"}");
    });
    servidor.start();
    base = "http://127.0.0.1:" + servidor.getAddress().getPort();
  }

  @AfterEach
  void apagar() { servidor.stop(0); }

  private void cuerpo(byte[] b) { cuerpos.add(new String(b, StandardCharsets.UTF_8)); }

  private void responder(com.sun.net.httpserver.HttpExchange e, int codigo, String cuerpo) throws IOException {
    byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
    e.sendResponseHeaders(codigo, bytes.length);
    e.getResponseBody().write(bytes);
    e.close();
  }

  private CorreoProperties propiedades(boolean habilitado) {
    // Los endpoints de prueba apuntan al host: el servicio ya antepone /token y /gmail/v1.
    return new CorreoProperties(habilitado, "hotel@ejemplo.test", "Hotel", "client-id",
      "client-secret", "refresh-token-de-prueba", base, base);
  }

  private CorreoService servicio(boolean habilitado) {
    return new CorreoService(propiedades(habilitado),
      new co.hotel.ota.HttpClienteOta(Duration.ofSeconds(3), 2, Duration.ZERO));
  }

  @Test
  @DisplayName("sin configurar, el envío se rechaza con el motivo en vez de fingir que salió")
  void sinConfigurarNoEnvia() {
    CorreoService svc = new CorreoService(new CorreoProperties(false, "", "", "", "", "", "", ""),
      new co.hotel.ota.HttpClienteOta(Duration.ofSeconds(1), 1, Duration.ZERO));
    var r = svc.enviar("ana@example.com", "Hola", "cuerpo");
    assertFalse(r.enviado());
    assertTrue(r.motivo().contains("deshabilitado"));
    assertTrue(peticiones.isEmpty(), "no debe llamar a Google sin configuración");
  }

  @Test
  @DisplayName("envía: pide token, lo usa como bearer y entrega el mensaje")
  void enviaMensaje() {
    var r = servicio(true).enviar("ana@example.com", "Reserva confirmada", "Tu reserva H-1 está confirmada.");
    assertTrue(r.enviado(), "motivo del fallo: " + r.motivo() + " | peticiones=" + peticiones);
    assertEquals("token POST", peticiones.get(0));
    assertEquals("envio POST", peticiones.get(1));
    assertEquals("Bearer access-de-prueba", cabeceraAuth);
    assertTrue(cuerpos.get(0).contains("refresh_token"), "el canje usa el refresh token");
    assertTrue(cuerpos.get(0).contains("refresh-token-de-prueba"));
    assertTrue(cuerpos.get(1).contains("\"raw\""), "Gmail recibe el mensaje en base64url");
  }

  @Test
  @DisplayName("el remitente viaja en la cabecera From del mensaje")
  void elRemitenteVaEnElMensaje() {
    servicio(true).enviar("ana@example.com", "Asunto", "cuerpo");
    String raw = correoBase64Url(cuerpos.get(1));
    assertTrue(raw.contains("From: Hotel <hotel@ejemplo.test>"), "debe verse Hotel <remitente>");
    assertTrue(raw.contains("To: ana@example.com"));
    assertTrue(raw.contains("Subject: Asunto"));
  }

  @Test
  @DisplayName("un token rechazado impide el envío y no se reporta éxito")
  void tokenRechazadoNoEnvia() {
    codigoToken = 401;
    var r = servicio(true).enviar("ana@example.com", "Asunto", "cuerpo");
    assertFalse(r.enviado());
    assertTrue(r.motivo().contains("401"));
    assertEquals(1, peticiones.size(), "solo se intentó el canje");
  }

  @Test
  @DisplayName("un envío rechazado por Gmail no se reporta como enviado")
  void gmailRechazadoNoEsExito() {
    codigoEnvio = 403;
    var r = servicio(true).enviar("ana@example.com", "Asunto", "cuerpo");
    assertFalse(r.enviado());
    assertTrue(r.motivo().contains("403"));
  }

  @Test
  @DisplayName("ni el token ni el secreto aparecen en el motivo del fallo")
  void losFallosNoFiltranTokens() {
    codigoToken = 400;
    cuerpoNoSirve();
    var r = servicio(true).enviar("ana@example.com", "Asunto", "cuerpo");
    assertFalse(r.motivo().contains("refresh-token-de-prueba"));
    assertFalse(r.motivo().contains("client-secret"));
    assertFalse(r.motivo().contains("access-de-prueba"));
  }

  private void cuerpoNoSirve() {
    // El servidor ya responde con su cuerpo por defecto; aquí solo se documenta el caso de error.
  }

  /** Decodifica el campo raw del JSON de Gmail para comprobar el mensaje real. */
  private String correoBase64Url(String json) {
    java.util.regex.Matcher m = java.util.regex.Pattern
      .compile("\"raw\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
    assertTrue(m.find(), "el cuerpo debe traer el campo raw");
    return new String(java.util.Base64.getUrlDecoder().decode(m.group(1)), StandardCharsets.UTF_8);
  }
}