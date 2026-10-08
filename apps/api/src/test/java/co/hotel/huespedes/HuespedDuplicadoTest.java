package co.hotel.huespedes;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * Un correo en dos cuentas (identidad de panel + login de Google con el mismo correo)
 * no puede tumbar la sesión con un 500: la resolución es determinista a la más
 * antigua en las tres superficies que resuelven por correo.
 */
@SpringBootTest
@AutoConfigureMockMvc
class HuespedDuplicadoTest {

  private static final Path DB = crearBase();
  private static final ObjectMapper JSON = new ObjectMapper();

  private static Path crearBase() {
    try {
      Path p = Files.createTempFile("hotel-dup-", ".sqlite3");
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
    jdbc.update("INSERT INTO usuarios(google_sub,email,nombre,creado_en) VALUES"
      + "('panel:dup@hotel.test','dup@hotel.test','Panel','2030-01-01'),"
      + "('sub-dup','dup@hotel.test','Dup','2030-01-02') "
      + "ON CONFLICT(google_sub) DO NOTHING");
  }

  private RequestPostProcessor sesionDup() {
    return user("dup@hotel.test").roles("HUESPED");
  }

  private long idAntigua() {
    return jdbc.queryForObject("SELECT MIN(id) FROM usuarios WHERE email='dup@hotel.test'",
      Long.class);
  }

  @Test
  @DisplayName("/api/yo con correo duplicado responde 200, no 500")
  void yoConDuplicadoEs200() throws Exception {
    mvc.perform(get("/api/yo").with(sesionDup()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.email").value("dup@hotel.test"));
  }

  @Test
  @DisplayName("/api/mis-reservas con correo duplicado responde 200, no 500")
  void misReservasConDuplicadoEs200() throws Exception {
    mvc.perform(get("/api/mis-reservas").with(sesionDup()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.reservas").isArray());
  }

  @Test
  @DisplayName("reservar con correo duplicado crea 201 enganchada a la más antigua")
  void reservarConDuplicadoEnganchaLaAntigua() throws Exception {
    String cuerpo = mvc.perform(post("/api/reservas").with(sesionDup()).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("email", "dup@hotel.test", "nombre", "Dup",
          "llegada", "2030-06-10", "salida", "2030-06-12", "huespedes", 1, "roomId", 1))))
      .andExpect(status().isCreated())
      .andReturn().getResponse().getContentAsString();
    String codigo = JSON.readTree(cuerpo).get("codigo").asText();

    assertEquals(idAntigua(), jdbc.queryForObject(
      "SELECT usuario_id FROM reservations WHERE codigo=?", Long.class, codigo));
  }
}
