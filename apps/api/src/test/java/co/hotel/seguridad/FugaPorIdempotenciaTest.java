package co.hotel.seguridad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
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
 * La reserva pública devuelve nombre, correo, fechas y total. Nadie debe poder leer la de otro
 * huesped, y la clave de idempotencia es el camino por donde se colaba: se buscaría en la base sin
 * mirar de quién era.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FugaPorIdempotenciaTest {

  private static final Path DB = crearBase();
  private static final ObjectMapper JSON = new ObjectMapper();

  private static Path crearBase() {
    try {
      Path p = Files.createTempFile("hotel-idem-", ".sqlite3");
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
  void habitaciones() {
    if (jdbc.queryForObject("SELECT COUNT(*) FROM rooms", Integer.class) == 0) {
      jdbc.update("INSERT INTO rooms(codigo, estado, nombre) VALUES('101','ACTIVA','Habitación 101')");
      jdbc.update("INSERT INTO rooms(codigo, estado, nombre) VALUES('102','ACTIVA','Habitación 102')");
    }
  }

  private String reserva(Map<String, ?> cuerpo, String clave) throws Exception {
    return mvc.perform(post("/api/reservas").with(csrf())
        .header("Idempotency-Key", clave)
        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(cuerpo)))
      .andExpect(status().isCreated())
      .andReturn().getResponse().getContentAsString();
  }

  private Map<String, ?> cuerpo(String email, String nombre, long habitacion, int mes, int dia) {
    return Map.of("email", email, "nombre", nombre, "llegada", "2035-%02d-%02d".formatted(mes, dia),
      "salida", "2035-%02d-%02d".formatted(mes, dia + 1), "huespedes", 1, "roomId", habitacion);
  }

  @Test
  @DisplayName("quien reutiliza la clave de otro no recibe sus datos personales")
  void laClaveDeIdempotenciaNoFiltraDatosAjenos() throws Exception {
    String clave = UUID.randomUUID().toString();
    int antes = jdbc.queryForObject("SELECT COUNT(*) FROM reservations", Integer.class);
    String deAna = reserva(cuerpo("ana@example.com", "Ana Perez", 1, 3, 10), clave);
    assertEquals("Ana Perez", JSON.readTree(deAna).get("nombre").asText());

    String deBruno = reserva(cuerpo("bruno@example.com", "Bruno Diaz", 2, 3, 10), clave);

    String cuerpo = JSON.readTree(deBruno).toString();
    assertFalse(cuerpo.contains("Ana Perez"), "la respuesta de Bruno no puede traer el nombre de Ana: " + cuerpo);
    assertFalse(cuerpo.contains("ana@example.com"), "ni su correo: " + cuerpo);
    assertEquals("Bruno Diaz", JSON.readTree(deBruno).get("nombre").asText());

    // Dos reservas distintas de verdad, no la misma mostrada dos veces. Se cuentan solo las de las
    // dos pruebas, porque la base es compartida dentro de la clase.
    assertEquals(antes + 2, jdbc.queryForObject("SELECT COUNT(*) FROM reservations", Integer.class),
      "Ana y Bruno deben tener una reserva cada uno");
  }

  @Test
  @DisplayName("un reintento del mismo huésped sigue devolviendo su misma reserva")
  void elReintentoDelMismoHuespedNoDuplica() throws Exception {
    String clave = UUID.randomUUID().toString();
    int antes = jdbc.queryForObject("SELECT COUNT(*) FROM reservations", Integer.class);

    String primera = reserva(cuerpo("ana@example.com", "Ana Perez", 1, 4, 10), clave);
    String segunda = reserva(cuerpo("ana@example.com", "Ana Perez", 1, 4, 10), clave);

    assertEquals(JSON.readTree(primera).get("codigo").asText(), JSON.readTree(segunda).get("codigo").asText());
    assertEquals(antes + 1, jdbc.queryForObject("SELECT COUNT(*) FROM reservations", Integer.class),
      "el reintento no puede crear una segunda reserva");
  }
}