package co.hotel.reservas;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.hotel.pruebas.HotelDePrueba;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * El personal también se hospeda: con sesión ADMIN y su propio correo, la reserva queda
 * colgando de su cuenta (creada al momento) y la ve en "Mis reservas" sin perder el panel.
 * Si reserva para otro correo (recepción), no se engancha a nadie.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminReservaPropiaTest {

  private static final Path DB = crearBase();
  private static final ObjectMapper JSON = new ObjectMapper();

  private static Path crearBase() {
    try {
      Path p = Files.createTempFile("hotel-admprop-", ".sqlite3");
      Files.delete(p);
      return p;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @DynamicPropertySource
  static void propiedades(DynamicPropertyRegistry reg) {
    reg.add("hotel.jdbc-path", () -> DB.toAbsolutePath().toString());
    reg.add("hotel.limites.reservas", () -> 200);
  }

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void inventario() {
    if (jdbc.queryForObject("SELECT COUNT(*) FROM rooms", Integer.class) == 0) {
      jdbc.update("INSERT INTO rooms(codigo, estado, nombre) VALUES('101','ACTIVA','Habitación 101')");
    }
    HotelDePrueba.tarifarTodo(jdbc, LocalDate.parse("2026-12-01"), LocalDate.parse("2026-12-31"));
  }

  private Map<String, Object> cuerpo(String email, String llegada, String salida) {
    Map<String, Object> cuerpo = new HashMap<>();
    cuerpo.put("email", email);
    cuerpo.put("nombre", "Personal");
    cuerpo.put("llegada", llegada);
    cuerpo.put("salida", salida);
    cuerpo.put("huespedes", 1);
    return cuerpo;
  }

  private String reservarComo(Map<String, Object> cuerpo, String emailSesion) throws Exception {
    var req = post("/api/reservas").with(csrf())
      .contentType(MediaType.APPLICATION_JSON)
      .content(JSON.writeValueAsString(cuerpo));
    if (emailSesion != null) req = req.with(user(emailSesion).roles("ADMIN"));
    String respuesta = mvc.perform(req)
      .andExpect(status().isCreated())
      .andReturn().getResponse().getContentAsString();
    return JSON.readTree(respuesta).get("codigo").asText();
  }

  @Test
  @DisplayName("el admin que reserva para sí mismo la ve en Mis reservas")
  void adminReservaPropiaSeVe() throws Exception {
    var admin = user("admin@hotel.test").roles("ADMIN");
    String codigo = reservarComo(cuerpo("admin@hotel.test", "2026-12-10", "2026-12-12"),
      "admin@hotel.test");

    mvc.perform(get("/api/mis-reservas").with(admin))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.reservas[?(@.codigo=='" + codigo + "')]").exists());

    String sub = jdbc.queryForObject("SELECT google_sub FROM usuarios WHERE email='admin@hotel.test'",
      String.class);
    assertEquals("panel:admin@hotel.test", sub);
  }

  @Test
  @DisplayName("el admin que reserva para otro no se la queda")
  void adminReservaAjenaNoSeEngancha() throws Exception {
    var admin = user("recepcion@hotel.test").roles("ADMIN");
    String codigo = reservarComo(cuerpo("huesped-ajeno@hotel.test", "2026-12-15", "2026-12-17"),
      "recepcion@hotel.test");

    assertNull(jdbc.queryForObject("SELECT usuario_id FROM reservations WHERE codigo=?",
      Long.class, codigo));

    mvc.perform(get("/api/mis-reservas").with(admin))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.reservas[?(@.codigo=='" + codigo + "')]").doesNotExist());
  }

  @Test
  @DisplayName("ni con identidad previa la reserva ajena se engancha")
  void adminConIdentidadReservaAjenaNoSeEngancha() throws Exception {
    // La primera reserva propia le crea la identidad de panel; la segunda, para otro
    // correo con la misma sesión, igual queda anónima: tener fila no es probar propiedad.
    reservarComo(cuerpo("admin@hotel.test", "2026-12-03", "2026-12-05"), "admin@hotel.test");
    String ajena = reservarComo(cuerpo("otro@hotel.test", "2026-12-06", "2026-12-08"),
      "admin@hotel.test");

    assertNull(jdbc.queryForObject("SELECT usuario_id FROM reservations WHERE codigo=?",
      Long.class, ajena));
  }

  @Test
  @DisplayName("sin sesión nada se engancha")
  void anonimaNoSeEngancha() throws Exception {
    String codigo = reservarComo(cuerpo("anon@hotel.test", "2026-12-20", "2026-12-22"), null);
    assertNull(jdbc.queryForObject("SELECT usuario_id FROM reservations WHERE codigo=?",
      Long.class, codigo));
  }
}
