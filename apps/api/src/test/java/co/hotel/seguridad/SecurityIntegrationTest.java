package co.hotel.seguridad;
import static org.junit.jupiter.api.Assertions.*;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Seguridad contra la aplicación real: arranque del primer administrador, login, roles y CSRF.
 *
 * Fija un defecto concreto: guardar el hash sin el prefijo {bcrypt} hace que
 * BCryptPasswordEncoder.matches devuelva siempre false, así que el login nunca valida.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SecurityIntegrationTest {

  private static final Path DB = crearBaseTemporal();
  private static final String TOKEN_INIT = "token-de-prueba-no-real";

  private static Path crearBaseTemporal() {
    try {
      Path p = Files.createTempFile("hotel-seg-", ".sqlite3");
      Files.delete(p);
      return p;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @DynamicPropertySource
  static void propiedades(DynamicPropertyRegistry reg) {
    reg.add("hotel.jdbc-path", () -> DB.toAbsolutePath().toString());
    reg.add("hotel.admin-init-token", () -> TOKEN_INIT);
  }

  @Autowired MockMvc mvc;
  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

  /** Inventario mínimo del hotel de prueba. El esquema de producción no trae datos de demo. */
  @BeforeEach
  void crearInventario() {
    if (jdbc.queryForObject("SELECT COUNT(*) FROM rooms", Integer.class) == 0) {
      jdbc.update("INSERT INTO rooms(codigo, estado, nombre) VALUES('101','ACTIVA','Habitación 101')");
    }
  }

  @Test
  @Order(1)
  @DisplayName("sin el token secreto, el arranque del administrador se rechaza")
  void sinTokenNoSeCreaAdministrador() throws Exception {
    mvc.perform(post("/api/admin/init").with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content(json(Map.of("token", "incorrecto", "email", "admin@hotel.test", "password", "ClaveLarga12345"))))
      .andExpect(status().isForbidden());
  }

  @Test
  @Order(2)
  @DisplayName("con el token secreto, el primer administrador se crea una sola vez")
  void conTokenSeCreaAdministradorUnaVez() throws Exception {
    mvc.perform(post("/api/admin/init").with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content(json(Map.of("token", TOKEN_INIT, "email", "admin@hotel.test", "password", "ClaveLarga12345"))))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.estado").value("administrador creado"));

    mvc.perform(post("/api/admin/init").with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content(json(Map.of("token", TOKEN_INIT, "email", "otro@hotel.test", "password", "OtraClave12345"))))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.estado").value("ya existe administrador"));
  }

  @Test
  @Order(3)
  @DisplayName("la contraseña del administrador valida contra el hash almacenado")
  void elLoginValidaLaContrasena() throws Exception {
    mvc.perform(post("/api/admin/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content(json(Map.of("email", "admin@hotel.test", "password", "ClaveLarga12345"))))
      .andExpect(status().isOk());

    mvc.perform(post("/api/admin/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content(json(Map.of("email", "admin@hotel.test", "password", "ContrasenaIncorrecta"))))
      .andExpect(status().isUnauthorized());
  }

  @Test
  @Order(4)
  @DisplayName("el panel exige sesión administrativa")
  void elPanelExigeAutenticacion() throws Exception {
    mvc.perform(get("/api/admin/reservas")).andExpect(status().isUnauthorized());
  }

  @Test
  @Order(5)
  @DisplayName("una escritura sin token CSRF se rechaza: React no puede saltarse la protección")
  void escrituraSinCsrfSeRechaza() throws Exception {
    mvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_JSON)
        .content(json(Map.of("email", "ana@example.com", "nombre", "Ana",
          "llegada", "2026-11-01", "salida", "2026-11-03", "huespedes", 2, "roomId", 1))))
      .andExpect(status().isForbidden());
  }

  @Test
  @Order(6)
  @DisplayName("con CSRF válido, la reserva pública se registra y avisa que queda pendiente")
  void escrituraConCsrfSeRegistra() throws Exception {
    mvc.perform(post("/api/reservas").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(json(Map.of("email", "ana@example.com", "nombre", "Ana",
          "llegada", "2026-11-01", "salida", "2026-11-03", "huespedes", 2, "roomId", 1))))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.codigo").isNotEmpty())
      .andExpect(jsonPath("$.estado").value("PENDIENTE"))
      .andExpect(jsonPath("$.mensaje").value(containsString("pendiente de confirmación")));
  }

  @Test
  @Order(7)
  @DisplayName("la consulta de una reserva exige el correo con el que se reservó")
  void laConsultaExigeElCorreo() throws Exception {
    MvcResult alta = mvc.perform(post("/api/reservas").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(json(Map.of("email", "bruno@example.com", "llegada", "2026-11-10", "salida", "2026-11-12",
          "huespedes", 1, "roomId", 1))))
      .andExpect(status().isCreated()).andReturn();
    String codigo = extraer(alta.getResponse().getContentAsString(), "codigo");

    mvc.perform(get("/api/reservas/" + codigo).param("email", "bruno@example.com"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.codigo").value(codigo));

    mvc.perform(get("/api/reservas/" + codigo).param("email", "intruso@example.com"))
      .andExpect(status().isNotFound());
  }

  private static final ObjectMapper JSON = new ObjectMapper();

  private static String json(Map<String, ?> datos) throws Exception {
    return JSON.writeValueAsString(datos);
  }

  private static String extraer(String cuerpo, String campo) throws Exception {
    return JSON.readTree(cuerpo).get(campo).asText();
  }
}
