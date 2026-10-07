package co.hotel.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

/**
 * Quién hizo qué en el panel. Hoy el rastro es optativo por endpoint y el único existente
 * (reservation_history) toma el actor del cuerpo de la petición, o sea que el cliente decide a
 * quién se le atribuye el cambio. Estas pruebas fijan lo contrario: el actor sale de la sesión.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuditoriaAdminTest {

  private static final Path DB = crearBase();
  private static final RequestPostProcessor ADMIN = user("admin@hotel.test").roles("ADMIN");
  private static final ObjectMapper JSON = new ObjectMapper();

  private static Path crearBase() {
    try {
      Path p = Files.createTempFile("hotel-aud-", ".sqlite3");
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

  @BeforeEach
  void inventario() {
    if (jdbc.queryForObject("SELECT COUNT(*) FROM rooms", Integer.class) == 0) {
      jdbc.update("INSERT INTO rooms(codigo, estado, nombre) VALUES('101','ACTIVA','Habitación 101')");
    }
  }

  @Test
  @DisplayName("toda escritura del panel deja rastro con el usuario de la sesión")
  void todaAccionQuedaRegistrada() throws Exception {
    mvc.perform(post("/api/admin/tipos").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("codigo", "STD", "nombre", "Estándar", "capacidadMax", 3))))
      .andExpect(status().is2xxSuccessful());

    List<Map<String, Object>> rastro = jdbc.queryForList(
      "SELECT actor, metodo, ruta, estado FROM admin_actions WHERE ruta=? ORDER BY id DESC LIMIT 1",
      "/api/admin/tipos");
    assertThat(rastro).hasSize(1);
    assertThat(rastro.get(0))
      .containsEntry("ACTOR", "admin@hotel.test")
      .containsEntry("METODO", "POST")
      .containsEntry("RUTA", "/api/admin/tipos")
      .containsEntry("ESTADO", 201);
  }

  @Test
  @DisplayName("el actor del historial sale de la sesión, no del cuerpo de la petición")
  void elActorNoEsFalsificable() throws Exception {
    String codigo = mvc.perform(post("/api/reservas").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("email", "huesped@hotel.test",
          "llegada", "2030-05-01", "salida", "2030-05-03", "huespedes", 2, "roomId", 1))))
      .andExpect(status().isCreated())
      .andReturn().getResponse().getContentAsString();
    String reserva = JSON.readTree(codigo).get("codigo").asText();

    // El cliente afirma ser otro. El servidor no le cree.
    mvc.perform(post("/api/admin/reservas/" + reserva + "/estado").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("estado", "CONFIRMADA", "actor", "mentira"))))
      .andExpect(status().isOk());

    assertThat(jdbc.queryForObject("SELECT actor FROM reservation_history ORDER BY id DESC LIMIT 1",
      String.class)).isEqualTo("admin@hotel.test");
  }

  @Test
  @DisplayName("el rastro de acciones es consultable desde el panel")
  void elRastroSeConsulta() throws Exception {
    mvc.perform(post("/api/admin/tipos").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("codigo", "DELUXE", "nombre", "Deluxe", "capacidadMax", 4))))
      .andExpect(status().is2xxSuccessful());

    mvc.perform(get("/api/admin/auditoria").with(ADMIN))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].actor").value("admin@hotel.test"))
      .andExpect(jsonPath("$[0].ruta").value("/api/admin/tipos"));
  }

  @Test
  @DisplayName("el rastro no guarda el cuerpo de la petición: ahí van contraseñas y tokens")
  void elRastroNoGuardaSecretos() throws Exception {
    mvc.perform(post("/api/admin/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("email", "nadie@hotel.test", "password", "clave-secreta"))))
      .andExpect(status().is4xxClientError());

    // Se concatena toda la fila porque el rastro no debe tener dónde esconder un cuerpo.
    List<String> todo = jdbc.queryForList(
      "SELECT actor||'|'||metodo||'|'||ruta||'|'||estado FROM admin_actions", String.class);
    assertThat(todo).noneMatch(t -> t.contains("clave-secreta"));
  }
}