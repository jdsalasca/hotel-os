package co.hotel.seguridad;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

/**
 * Sin tope, un cuerpo JSON gigante se lee entero en memoria: `server.tomcat.*post-size`
 * solo limita formularios, no JSON. El cuerpo más grande legítimo (un lote de 366 noches)
 * no llega a 100 KB; 1 MB sobra y corta el abuso.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LimiteCuerpoJsonTest {

  private static final Path DB = crearBase();
  private static final RequestPostProcessor ADMIN = user("admin@hotel.test").roles("ADMIN");
  private static final ObjectMapper JSON = new ObjectMapper();

  private static Path crearBase() {
    try {
      Path p = Files.createTempFile("hotel-cuerpo-", ".sqlite3");
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
  @DisplayName("un cuerpo gigante en el panel es 413, no un 201 con megabytes guardados")
  void cuerpoGiganteEnAdminEs413() throws Exception {
    String gigante = "x".repeat(2 * 1024 * 1024);
    mvc.perform(post("/api/admin/tipos").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("codigo", gigante, "nombre", "Gigante",
          "capacidadMax", 2))))
      .andExpect(status().isPayloadTooLarge())
      .andExpect(jsonPath("$.error").exists());
  }

  @Test
  @DisplayName("un cuerpo gigante en la reserva pública es 413")
  void cuerpoGiganteEnPublicoEs413() throws Exception {
    String gigante = "x".repeat(2 * 1024 * 1024);
    mvc.perform(post("/api/reservas").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("email", "a@hotel.test", "nombre", gigante,
          "llegada", "2027-05-01", "salida", "2027-05-03", "huespedes", 2))))
      .andExpect(status().isPayloadTooLarge())
      .andExpect(jsonPath("$.error").exists());
  }

  @Test
  @DisplayName("un cuerpo normal pasa sin tocarlo")
  void cuerpoNormalPasa() throws Exception {
    mvc.perform(post("/api/admin/tipos").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("codigo", "PEQ", "nombre", "Pequeño",
          "capacidadMax", 2))))
      .andExpect(status().is2xxSuccessful());
  }
}
