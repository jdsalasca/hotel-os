package co.hotel.pagos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
 * Libro de abonos de una reserva: lo acordado menos lo cobrado es lo pendiente. Sin pasarela de
 * pagos (bloqueada: el hotel no ha elegido proveedor), el registro manual con trazabilidad es lo
 * que permite cobrar en recepción sin papelitos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("abonos y saldo de una reserva")
class PagosAdminTest {

  private static final Path DB = crearBase();
  private static final RequestPostProcessor ADMIN = user("admin@hotel.test").roles("ADMIN");
  private static final ObjectMapper JSON = new ObjectMapper();

  private static Path crearBase() {
    try {
      Path p = Files.createTempFile("hotel-pagos-", ".sqlite3");
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
    }
    HotelDePrueba.tarifarTodo(jdbc, LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-16"));
  }

  private String reservar(String email, String llegada, String salida) throws Exception {
    String cuerpo = mvc.perform(post("/api/reservas").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("email", email, "llegada", llegada, "salida", salida,
          "huespedes", 2, "roomId", 1))))
      .andExpect(status().isCreated())
      .andReturn().getResponse().getContentAsString();
    return JSON.readTree(cuerpo).get("codigo").asText();
  }

  @Test
  @DisplayName("un abono baja el pendiente y el saldo lo dice")
  void abonoBajaElPendiente() throws Exception {
    String codigo = reservar("paga@example.com", "2026-11-01", "2026-11-03");

    mvc.perform(post("/api/admin/reservas/" + codigo + "/abonos").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("montoCents", 100000, "moneda", "COP",
          "concepto", "Anticipo"))))
      .andExpect(status().isCreated());

    mvc.perform(get("/api/admin/reservas/" + codigo + "/saldo").with(ADMIN))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalCents").value(300000))
      .andExpect(jsonPath("$.abonadoCents").value(100000))
      .andExpect(jsonPath("$.pendienteCents").value(200000))
      .andExpect(jsonPath("$.moneda").value("COP"));
  }

  @Test
  @DisplayName("anular un abono devuelve el pendiente")
  void anularAbonoDevuelveElPendiente() throws Exception {
    String codigo = reservar("anula@example.com", "2026-11-04", "2026-11-06");
    long abono = JSON.readTree(mvc.perform(post("/api/admin/reservas/" + codigo + "/abonos")
        .with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("montoCents", 300000, "moneda", "COP"))))
      .andExpect(status().isCreated())
      .andReturn().getResponse().getContentAsString()).get("id").asLong();

    mvc.perform(post("/api/admin/abonos/" + abono + "/anular").with(ADMIN).with(csrf()))
      .andExpect(status().isOk());

    mvc.perform(get("/api/admin/reservas/" + codigo + "/saldo").with(ADMIN))
      .andExpect(jsonPath("$.pendienteCents").value(300000))
      .andExpect(jsonPath("$.abonadoCents").value(0));
  }

  @Test
  @DisplayName("anular dos veces es un 400, no un doble reintegro silencioso")
  void dobleAnulacionEs400() throws Exception {
    String codigo = reservar("doble@example.com", "2026-11-06", "2026-11-08");
    long abono = JSON.readTree(mvc.perform(post("/api/admin/reservas/" + codigo + "/abonos")
        .with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("montoCents", 50000, "moneda", "COP"))))
      .andExpect(status().isCreated())
      .andReturn().getResponse().getContentAsString()).get("id").asLong();

    mvc.perform(post("/api/admin/abonos/" + abono + "/anular").with(ADMIN).with(csrf()))
      .andExpect(status().isOk());
    mvc.perform(post("/api/admin/abonos/" + abono + "/anular").with(ADMIN).with(csrf()))
      .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("abono con otra moneda o sin importe es un 400")
  void abonoConOtraMonedaEs400() throws Exception {
    String codigo = reservar("moneda@example.com", "2026-11-08", "2026-11-10");

    mvc.perform(post("/api/admin/reservas/" + codigo + "/abonos").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("montoCents", 1000, "moneda", "USD"))))
      .andExpect(status().isBadRequest());

    mvc.perform(post("/api/admin/reservas/" + codigo + "/abonos").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("montoCents", 0, "moneda", "COP"))))
      .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("el comprobante muestra lo abonado y lo pendiente")
  void comprobanteMuestraSaldo() throws Exception {
    String codigo = reservar("saldo@example.com", "2026-11-13", "2026-11-15");
    mvc.perform(post("/api/admin/reservas/" + codigo + "/abonos").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("montoCents", 120000, "moneda", "COP",
          "concepto", "Anticipo"))))
      .andExpect(status().isCreated());

    mvc.perform(get("/api/reservas/" + codigo + "/comprobante").param("email", "saldo@example.com"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.reserva.abonadoCents").value(120000))
      .andExpect(jsonPath("$.reserva.pendienteCents").value(180000));
  }

  @Test
  @DisplayName("abonar y saldar una reserva inexistente es un 404")
  void reservaInexistenteEs404() throws Exception {
    mvc.perform(post("/api/admin/reservas/H-NOEXISTE/abonos").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("montoCents", 1000, "moneda", "COP"))))
      .andExpect(status().isNotFound());
    mvc.perform(get("/api/admin/reservas/H-NOEXISTE/saldo").with(ADMIN))
      .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("abonar una reserva cancelada es un 400: lo terminal no recibe dinero")
  void abonoEnCanceladaEs400() throws Exception {
    String codigo = reservar("terminal@example.com", "2026-11-11", "2026-11-13");
    mvc.perform(post("/api/reservas/" + codigo + "/cancelar").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("email", "terminal@example.com"))))
      .andExpect(status().isOk());

    mvc.perform(post("/api/admin/reservas/" + codigo + "/abonos").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("montoCents", 50000, "moneda", "COP",
          "concepto", "Tarde"))))
      .andExpect(status().isBadRequest());
  }
}
