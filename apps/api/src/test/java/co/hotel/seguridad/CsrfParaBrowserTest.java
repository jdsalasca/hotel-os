package co.hotel.seguridad;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * CSRF para el panel React, verificado como lo hace un navegador.
 *
 * Vive en su propia clase a propósito: MockMvc reutiliza la sesión entre peticiones de la misma
 * clase, y una sesión previa con token guardado hace que la cookie no se vuelva a emitir. Contra
 * el servidor real (curl) el flujo se comprobó aparte y emite la cookie en cada GET.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CsrfParaBrowserTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @DynamicPropertySource
  static void propiedades(DynamicPropertyRegistry reg) {
    reg.add("hotel.jdbc-path", () -> {
      try {
        Path p = Files.createTempFile("hotel-csrf-", ".sqlite3");
        Files.delete(p);
        return p.toAbsolutePath().toString();
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    });
  }

  @Autowired MockMvc mvc;
  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

  @Test
  @DisplayName("el navegador recibe XSRF-TOKEN y puede escribir enviándolo de vuelta")
  void elNavegadorRecibeElTokenYEscribe() throws Exception {
    jdbc.update("INSERT OR IGNORE INTO rooms(codigo, estado, nombre) VALUES('101','ACTIVA','Habitación 101')");

    var respuesta = mvc.perform(get("/api/health")).andExpect(status().isOk()).andReturn();
    var cookie = java.util.Arrays.stream(respuesta.getResponse().getCookies())
      .filter(c -> "XSRF-TOKEN".equals(c.getName())).findFirst().orElse(null);
    org.junit.jupiter.api.Assertions.assertNotNull(cookie,
      "sin XSRF-TOKEN el panel React no puede enviar ninguna escritura");

    mvc.perform(post("/api/reservas")
        .cookie(cookie)
        .header("X-XSRF-TOKEN", cookie.getValue())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("email", "carla@example.com", "llegada", "2026-12-01",
          "salida", "2026-12-03", "huespedes", 1, "roomId", 1))))
      .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("sin token, la escritura se rechaza: la protección sigue activa")
  void sinTokenLaEscrituraSeRechaza() throws Exception {
    mvc.perform(post("/api/reservas")
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("email", "sin-token@example.com", "llegada", "2026-12-10",
          "salida", "2026-12-12", "huespedes", 1, "roomId", 1))))
      .andExpect(status().isForbidden());
  }
}