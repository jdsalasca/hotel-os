package co.hotel.reservas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
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
 * El panel administrativo: lo que el hotel necesita para ver y confirmar una reserva que llegó por
 * la web pública. Sin esto, una reserva registrada no es visible en ninguna parte.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminReservasControllerTest {

  private static final Path DB = crearBase();
  private static final RequestPostProcessor ADMIN = user("admin@hotel.test").roles("ADMIN");
  private static final ObjectMapper JSON = new ObjectMapper();

  private static Path crearBase() {
    try {
      Path p = Files.createTempFile("hotel-admin-", ".sqlite3");
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
  }

  private String crearReserva(String email, String llegada, String salida) throws Exception {
    String cuerpo = mvc.perform(post("/api/reservas").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(json(Map.of("email", email, "llegada", llegada, "salida", salida,
          "huespedes", 2, "roomId", 1))))
      .andExpect(status().isCreated())
      .andReturn().getResponse().getContentAsString();
    return JSON.readTree(cuerpo).get("codigo").asText();
  }

  private String cambiarEstado(String codigo, String estado) throws Exception {
    return mvc.perform(post("/api/admin/reservas/" + codigo + "/estado").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(json(Map.of("estado", estado, "actor", "admin@hotel.test"))))
      .andReturn().getResponse().getContentAsString();
  }

  private static String json(Map<String, ?> datos) throws Exception {
    return JSON.writeValueAsString(datos);
  }

  @Test
  @DisplayName("la reserva creada en la web aparece listada en el panel")
  void laReservaWebApareceEnElPanel() throws Exception {
    String codigo = crearReserva("ana@example.com", "2026-11-01", "2026-11-04");

    mvc.perform(get("/api/admin/reservas").with(ADMIN))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].codigo").value(codigo))
      .andExpect(jsonPath("$[0].origen").value("WEB"))
      .andExpect(jsonPath("$[0].estado").value("PENDIENTE"));
  }

  @Test
  @DisplayName("el detalle incluye el historial de cambios de estado")
  void elDetalleIncluyeHistorial() throws Exception {
    String codigo = crearReserva("bruno@example.com", "2026-11-10", "2026-11-12");

    mvc.perform(get("/api/admin/reservas/" + codigo).with(ADMIN))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.reserva.estado").value("PENDIENTE"))
      .andExpect(jsonPath("$.historial.length()").value(1));

    cambiarEstado(codigo, "CONFIRMADA");

    mvc.perform(get("/api/admin/reservas/" + codigo).with(ADMIN))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.reserva.estado").value("CONFIRMADA"))
      .andExpect(jsonPath("$.historial.length()").value(2));
  }

  @Test
  @DisplayName("cancelar una reserva libera el inventario para esas fechas")
  void cancelarLiberaInventario() throws Exception {
    String codigo = crearReserva("carla@example.com", "2026-11-20", "2026-11-22");
    assertEquals("CANCELADA", JSON.readTree(cambiarEstado(codigo, "CANCELADA")).get("estado").asText());

    mvc.perform(post("/api/reservas").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(json(Map.of("email", "nuevo@example.com", "llegada", "2026-11-20",
          "salida", "2026-11-22", "huespedes", 2, "roomId", 1))))
      .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("un estado desconocido se rechaza sin tocar la reserva")
  void estadoDesconocidoSeRechaza() throws Exception {
    String codigo = crearReserva("dani@example.com", "2026-11-25", "2026-11-27");

    mvc.perform(post("/api/admin/reservas/" + codigo + "/estado").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(json(Map.of("estado", "INVENTADA", "actor", "x"))))
      .andExpect(status().isBadRequest());

    mvc.perform(get("/api/admin/reservas/" + codigo).with(ADMIN))
      .andExpect(jsonPath("$.reserva.estado").value("PENDIENTE"));
  }

  @Test
  @DisplayName("sin sesión, el panel no responde datos")
  void elPanelExigeSesion() throws Exception {
    mvc.perform(get("/api/admin/reservas")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("una reserva pública con fecha mal escrita devuelve 400, no un error 500")
  void reservaPublicaFechaMalaEs400() throws Exception {
    mvc.perform(post("/api/reservas").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(json(Map.of("email", "mala@example.com", "llegada", "ayer",
          "salida", "2026-11-22", "huespedes", 2))))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.error").exists());
  }
}