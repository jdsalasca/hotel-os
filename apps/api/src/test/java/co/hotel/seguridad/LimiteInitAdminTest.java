package co.hotel.seguridad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * `POST /api/admin/init` no tiene sesión: su única barrera es ADMIN_INIT_TOKEN, y quien lo adivine
 * crea la cuenta de administrador con la contraseña que elija. Un endpoint abierto que acepta un
 * secreto necesita el mismo tope de intentos que el login, o basta un bucle.
 */
@SpringBootTest(properties = "hotel.admin-init-token=token-de-prueba-largo-123456")
@AutoConfigureMockMvc
class LimiteInitAdminTest {

  private static final Path DB = crearBase();
  private static final ObjectMapper JSON = new ObjectMapper();

  private static Path crearBase() {
    try {
      Path p = Files.createTempFile("hotel-init-", ".sqlite3");
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

  private int intentos(String ip, int cuantos) throws Exception {
    int ultimo = 0;
    for (int i = 0; i < cuantos; i++) {
      ultimo = mvc.perform(post("/api/admin/init").with(csrf())
          .with(req -> { req.setRemoteAddr(ip); return req; })
          .contentType(MediaType.APPLICATION_JSON)
          .content(JSON.writeValueAsString(Map.of(
            "token", "intento-" + i, "email", "nuevo@hotel.test",
            "password", "contrasena-larga-1234"))))
        .andReturn().getResponse().getStatus();
    }
    return ultimo;
  }

  @Test
  @DisplayName("adivinar el token de arranque se topa con un 429")
  void adivinarElTokenSeTope() throws Exception {
    int antes = jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);

    assertEquals(403, intentos("10.5.0.1", 5), "los primeros intentos responden token inválido");
    assertEquals(429, intentos("10.5.0.1", 1), "seguir adivinando tiene que toparse");

    assertEquals(antes, jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class),
      "nada de esto puede crear usuarios");
  }

  @Test
  @DisplayName("el tope del init no cierra el arranque a otra conexión")
  void elTopeDelInitEsPorIp() throws Exception {
    intentos("10.5.0.2", 10);

    // Otra IP: el token equivocado sigue siendo 403 y no 429.
    assertEquals(403, intentos("10.5.0.3", 1));
  }
}