package co.hotel.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El endpoint de salud lo consume el healthcheck de Docker: sin él, el contenedor nunca queda
 * sano y el proxy no levanta. Verifica además que responde sin sesión y que ve la base real.
 */
@SpringBootTest
@AutoConfigureMockMvc
class HealthControllerTest {

  private static final Path DB = crearBase();

  private static Path crearBase() {
    try {
      Path p = Files.createTempFile("hotel-health-", ".sqlite3");
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

  @Test
  @DisplayName("responde ok sin sesión y confirma que la base es accesible")
  void healthEsPublicoYVerificaLaBase() throws Exception {
    mvc.perform(get("/api/health"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.estado").value("ok"))
      .andExpect(jsonPath("$.base").value("accesible"));
  }
}