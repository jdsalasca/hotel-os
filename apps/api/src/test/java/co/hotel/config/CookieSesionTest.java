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

  private static String valor(String setCookie, String nombre) {
    return setCookie.split(";", 2)[0].substring((nombre + "=").length());
  }

  @Test
  @DisplayName("el login emite JSESSIONID con Secure y SameSite=Strict")
  void cookieDeSesionEndurecida() throws Exception {
    String base = "http://127.0.0.1:" + puerto;

    HttpResponse<String> salud = http.send(HttpRequest.newBuilder(URI.create(base + "/api/health"))
      .GET().build(), HttpResponse.BodyHandlers.ofString());
    assertEquals(200, salud.statusCode());
    String xsrf = galleta(salud.headers().allValues("set-cookie"), "XSRF-TOKEN");
    String valor = valor(xsrf, "XSRF-TOKEN");

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

  @Test
  @DisplayName("el login manual rota el identificador de sesión")
  void loginRotaIdDeSesion() throws Exception {
    // Fijación de sesión: si alguien plantó el JSESSIONID antes del login, tras
    // autenticar ese id debe morir y solo el nuevo entra. Sin rotación, el id
    // conocido por el atacante sigue válido autenticado.
    String base = "http://127.0.0.1:" + puerto;

    HttpResponse<String> salud = http.send(HttpRequest.newBuilder(URI.create(base + "/api/health"))
      .GET().build(), HttpResponse.BodyHandlers.ofString());
    String valorXsrf = valor(galleta(salud.headers().allValues("set-cookie"), "XSRF-TOKEN"), "XSRF-TOKEN");

    HttpResponse<String> primero = acceso(base, valorXsrf, null);
    assertEquals(200, primero.statusCode(), "cuerpo=" + primero.body());
    String sesionA = valor(galleta(primero.headers().allValues("set-cookie"), "JSESSIONID"), "JSESSIONID");

    HttpResponse<String> segundo = acceso(base, valorXsrf, sesionA);
    assertEquals(200, segundo.statusCode(), "cuerpo=" + segundo.body());
    // Sin rotación Tomcat no emite Set-Cookie (el id sigue siendo el mismo): la ausencia
    // ya es la prueba de que no rotó. Con rotación, el id nuevo viaja aquí sí o sí.
    String crudaB = segundo.headers().allValues("set-cookie").stream()
      .filter(c -> c.startsWith("JSESSIONID=")).findFirst().orElse(null);
    assertNotNull(crudaB, "el segundo login no emitió JSESSIONID: no hubo rotación");
    String sesionB = valor(crudaB, "JSESSIONID");
    assertNotEquals(sesionA, sesionB, "el login debe rotar el JSESSIONID");

    assertEquals(401, sesion(base, sesionA).statusCode(), "el id anterior muere con el login");
    assertEquals(200, sesion(base, sesionB).statusCode(), "el id nuevo entra al panel");
  }

  private HttpResponse<String> acceso(String base, String valorXsrf, String sesion) throws Exception {
    var req = HttpRequest.newBuilder(URI.create(base + "/api/admin/login"))
      .header("Content-Type", "application/json")
      .header("Cookie", "XSRF-TOKEN=" + valorXsrf + (sesion == null ? "" : "; JSESSIONID=" + sesion))
      .header("X-XSRF-TOKEN", valorXsrf)
      .POST(HttpRequest.BodyPublishers.ofString(
        JSON.writeValueAsString(Map.of("email", "admin", "password", "admin"))));
    return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> sesion(String base, String valorSesion) throws Exception {
    return http.send(HttpRequest.newBuilder(URI.create(base + "/api/admin/sesion"))
      .header("Cookie", "JSESSIONID=" + valorSesion)
      .GET().build(), HttpResponse.BodyHandlers.ofString());
  }

  @Test
  @DisplayName("cerrar sesión invalida la sesión: la misma cookie ya no entra al panel")
  void cerrarSesionInvalidaLaSesion() throws Exception {
    String base = "http://127.0.0.1:" + puerto;

    HttpResponse<String> salud = http.send(HttpRequest.newBuilder(URI.create(base + "/api/health"))
      .GET().build(), HttpResponse.BodyHandlers.ofString());
    String valorXsrf = valor(galleta(salud.headers().allValues("set-cookie"), "XSRF-TOKEN"), "XSRF-TOKEN");

    HttpResponse<String> acceso = http.send(HttpRequest.newBuilder(URI.create(base + "/api/admin/login"))
      .header("Content-Type", "application/json")
      .header("Cookie", "XSRF-TOKEN=" + valorXsrf)
      .header("X-XSRF-TOKEN", valorXsrf)
      .POST(HttpRequest.BodyPublishers.ofString(
        JSON.writeValueAsString(Map.of("email", "admin", "password", "admin"))))
      .build(), HttpResponse.BodyHandlers.ofString());
    assertEquals(200, acceso.statusCode(), "cuerpo=" + acceso.body());
    String valorSesion = valor(galleta(acceso.headers().allValues("set-cookie"), "JSESSIONID"), "JSESSIONID");

    HttpResponse<String> salida = http.send(HttpRequest.newBuilder(URI.create(base + "/api/admin/logout"))
      .header("Cookie", "JSESSIONID=" + valorSesion + "; XSRF-TOKEN=" + valorXsrf)
      .header("X-XSRF-TOKEN", valorXsrf)
      .POST(HttpRequest.BodyPublishers.noBody())
      .build(), HttpResponse.BodyHandlers.ofString());
    assertEquals(200, salida.statusCode(), "cuerpo=" + salida.body());
    assertTrue(salida.body().contains("sesi"), "la API debe confirmar el cierre: " + salida.body());

    HttpResponse<String> despues = http.send(HttpRequest.newBuilder(
        URI.create(base + "/api/admin/reservas?limit=1"))
      .header("Cookie", "JSESSIONID=" + valorSesion)
      .GET().build(), HttpResponse.BodyHandlers.ofString());
    assertEquals(401, despues.statusCode(),
      "la cookie cerrada no puede seguir entrando al panel: " + despues.statusCode());
  }
}
