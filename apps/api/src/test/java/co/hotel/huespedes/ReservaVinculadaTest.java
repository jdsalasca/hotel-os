package co.hotel.huespedes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.hotel.pruebas.HotelDePrueba;
import co.hotel.reservas.CrearReserva;
import co.hotel.reservas.Origen;
import co.hotel.reservas.ReservaService;
import java.nio.file.Files;
import java.time.LocalDate;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
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
import tools.jackson.databind.ObjectMapper;

/**
 * Reservar con la sesión abierta tiene que dejar la reserva colgando de la cuenta, que es lo
 * único que hace útil "Mis reservas". Sin ese enganche la página siempre saldría vacía.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReservaVinculadaTest {

  private static final Path DB = crearBase();
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final AtomicInteger TRAMO = new AtomicInteger(2030);

  private static Path crearBase() {
    try {
      Path p = Files.createTempFile("hotel-vinc-", ".sqlite3");
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
  @Autowired ReservaService svc;

  @BeforeEach
  void inventario() {
    if (jdbc.queryForObject("SELECT COUNT(*) FROM rooms", Integer.class) == 0) {
      jdbc.update("INSERT INTO rooms(codigo, estado, nombre) VALUES('101','ACTIVA','Habitación 101')");
      jdbc.update("INSERT INTO rooms(codigo, estado, nombre) VALUES('102','ACTIVA','Habitación 102')");
    }
    // La reserva pública exige precio acordado; esta clase mide el enganche a la cuenta.
    HotelDePrueba.tarifarTodo(jdbc, LocalDate.parse("2030-01-01"), LocalDate.parse("2031-01-01"));
  }

  private Map<String, Object> siguiente() {
    int t = TRAMO.incrementAndGet();
    return Map.of("email", "huesped@hotel.test", "nombre", "Huésped",
      "llegada", "2030-%02d-10".formatted(1 + (t % 12)), "salida", "2030-%02d-12".formatted(1 + (t % 12)),
      "huespedes", 1, "roomId", 1);
  }

  @Test
  @DisplayName("reservar con sesión deja la reserva en la cuenta y aparece en Mis reservas")
  void reservarConSesionEnganchaLaReserva() throws Exception {
    jdbc.update("INSERT INTO usuarios(google_sub,email,nombre,creado_en) VALUES('sub-vinc',"
      + "'huesped@hotel.test','Huésped','2030-01-01')");
    var huesped = user("huesped@hotel.test").roles("HUESPED");

    String cuerpo = mvc.perform(post("/api/reservas").with(huesped).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(siguiente())))
      .andExpect(status().isCreated())
      .andReturn().getResponse().getContentAsString();
    String codigo = JSON.readTree(cuerpo).get("codigo").asText();

    Long usuarioId = jdbc.queryForObject("SELECT usuario_id FROM reservations WHERE codigo=?",
      Long.class, codigo);
    assertNotNull(usuarioId, "reservar con sesión tiene que enganchar la reserva a la cuenta");

    mvc.perform(get("/api/mis-reservas").with(huesped))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.reservas[0].codigo").value(codigo));
  }

  @Test
  @DisplayName("reservar sin sesión deja la reserva sin usuario, no la rompe")
  void reservarSinSesionNoEngancha() throws Exception {
    String cuerpo = mvc.perform(post("/api/reservas").with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(siguiente())))
      .andExpect(status().isCreated())
      .andReturn().getResponse().getContentAsString();
    String codigo = JSON.readTree(cuerpo).get("codigo").asText();

    Long usuarioId = jdbc.queryForObject("SELECT usuario_id FROM reservations WHERE codigo=?",
      Long.class, codigo);
    assertNull(usuarioId, "sin sesión no hay cuenta: la reserva sigue siendo anónima");

    // Y se sigue pudiendo consultar por código y correo, que es la vía de siempre.
    mvc.perform(get("/api/reservas/" + codigo).param("email", "huesped@hotel.test"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.codigo").value(codigo));
  }

  @Test
  @DisplayName("con sesión, reservar para otro correo no se engancha a la cuenta")
  void reservarParaOtroCorreoNoSeEngancha() throws Exception {
    // La sesión es de huesped@hotel.test pero la reserva es para otro@: con el
    // correo distinto no hay prueba de propiedad y la reserva queda anónima.
    jdbc.update("INSERT INTO usuarios(google_sub,email,nombre,creado_en) VALUES('sub-otro',"
      + "'huesped@hotel.test','Huésped','2030-01-01')");
    var huesped = user("huesped@hotel.test").roles("HUESPED");
    Map<String, Object> cuerpo = new java.util.HashMap<>(siguiente());
    cuerpo.put("email", "otro@hotel.test");
    cuerpo.put("llegada", "2030-03-10");
    cuerpo.put("salida", "2030-03-12");
    cuerpo.put("roomId", 2);

    String respuesta = mvc.perform(post("/api/reservas").with(huesped).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(cuerpo)))
      .andExpect(status().isCreated())
      .andReturn().getResponse().getContentAsString();
    String codigo = JSON.readTree(respuesta).get("codigo").asText();

    assertNull(jdbc.queryForObject("SELECT usuario_id FROM reservations WHERE codigo=?",
      Long.class, codigo), "para otro correo no se engancha a la sesión abierta");

    mvc.perform(get("/api/mis-reservas").with(huesped))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.reservas[?(@.codigo=='" + codigo + "')]").doesNotExist());
  }

  @Test
  @DisplayName("con el correo duplicado no hay 500: se engancha a la cuenta más antigua")
  void correoDuplicadoNoEs500() throws Exception {
    // Dos cuentas con el mismo correo (panel + Google): la petición con ese correo sale
    // 201 contra la más antigua, en vez de reventar resolviendo el id.
    jdbc.update("INSERT INTO usuarios(google_sub,email,nombre,creado_en) VALUES('panel:dup@hotel.test',"
      + "'dup@hotel.test','Panel','2030-01-01')");
    jdbc.update("INSERT INTO usuarios(google_sub,email,nombre,creado_en) VALUES('sub-dup',"
      + "'dup@hotel.test','Dup','2030-01-02')");
    Long antigua = jdbc.queryForObject("SELECT MIN(id) FROM usuarios WHERE email='dup@hotel.test'",
      Long.class);
    Map<String, Object> cuerpo = new java.util.HashMap<>(siguiente());
    cuerpo.put("email", "dup@hotel.test");
    cuerpo.put("llegada", "2030-05-10");
    cuerpo.put("salida", "2030-05-12");
    cuerpo.put("roomId", 2);

    String respuesta = mvc.perform(post("/api/reservas").with(user("dup@hotel.test").roles("HUESPED"))
        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(cuerpo)))
      .andExpect(status().isCreated())
      .andReturn().getResponse().getContentAsString();
    String codigo = JSON.readTree(respuesta).get("codigo").asText();

    assertEquals(antigua, jdbc.queryForObject("SELECT usuario_id FROM reservations WHERE codigo=?",
      Long.class, codigo), "desempate determinista por antigüedad");
  }

  @Test
  @DisplayName("si el vínculo falla, la reserva tampoco se guarda")
  void vinculoFallidoNoDejaHuerfana() {
    // Un usuario_id inexistente viola la FK al enganchar: el alta entera revierte en la
    // misma unidad, en vez de dejar la reserva huérfana como con las dos escrituras.
    int antes = jdbc.queryForObject("SELECT COUNT(*) FROM reservations", Integer.class);
    assertThrows(org.springframework.dao.DataAccessException.class, () ->
      svc.crear(new CrearReserva("huerfana@hotel.test", "H",
        LocalDate.parse("2030-04-10"), LocalDate.parse("2030-04-12"), 1, Origen.WEB,
        java.util.UUID.randomUUID().toString(), 2), null, null, null, 999999L));
    assertEquals(antes, jdbc.queryForObject("SELECT COUNT(*) FROM reservations", Integer.class),
      "el fallo del vínculo revierte el alta");
  }

  @Test
  @DisplayName("el enganche no pisa una reserva que ya tenía dueño")
  void elEngancheNoPisaDueno() throws Exception {
    jdbc.update("INSERT INTO usuarios(google_sub,email,nombre,creado_en) VALUES('sub-a',"
      + "'a@hotel.test','A','2030-01-01')");
    long deA = jdbc.queryForObject("SELECT id FROM usuarios WHERE google_sub='sub-a'", Long.class);
    jdbc.update("INSERT INTO usuarios(google_sub,email,nombre,creado_en) VALUES('sub-b',"
      + "'b@hotel.test','B','2030-01-01')");
    long deB = jdbc.queryForObject("SELECT id FROM usuarios WHERE google_sub='sub-b'", Long.class);

    // Reserva ya de A; entra B con sesión y reintenta con la misma clave de idempotencia.
    String cuerpo = mvc.perform(post("/api/reservas").with(user("a@hotel.test").roles("HUESPED"))
        .with(csrf()).header("Idempotency-Key", "compartida-de-prueba")
        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(siguiente())))
      .andExpect(status().isCreated())
      .andReturn().getResponse().getContentAsString();
    String codigo = JSON.readTree(cuerpo).get("codigo").asText();
    jdbc.update("UPDATE reservations SET usuario_id=? WHERE codigo=?", deA, codigo);

    mvc.perform(post("/api/reservas").with(user("b@hotel.test").roles("HUESPED"))
        .with(csrf()).header("Idempotency-Key", "otra-clave")
        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(siguiente())))
      .andExpect(status().isCreated());

    Integer dueno = jdbc.queryForObject("SELECT usuario_id FROM reservations WHERE codigo=?",
      Integer.class, codigo);
    assertEquals((int) deA, dueno, "una reserva con dueño no cambia de dueño");
  }
}