package co.hotel.reservas;

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
 * El parte de la recepción: quién llega y quién se va un día dado.
 *
 * Hoy eso exige recorrer la lista completa de reservas o preguntarle a la base. El parte sale de
 * los mismos datos, sin SQL: llegadas y salidas del día con habitación y huéspedes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("parte diario de llegadas y salidas")
class OcupacionDiaTest {

  private static final Path DB = crearBase();
  private static final RequestPostProcessor ADMIN = user("admin@hotel.test").roles("ADMIN");
  private static final ObjectMapper JSON = new ObjectMapper();

  private static Path crearBase() {
    try {
      Path p = Files.createTempFile("hotel-parte-", ".sqlite3");
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
      jdbc.update("INSERT INTO rooms(codigo, estado, nombre) VALUES('102','ACTIVA','Habitación 102')");
    }
    HotelDePrueba.tarifarTodo(jdbc, LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-10"));
  }

  private String reservar(String email, String llegada, String salida, long roomId) throws Exception {
    String cuerpo = mvc.perform(post("/api/reservas").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("email", email, "nombre", email,
          "llegada", llegada, "salida", salida, "huespedes", 2, "roomId", roomId))))
      .andExpect(status().isCreated())
      .andReturn().getResponse().getContentAsString();
    return JSON.readTree(cuerpo).get("codigo").asText();
  }

  @Test
  @DisplayName("el parte del día trae quién llega y quién se va, con habitación")
  void parteDelDia() throws Exception {
    String llega = reservar("llega@example.com", "2026-11-03", "2026-11-05", 1);
    reservar("sigue@example.com", "2026-11-01", "2026-11-06", 2);
    String seVa = reservar("sale@example.com", "2026-11-01", "2026-11-03", 1);

    mvc.perform(get("/api/admin/ocupacion/dia").with(ADMIN).param("fecha", "2026-11-03"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.fecha").value("2026-11-03"))
      .andExpect(jsonPath("$.llegadas.length()").value(1))
      .andExpect(jsonPath("$.llegadas[0].codigo").value(llega))
      .andExpect(jsonPath("$.llegadas[0].habitacion").value("101"))
      .andExpect(jsonPath("$.llegadas[0].huespedes").value(2))
      .andExpect(jsonPath("$.salidas.length()").value(1))
      .andExpect(jsonPath("$.salidas[0].codigo").value(seVa));
  }

  @Test
  @DisplayName("un día sin movimiento trae listas vacías, no un 404")
  void diaSinMovimiento() throws Exception {
    mvc.perform(get("/api/admin/ocupacion/dia").with(ADMIN).param("fecha", "2026-11-08"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.llegadas").isEmpty())
      .andExpect(jsonPath("$.salidas").isEmpty());
  }

  @Test
  @DisplayName("la fecha mal formada es un 400")
  void fechaMalformadaEs400() throws Exception {
    mvc.perform(get("/api/admin/ocupacion/dia").with(ADMIN).param("fecha", "03-11-2026"))
      .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("sin sesión no hay parte")
  void sinSesionEs401() throws Exception {
    mvc.perform(get("/api/admin/ocupacion/dia").param("fecha", "2026-11-03"))
      .andExpect(status().isUnauthorized());
  }
}
