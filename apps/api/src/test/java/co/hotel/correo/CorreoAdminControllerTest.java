package co.hotel.correo;

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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

/**
 * Pantalla de correo del panel. Sin configurar, debe explicar qué falta; nunca fingir un envío.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CorreoAdminControllerTest {

  private static final RequestPostProcessor ADMIN = user("admin@hotel.test").roles("ADMIN");
  private static final ObjectMapper JSON = new ObjectMapper();

  @DynamicPropertySource
  static void propiedades(DynamicPropertyRegistry reg) {
    reg.add("hotel.jdbc-path", () -> {
      try {
        Path p = Files.createTempFile("hotel-correo-", ".sqlite3");
        Files.delete(p);
        return p.toAbsolutePath().toString();
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    });
    // Sin configurar a propósito: este test fija el comportamiento cuando falta todo.
    reg.add("hotel.correo.habilitado", () -> "true");
  }

  @Autowired MockMvc mvc;

  @Test
  @DisplayName("el estado dice que falta configuración y por qué")
  void elEstadoExplicaLoQueFalta() throws Exception {
    mvc.perform(get("/api/admin/correo/estado").with(ADMIN))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.configurado").value(false))
      .andExpect(jsonPath("$.motivo").value(org.hamcrest.Matchers.containsString("remitente")));
  }

  @Test
  @DisplayName("sin configuración, el envío de prueba responde 502 y no dice enviado")
  void sinConfiguracionNoDiceEnviado() throws Exception {
    mvc.perform(post("/api/admin/correo/prueba").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("destinatario", "ana@example.com"))))
      .andExpect(status().isBadGateway())
      .andExpect(jsonPath("$.enviado").value(false));
  }

  @Test
  @DisplayName("un destinatario inválido se rechaza antes de tocar Google")
  void destinatarioInvalidoSeRechaza() throws Exception {
    mvc.perform(post("/api/admin/correo/prueba").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("destinatario", "no-es-correo"))))
      .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("la pantalla de correo exige sesión")
  void exigeSesion() throws Exception {
    mvc.perform(get("/api/admin/correo/estado")).andExpect(status().isUnauthorized());
  }
}