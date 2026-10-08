package co.hotel.reservas;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * El límite del listado y del CSV del panel tiene contrato: en SQLite LIMIT -1
 * es sin tope, así que `?limite=-1` sin validar vuelca la tabla. Y lo que no es
 * número responde 400 con motivo, no con el cuerpo crudo de Spring.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminReservasLimiteTest {

  private static final Path DB = crearBase();
  private static final RequestPostProcessor ADMIN = user("admin@hotel.test").roles("ADMIN");

  private static Path crearBase() {
    try {
      Path p = Files.createTempFile("hotel-limite-", ".sqlite3");
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
  @DisplayName("listar con límite negativo es 400, no la tabla entera")
  void listarConLimiteNegativoEs400() throws Exception {
    mvc.perform(get("/api/admin/reservas").with(ADMIN).param("limite", "-1"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.error").exists());
  }

  @Test
  @DisplayName("listar con límite cero es 400")
  void listarConLimiteCeroEs400() throws Exception {
    mvc.perform(get("/api/admin/reservas").with(ADMIN).param("limite", "0"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.error").exists());
  }

  @Test
  @DisplayName("listar con límite que no es número es 400 con motivo")
  void listarConLimiteNoNumericoEs400() throws Exception {
    mvc.perform(get("/api/admin/reservas").with(ADMIN).param("limite", "muchas"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.error").exists());
  }

  @Test
  @DisplayName("exportar con límite negativo es 400")
  void exportarConLimiteNegativoEs400() throws Exception {
    mvc.perform(get("/api/admin/reservas.csv").with(ADMIN).param("limite", "-1"))
      .andExpect(status().isBadRequest());
  }
}
