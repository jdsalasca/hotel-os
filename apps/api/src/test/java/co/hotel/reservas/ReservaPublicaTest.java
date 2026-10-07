package co.hotel.reservas;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * El flujo público de reserva por HTTP.
 *
 * La búsqueda solo ofrece habitaciones con precio, pero el alta aceptaba cualquier roomId e
 * incluso ninguno: sin habitación elegida se quedaba con la primera libre, sin mirar capacidad
 * ni tarifas, y la reserva se guardaba con el total en NULL. Vender sin importe.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("alta pública de reservas")
class ReservaPublicaTest {

  private static final Path DB = crearBase();
  private static final ObjectMapper JSON = new ObjectMapper();

  private static Path crearBase() {
    try {
      Path p = Files.createTempFile("hotel-publica-", ".sqlite3");
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
    HotelDePrueba.tarifarTodo(jdbc, LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-30"));
  }

  private Map<String, Object> cuerpo(String email, String llegada, String salida, Integer huespedes,
      Long roomId) {
    Map<String, Object> cuerpo = new HashMap<>();
    cuerpo.put("email", email);
    cuerpo.put("llegada", llegada);
    cuerpo.put("salida", salida);
    cuerpo.put("huespedes", huespedes);
    if (roomId != null) cuerpo.put("roomId", roomId);
    return cuerpo;
  }

  private String reservar(Map<String, Object> cuerpo) throws Exception {
    String respuesta = mvc.perform(post("/api/reservas").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(cuerpo)))
      .andExpect(status().isCreated())
      .andReturn().getResponse().getContentAsString();
    return JSON.readTree(respuesta).get("codigo").asText();
  }

  @Test
  @DisplayName("sin habitación elegida se asigna una vendible, con su precio")
  void sinHabitacionEligeUnaVendible() throws Exception {
    String codigo = reservar(cuerpo("ana@example.com", "2026-11-05", "2026-11-07", 2, null));

    var fila = jdbc.queryForMap("SELECT total_cents FROM reservations WHERE codigo=?", codigo);
    assertNotNull(fila.get("total_cents"), "la reserva asignada tiene que traer precio");
  }

  @Test
  @DisplayName("sin habitación elegida y sin nada a la venta: 409, no una reserva sin precio")
  void sinNadaALaVentaEs409() throws Exception {
    var respuesta = mvc.perform(post("/api/reservas").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(
          cuerpo("nadie@example.com", "2027-01-05", "2027-01-07", 2, null))))
      .andExpect(status().isConflict())
      .andReturn().getResponse().getContentAsString();
    assert JSON.readTree(respuesta).get("error").asText().contains("a la venta");
  }

  @Test
  @DisplayName("sin huéspedes ni habitación: 400 con su motivo, no un 409 de disponibilidad")
  void sinHuespedesEs400() throws Exception {
    var respuesta = mvc.perform(post("/api/reservas").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(
          cuerpo("cero@example.com", "2026-11-05", "2026-11-07", null, null))))
      .andExpect(status().isBadRequest())
      .andReturn().getResponse().getContentAsString();
    assert JSON.readTree(respuesta).get("error").asText().contains("huéspedes");
  }

  @Test
  @DisplayName("una habitación retirada no se puede reservar ni pidiéndola por id")
  void habitacionRetiradaNiPorId() throws Exception {
    jdbc.update("INSERT INTO rooms(codigo,room_type_id,estado) VALUES('105',1,'FUERA_DE_SERVICIO')");
    long retirada = jdbc.queryForObject("SELECT id FROM rooms WHERE codigo='105'", Long.class);

    mvc.perform(post("/api/reservas").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(
          cuerpo("lista@example.com", "2026-11-05", "2026-11-07", 2, retirada))))
      .andExpect(status().isConflict());
  }
}
