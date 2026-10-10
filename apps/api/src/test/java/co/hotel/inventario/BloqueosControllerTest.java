package co.hotel.inventario;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

/**
 * El bloqueo es escritura del panel: lo inválido es 400 con motivo, nunca 500 por un
 * NPE o una FK cruda. Lo rechazado no deja filas.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BloqueosControllerTest {

  private static final Path DB = crearBase();
  private static final RequestPostProcessor ADMIN = user("admin@hotel.test").roles("ADMIN");
  private static final ObjectMapper JSON = new ObjectMapper();

  private static Path crearBase() {
    try {
      Path p = Files.createTempFile("hotel-bloq-", ".sqlite3");
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
  @Autowired InventarioService inventario;
  @Autowired JdbcTemplate jdbc;

  private Map<String, Object> bloqueo(Object roomId, String desde, String hasta) {
    var cuerpo = new HashMap<String, Object>();
    cuerpo.put("roomId", roomId);
    cuerpo.put("desde", desde);
    cuerpo.put("hasta", hasta);
    cuerpo.put("motivo", "Obra");
    return cuerpo;
  }

  @Test
  @DisplayName("bloquear una habitación inexistente es 400, no 500")
  void bloquearInexistenteEs400() throws Exception {
    mvc.perform(post("/api/admin/bloqueos").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(bloqueo(999999, "2027-11-01", "2027-11-05"))))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.error").exists());
  }

  @Test
  @DisplayName("bloquear sin fechas es 400, no 500 por nulo")
  void bloquearSinFechasEs400() throws Exception {
    var sinDesde = bloqueo(null, null, "2027-11-05");
    mvc.perform(post("/api/admin/bloqueos").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(sinDesde)))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.error").exists());

    var sinNada = new HashMap<String, Object>();
    sinNada.put("motivo", "Obra");
    mvc.perform(post("/api/admin/bloqueos").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(sinNada)))
      .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("una reserva que aparece después de abrir el calendario produce 409 y ningún bloqueo")
  void bloqueoContraReservaVigenteEs409SinFila() throws Exception {
    String sufijo = java.util.UUID.randomUUID().toString().substring(0, 8);
    var tipo = inventario.crearTipo("T-" + sufijo, "Tipo de prueba", 2);
    var habitacion = inventario.crearHabitacion("R-" + sufijo, tipo.id(), "Habitación de prueba");
    jdbc.update("INSERT INTO reservations(codigo,email,llegada,salida,huespedes,estado,origen,idempotencia,creado_en)"
      + " VALUES(?,?,?,?,?,?,?,?,datetime('now'))", "RES-" + sufijo, "huesped@test", "2031-05-02",
      "2031-05-04", 1, "CONFIRMADA", "WEB", "idem-" + sufijo);
    long reserva = jdbc.queryForObject("SELECT id FROM reservations WHERE codigo=?",
      Long.class, "RES-" + sufijo);
    jdbc.update("INSERT INTO reservation_items(reservation_id,room_id,desde,hasta) VALUES(?,?,?,?)",
      reserva, habitacion.id(), "2031-05-02", "2031-05-04");

    mvc.perform(post("/api/admin/bloqueos").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(bloqueo(habitacion.id(), "2031-05-03", "2031-05-05"))))
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("reserva")));

    Integer filas = jdbc.queryForObject("SELECT COUNT(*) FROM blocks WHERE room_id=?"
      + " AND desde=? AND hasta=?", Integer.class, habitacion.id(), "2031-05-03", "2031-05-05");
    org.junit.jupiter.api.Assertions.assertEquals(0, filas);
  }
}
