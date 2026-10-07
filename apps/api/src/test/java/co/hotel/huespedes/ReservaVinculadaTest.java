package co.hotel.huespedes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
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

  @BeforeEach
  void inventario() {
    if (jdbc.queryForObject("SELECT COUNT(*) FROM rooms", Integer.class) == 0) {
      jdbc.update("INSERT INTO rooms(codigo, estado, nombre) VALUES('101','ACTIVA','Habitación 101')");
      jdbc.update("INSERT INTO rooms(codigo, estado, nombre) VALUES('102','ACTIVA','Habitación 102')");
    }
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