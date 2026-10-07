package co.hotel.seguridad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * El login está topado por `correo|IP`: cinco intentos contra un correo, y después a otro. Eso
 * frena el ataque a una cuenta pero no a un origen, que es lo que hace el credential stuffing:
 * probar cinco contraseñas de mil correos distintos desde la misma máquina.
 *
 * Con el tope por IP, esa pasada entera se corta. El de correo se queda, porque también protege
 * el caso de un atacante con muchos orígenes contra una sola cuenta.
 */
@SpringBootTest(properties = "hotel.admin-init-token=token-de-prueba-largo-123456")
@AutoConfigureMockMvc
class LoginPorIpTest {

  private static final Path DB = crearBase();
  private static final ObjectMapper JSON = new ObjectMapper();

  private static Path crearBase() {
    try {
      Path p = Files.createTempFile("hotel-lip-", ".sqlite3");
      Files.delete(p);
      return p;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @DynamicPropertySource
  static void propiedades(DynamicPropertyRegistry reg) {
    reg.add("hotel.jdbc-path", () -> DB.toAbsolutePath().toString());
  }

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  private int intento(String ip, String email) throws Exception {
    return mvc.perform(post("/api/admin/login").with(csrf())
        .with(req -> { req.setRemoteAddr(ip); return req; })
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("email", email, "password", "no-es-la-clave"))))
      .andReturn().getResponse().getStatus();
  }

  @Test
  @DisplayName("probar muchos correos desde una IP se corta en el camino")
  void stuffingDeCorreosSeCorta() throws Exception {
    // Con el tope por IP sola, la pasada entera se corta al quinto fallo aunque cada intento sea
    // contra un correo distinto: es lo que el índice `correo|IP` no veía.
    int permitidos = LoginThrottle.MAX_INTENTOS;
    for (int i = 1; i <= permitidos; i++) {
      assertEquals(401, intento("10.9.0.1", "objetivo" + i + "@hotel.test"),
        "intento " + i + " debe seguir dando credenciales inválidas");
    }
    assertEquals(429, intento("10.9.0.1", "otro@hotel.test"),
      "cambiar de correo no evita el tope: la conexión ya está topada");
  }

  @Test
  @DisplayName("otra IP no se topa por lo que haga esta")
  void elTopeEsPorIp() throws Exception {
    for (int i = 1; i <= 8; i++) intento("10.9.0.2", "mismo@hotel.test");

    assertEquals(401, intento("10.9.0.3", "mismo@hotel.test"),
      "otra conexión puede seguir intentando: el tope es por origen, no global");
  }

  @Test
  @DisplayName("el tope no revela si el correo existe")
  void elTopeNoEnumeraCuentas() throws Exception {
    // Que el mensaje sea el mismo exista o no la cuenta es lo que impide enumerar usuarios.
    String deExistente = mvc.perform(post("/api/admin/login").with(csrf())
        .with(req -> { req.setRemoteAddr("10.9.0.4"); return req; })
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("email", "admin", "password", "incorrecta"))))
      .andReturn().getResponse().getContentAsString();

    String deInexistente = mvc.perform(post("/api/admin/login").with(csrf())
        .with(req -> { req.setRemoteAddr("10.9.0.5"); return req; })
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("email", "nadie-aqui@hotel.test", "password", "incorrecta"))))
      .andReturn().getResponse().getContentAsString();

    assertEquals(deInexistente, deExistente,
      "la respuesta no puede distinguir una cuenta real de una inventada");
  }
}