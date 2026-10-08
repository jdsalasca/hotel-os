package co.hotel.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * El nombre visible es del dueño de la sesión y se puede ajustar sin pasar por
 * Google: con la clave no hay given_name que lo ponga, y el saludo no puede
 * quedarse en el correo cortado para siempre.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminPerfilTest {

  private static final Path DB = crearBase();
  private static final ObjectMapper JSON = new ObjectMapper();

  private static Path crearBase() {
    try {
      Path p = Files.createTempFile("hotel-perfil-", ".sqlite3");
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

  private String cuerpo(Object o) {
    try {
      return JSON.writeValueAsString(o);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  @DisplayName("el admin pone su nombre y la sesión lo muestra")
  void nombreAjustable() throws Exception {
    jdbc.update("INSERT INTO users(email,hash,rol,activo,creado_en,nombre)"
      + " VALUES('perfil@hotel.test','x','ADMIN',1,'2030-01-01','')");
    var admin = user("perfil@hotel.test").roles("ADMIN");

    mvc.perform(post("/api/admin/perfil").with(admin).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo(Map.of("nombre", "  Carolina Ruiz  "))))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.nombre").value("Carolina Ruiz"));

    assertEquals("Carolina Ruiz", jdbc.queryForObject(
      "SELECT nombre FROM users WHERE email='perfil@hotel.test'", String.class));

    mvc.perform(get("/api/admin/sesion").with(admin))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.nombre").value("Carolina Ruiz"));

    mvc.perform(post("/api/admin/perfil").with(admin).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo(Map.of("nombre", "x"))))
      .andExpect(status().isBadRequest());

    mvc.perform(post("/api/admin/perfil").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo(Map.of("nombre", "Otro"))))
      .andExpect(status().isUnauthorized());
  }
}
