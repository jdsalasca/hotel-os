package co.hotel.admin;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Superusuarios sembrados (HOTEL_SEED_ADMINS) y cambio inicial obligatorio.
 *
 * La semilla solo crea lo que falta: si el correo ya tiene contraseña, se respeta. La clave
 * del entorno es de un solo uso: el login la valida pero no abre sesión hasta cambiarla.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CambioClaveAdminTest {

  private static final Path DB = crearBaseTemporal();

  private static Path crearBaseTemporal() {
    try {
      Path p = Files.createTempFile("hotel-seed-", ".sqlite3");
      Files.delete(p);
      return p;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @DynamicPropertySource
  static void propiedades(DynamicPropertyRegistry reg) {
    reg.add("hotel.jdbc-path", () -> DB.toAbsolutePath().toString());
    reg.add("hotel.seed-admins", () -> "semilla1@hotel.test:Semilla12345,semilla2@hotel.test:OtraSemilla12345");
  }

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;
  private final ObjectMapper json = new ObjectMapper();

  private String cuerpo(Map<String, String> mapa) {
    try {
      return json.writeValueAsString(mapa);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  @Order(1)
  @DisplayName("la semilla crea los faltantes con bandera de cambio")
  void laSemillaCreaFaltantes() {
    Integer n = jdbc.queryForObject(
      "SELECT COUNT(*) FROM users WHERE email LIKE 'semilla%@hotel.test'", Integer.class);
    assertEquals(2, n);
    Integer marcadas = jdbc.queryForObject(
      "SELECT COUNT(*) FROM users WHERE debe_cambiar_clave = 1", Integer.class);
    assertEquals(2, marcadas);
  }

  @Test
  @Order(2)
  @DisplayName("la semilla respeta la contraseña ya guardada")
  void laSemillaRespetaExistentes() throws Exception {
    jdbc.update("INSERT INTO users(email,hash,rol,activo,creado_en,debe_cambiar_clave)"
      + " VALUES('propia@hotel.test',?, 'ADMIN',1,datetime('now'),0)",
      encoder.encode("ClavePropia12345"));
    mvc.perform(post("/api/admin/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo(Map.of("email", "propia@hotel.test", "password", "ClavePropia12345"))))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.estado").value("autenticado"));
  }

  @Test
  @Order(3)
  @DisplayName("con la clave semilla el login exige el cambio y no abre sesión")
  void loginSemillaExigeCambio() throws Exception {
    mvc.perform(post("/api/admin/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo(Map.of("email", "semilla1@hotel.test", "password", "Semilla12345"))))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.estado").value("cambio_requerido"));

    mvc.perform(get("/api/admin/reservas")).andExpect(status().isUnauthorized());
  }

  @Test
  @Order(4)
  @DisplayName("el cambio rechaza actual incorrecta y nueva débil o igual")
  void cambioValidaEntradas() throws Exception {
    mvc.perform(post("/api/admin/password").with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo(Map.of("email", "semilla3@hotel.test", "actual", "NoExiste12345",
          "nueva", "NuevaClave12345"))))
      .andExpect(status().isUnauthorized());

    mvc.perform(post("/api/admin/password").with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo(Map.of("email", "semilla1@hotel.test", "actual", "Semilla12345",
          "nueva", "corta"))))
      .andExpect(status().isBadRequest());

    mvc.perform(post("/api/admin/password").with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo(Map.of("email", "semilla1@hotel.test", "actual", "Semilla12345",
          "nueva", "Semilla12345"))))
      .andExpect(status().isBadRequest());
  }

  @Test
  @Order(5)
  @DisplayName("tras el cambio, la semilla vieja muere y el login entra pleno")
  void cambioHabilitaSesionPlena() throws Exception {
    mvc.perform(post("/api/admin/password").with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo(Map.of("email", "semilla1@hotel.test", "actual", "Semilla12345",
          "nueva", "Definitiva12345"))))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.estado").value("clave actualizada"));

    mvc.perform(post("/api/admin/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo(Map.of("email", "semilla1@hotel.test", "password", "Semilla12345"))))
      .andExpect(status().isUnauthorized());

    var sesion = mvc.perform(post("/api/admin/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo(Map.of("email", "semilla1@hotel.test", "password", "Definitiva12345"))))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.estado").value("autenticado"))
      .andReturn();

    var mockSesion = sesion.getRequest().getSession(false);
    assertNotNull(mockSesion);
    mvc.perform(get("/api/admin/reservas")
        .session((org.springframework.mock.web.MockHttpSession) mockSesion))
      .andExpect(status().isOk());
  }
}
