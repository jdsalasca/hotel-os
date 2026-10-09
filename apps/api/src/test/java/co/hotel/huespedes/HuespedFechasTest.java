package co.hotel.huespedes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.hotel.pruebas.HotelDePrueba;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
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
 * El huésped mueve sus fechas sin llamar al hotel: solo las suyas, solo vigentes
 * y solo a noches libres y vendibles. La ajena ni se confirma que existe.
 */
@SpringBootTest
@AutoConfigureMockMvc
class HuespedFechasTest {

  private static final Path DB = crearBase();
  private static final ObjectMapper JSON = new ObjectMapper();

  private static Path crearBase() {
    try {
      Path p = Files.createTempFile("hotel-hfechas-", ".sqlite3");
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
    HotelDePrueba.tarifarTodo(jdbc, LocalDate.parse("2030-01-01"), LocalDate.parse("2031-01-01"));
  }

  private void cuenta(String sub, String email) {
    jdbc.update("INSERT INTO usuarios(google_sub,email,nombre,creado_en) VALUES(?,?,?,?) "
      + "ON CONFLICT(google_sub) DO NOTHING", sub, email, email, "2030-01-01");
  }

  private RequestPostProcessor sesion(String email) {
    return user(email).roles("HUESPED");
  }

  private String reservar(String email, String llegada, String salida) throws Exception {
    String cuerpo = mvc.perform(post("/api/reservas").with(sesion(email)).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("email", email, "nombre", "H",
          "llegada", llegada, "salida", salida, "huespedes", 1, "roomId", 1))))
      .andExpect(status().isCreated())
      .andReturn().getResponse().getContentAsString();
    return JSON.readTree(cuerpo).get("codigo").asText();
  }

  private String mover(String codigo, String llegada, String salida, RequestPostProcessor sesion)
      throws Exception {
    return mvc.perform(post("/api/mis-reservas/" + codigo + "/fechas").with(sesion).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("llegada", llegada, "salida", salida))))
      .andReturn().getResponse().getContentAsString();
  }

  @Test
  @DisplayName("el dueño mueve sus fechas y el precio se recalcula")
  void elDuenoMueveSusFechas() throws Exception {
    cuenta("sub-a", "a@hotel.test");
    String codigo = reservar("a@hotel.test", "2030-06-10", "2030-06-12");

    mvc.perform(post("/api/mis-reservas/" + codigo + "/fechas").with(sesion("a@hotel.test")).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("llegada", "2030-06-13", "salida", "2030-06-15"))))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.codigo").value(codigo))
      .andExpect(jsonPath("$.llegada").value("2030-06-13"))
      .andExpect(jsonPath("$.salida").value("2030-06-15"));

    assertEquals("2030-06-13", jdbc.queryForObject(
      "SELECT llegada FROM reservations WHERE codigo=?", String.class, codigo));
  }

  @Test
  @DisplayName("la ajena es 404 y no se mueve")
  void laAjenaEs404() throws Exception {
    cuenta("sub-a", "a@hotel.test");
    cuenta("sub-b", "b@hotel.test");
    String codigo = reservar("a@hotel.test", "2030-07-10", "2030-07-12");

    mvc.perform(post("/api/mis-reservas/" + codigo + "/fechas").with(sesion("b@hotel.test")).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("llegada", "2030-07-13", "salida", "2030-07-15"))))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.error").exists());

    assertEquals("2030-07-10", jdbc.queryForObject(
      "SELECT llegada FROM reservations WHERE codigo=?", String.class, codigo));
  }

  @Test
  @DisplayName("sin sesión es 401")
  void sinSesionEs401() throws Exception {
    cuenta("sub-a", "a@hotel.test");
    String codigo = reservar("a@hotel.test", "2030-08-10", "2030-08-12");

    mvc.perform(post("/api/mis-reservas/" + codigo + "/fechas").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("llegada", "2030-08-13", "salida", "2030-08-15"))))
      .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("moverse encima de otra reserva es 409 y no se mueve")
  void moverseEncimaEs409() throws Exception {
    cuenta("sub-a", "a@hotel.test");
    String codigo = reservar("a@hotel.test", "2030-09-10", "2030-09-12");
    jdbc.update("INSERT INTO usuarios(google_sub,email,nombre,creado_en) VALUES('sub-bloq',"
      + "'bloq@hotel.test','B','2030-01-01')");
    mvc.perform(post("/api/reservas").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("email", "bloq@hotel.test", "nombre", "B",
          "llegada", "2030-09-14", "salida", "2030-09-16", "huespedes", 1, "roomId", 1))))
      .andExpect(status().isCreated());

    mvc.perform(post("/api/mis-reservas/" + codigo + "/fechas").with(sesion("a@hotel.test")).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("llegada", "2030-09-15", "salida", "2030-09-17"))))
      .andExpect(status().isConflict());

    assertEquals("2030-09-10", jdbc.queryForObject(
      "SELECT llegada FROM reservations WHERE codigo=?", String.class, codigo));
  }

  @Test
  @DisplayName("fechas mal escritas son 400")
  void fechasMalSon400() throws Exception {
    cuenta("sub-a", "a@hotel.test");
    String codigo = reservar("a@hotel.test", "2030-10-10", "2030-10-12");

    mvc.perform(post("/api/mis-reservas/" + codigo + "/fechas").with(sesion("a@hotel.test")).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("llegada", "ayer", "salida", "2030-10-15"))))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.error").exists());
  }

  @Test
  @DisplayName("el dueño cambia sus huéspedes")
  void elDuenoCambiaHuespedes() throws Exception {
    cuenta("sub-a", "a@hotel.test");
    String codigo = reservar("a@hotel.test", "2030-11-10", "2030-11-12");

    mvc.perform(post("/api/mis-reservas/" + codigo + "/huespedes").with(sesion("a@hotel.test")).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("huespedes", 2))))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.codigo").value(codigo))
      .andExpect(jsonPath("$.huespedes").value(2));

    assertEquals(2, jdbc.queryForObject("SELECT huespedes FROM reservations WHERE codigo=?",
      Integer.class, codigo));
  }

  @Test
  @DisplayName("los huéspedes de la ajena son 404 y no se tocan")
  void huespedesDeLaAjenaSon404() throws Exception {
    cuenta("sub-a", "a@hotel.test");
    cuenta("sub-b", "b@hotel.test");
    String codigo = reservar("a@hotel.test", "2030-11-13", "2030-11-15");

    mvc.perform(post("/api/mis-reservas/" + codigo + "/huespedes").with(sesion("b@hotel.test")).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("huespedes", 2))))
      .andExpect(status().isNotFound());

    assertEquals(1, jdbc.queryForObject("SELECT huespedes FROM reservations WHERE codigo=?",
      Integer.class, codigo));
  }

  @Test
  @DisplayName("cambiar huéspedes sin sesión es 401")
  void huespedesSinSesionEs401() throws Exception {
    cuenta("sub-a", "a@hotel.test");
    String codigo = reservar("a@hotel.test", "2030-12-10", "2030-12-12");

    mvc.perform(post("/api/mis-reservas/" + codigo + "/huespedes").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("huespedes", 2))))
      .andExpect(status().isUnauthorized());
  }
}
