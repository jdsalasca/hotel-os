package co.hotel.config;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.ObjectMapper;

/**
 * La cookie de sesión en producción: solo HTTPS y solo mismo sitio. Este test habla HTTP de
 * verdad contra el contenedor embebido, porque MockMvc no aplica las banderas de la cookie del
 * contenedor real. Si estas banderas no llegan a la Set-Cookie, el endurecimiento del compose es
 * papel mojado.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties = {
    "server.servlet.session.cookie.secure=true",
    "server.servlet.session.cookie.same-site=strict"
  })
class CookieSesionTest {

  @DynamicPropertySource
  static void propiedades(DynamicPropertyRegistry reg) {
    reg.add("hotel.jdbc-path", () -> {
      try {
        Path p = Files.createTempFile("hotel-cookie-", ".sqlite3");
        Files.delete(p);
        return p.toAbsolutePath().toString();
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    });
    reg.add("hotel.demo.admin", () -> "true");
  }

  @LocalServerPort int puerto;

  private final HttpClient http = HttpClient.newBuilder()
    .followRedirects(HttpClient.Redirect.NEVER).build();
  private static final ObjectMapper JSON = new ObjectMapper();

  private String galleta(List<String> cabeceras, String nombre) {
    assertNotNull(cabeceras, "el contenedor debe emitir Set-Cookie");
    return cabeceras.stream().filter(c -> c.startsWith(nombre + "=")).findFirst()
      .orElseThrow(() -> new AssertionError("sin " + nombre + " en: " + cabeceras));
  }

  @Test
  @DisplayName("el login emite JSESSIONID con Secure y SameSite=Strict")
  void cookieDeSesionEndurecida() throws Exception {
    String base = "http://127.0.0.1:" + puerto;

    HttpResponse<String> salud = http.send(HttpRequest.newBuilder(URI.create(base + "/api/health"))
      .GET().build(), HttpResponse.BodyHandlers.ofString());
    assertEquals(200, salud.statusCode());
    String xsrf = galleta(salud.headers().allValues("set-cookie"), "XSRF-TOKEN");
    String valor = xsrf.split(";", 2)[0].substring("XSRF-TOKEN=".length());

    HttpResponse<String> acceso = http.send(HttpRequest.newBuilder(URI.create(base + "/api/admin/login"))
      .header("Content-Type", "application/json")
      .header("Cookie", "XSRF-TOKEN=" + valor)
      .header("X-XSRF-TOKEN", valor)
      .POST(HttpRequest.BodyPublishers.ofString(
        JSON.writeValueAsString(Map.of("email", "admin", "password", "admin"))))
      .build(), HttpResponse.BodyHandlers.ofString());
    assertEquals(200, acceso.statusCode(), "cuerpo=" + acceso.body());

    String sesion = galleta(acceso.headers().allValues("set-cookie"), "JSESSIONID");
    assertTrue(sesion.contains("Secure"), "falta Secure: " + sesion);
    assertTrue(sesion.toLowerCase().contains("samesite=strict"), "falta SameSite=Strict: " + sesion);
    assertTrue(sesion.contains("HttpOnly"), "falta HttpOnly: " + sesion);
  }
}
