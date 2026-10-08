package co.hotel.huespedes;

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
 * El huésped con sesión ve el comprobante completo de SUS reservas sin pasar el correo por
 * la URL: la sesión ya dice de quién son. Las ajenas no existen para él (404, no 403 que
 * confirma que existen).
 */
@SpringBootTest
@AutoConfigureMockMvc
class HuespedComprobanteTest {

  private static final Path DB = crearBase();
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final AtomicInteger TRAMO = new AtomicInteger(2040);

  private static Path crearBase() {
    try {
      Path p = Files.createTempFile("hotel-comp-", ".sqlite3");
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
    if (jdbc.queryForObject("SELECT COUNT(*) FROM usuarios WHERE email='comp@hotel.test'",
        Integer.class) == 0) {
      jdbc.update("INSERT INTO usuarios(google_sub,email,nombre,creado_en) VALUES('sub-comp',"
        + "'comp@hotel.test','Comprobante','2030-01-01')");
      jdbc.update("INSERT INTO usuarios(google_sub,email,nombre,creado_en) VALUES('sub-ajeno',"
        + "'ajeno@hotel.test','Ajeno','2030-01-01')");
    }
  }

  private String reservar() throws Exception {
    int t = TRAMO.incrementAndGet();
    var huesped = user("comp@hotel.test").roles("HUESPED");
    String cuerpo = mvc.perform(post("/api/reservas").with(huesped).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("email", "comp@hotel.test", "nombre", "Comprobante",
          "llegada", "2030-%02d-10".formatted(1 + (t % 12)),
          "salida", "2030-%02d-12".formatted(1 + (t % 12)), "huespedes", 1, "roomId", 1))))
      .andExpect(status().isCreated())
      .andReturn().getResponse().getContentAsString();
    return JSON.readTree(cuerpo).get("codigo").asText();
  }

  @Test
  @DisplayName("el dueño ve su comprobante por sesión; el ajeno y el anónimo no")
  void comprobantePropioPorSesion() throws Exception {
    String codigo = reservar();
    var dueno = user("comp@hotel.test").roles("HUESPED");
    var ajeno = user("ajeno@hotel.test").roles("HUESPED");

    mvc.perform(get("/api/mis-reservas/" + codigo + "/comprobante").with(dueno))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.reserva.codigo").value(codigo))
      .andExpect(jsonPath("$.hotel").exists())
      .andExpect(jsonPath("$.historial").isArray());

    mvc.perform(get("/api/mis-reservas/" + codigo + "/comprobante").with(ajeno))
      .andExpect(status().isNotFound());

    mvc.perform(get("/api/mis-reservas/" + codigo + "/comprobante"))
      .andExpect(status().isUnauthorized());
  }
}
