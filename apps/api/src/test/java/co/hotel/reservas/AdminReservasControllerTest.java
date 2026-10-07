package co.hotel.reservas;

import static org.hamcrest.Matchers.containsString;
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
import java.time.LocalDate;
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
    // Esta clase crea una reserva por test y ya supera las diez del límite público; sin esto los
    // últimos tests se comerían un 429 y el fallo parecería del endpoint y no del tope.
    reg.add("hotel.limites.reservas", () -> 200);
  }

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void inventario() {
    if (jdbc.queryForObject("SELECT COUNT(*) FROM rooms", Integer.class) == 0) {
      jdbc.update("INSERT INTO rooms(codigo, estado, nombre) VALUES('101','ACTIVA','Habitación 101')");
    }
    // La reserva pública exige precio acordado; esta clase mide el panel, no las tarifas.
    HotelDePrueba.tarifarTodo(jdbc, LocalDate.parse("2026-11-01"), LocalDate.parse("2026-12-28"));
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

  /** Habitación con tarifa completa para probar el comprobante con precio real. */
  private record Escenario(long room, long tipo, long plan) {}

  private Escenario habitacionTarifada(String sufijo) throws Exception {
    long tipo = JSON.readTree(mvc.perform(post("/api/admin/tipos").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(json(Map.of("codigo", "V" + sufijo, "nombre", "Voucher " + sufijo, "capacidadMax", 2))))
      .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
    long habitacion = JSON.readTree(mvc.perform(post("/api/admin/habitaciones").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(json(Map.of("codigo", "V" + sufijo, "roomTypeId", tipo, "nombre", "Habitación " + sufijo))))
      .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
    long plan = JSON.readTree(mvc.perform(post("/api/admin/planes").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(json(Map.of("codigo", "VP" + sufijo, "nombre", "Plan " + sufijo, "moneda", "COP"))))
      .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
    for (String dia : new String[] {"2026-11-01", "2026-11-02"}) {
      mvc.perform(post("/api/admin/tarifas").with(ADMIN).with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(json(Map.of("ratePlanId", plan, "roomTypeId", tipo, "fecha", dia, "precioCents", 150_000))))
        .andExpect(status().isCreated());
    }
    return new Escenario(habitacion, tipo, plan);
  }

  private String crearReservaEn(String email, String llegada, String salida, long roomId) throws Exception {
    String cuerpo = mvc.perform(post("/api/reservas").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(json(Map.of("email", email, "llegada", llegada, "salida", salida,
          "huespedes", 2, "roomId", roomId))))
      .andExpect(status().isCreated())
      .andReturn().getResponse().getContentAsString();
    return JSON.readTree(cuerpo).get("codigo").asText();
  }

  @Test
  @DisplayName("el comprobante público muestra habitación, total acordado e identidad del hotel")
  void comprobantePublicoConPrecio() throws Exception {
    var escenario = habitacionTarifada("A" + System.nanoTime() % 100000);
    String codigo = crearReservaEn("vale@example.com", "2026-11-01", "2026-11-03", escenario.room());

    mvc.perform(get("/api/reservas/" + codigo + "/comprobante").param("email", "vale@example.com"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.reserva.codigo").value(codigo))
      .andExpect(jsonPath("$.reserva.totalCents").value(300000))
      .andExpect(jsonPath("$.reserva.moneda").value("COP"))
      .andExpect(jsonPath("$.habitacion.codigo").exists())
      .andExpect(jsonPath("$.historial").isArray());
  }

  @Test
  @DisplayName("el comprobante público exige el correo de la reserva")
  void comprobantePublicoExigeCorreo() throws Exception {
    var escenario = habitacionTarifada("B" + System.nanoTime() % 100000);
    String codigo = crearReservaEn("vale@example.com", "2026-11-01", "2026-11-03", escenario.room());

    mvc.perform(get("/api/reservas/" + codigo + "/comprobante").param("email", "otro@example.com"))
      .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("el comprobante congela el precio: si la tarifa cambia después, el total no se mueve")
  void comprobanteCongelaElPrecioAcordado() throws Exception {
    var escenario = habitacionTarifada("C" + System.nanoTime() % 100000);
    String codigo = crearReservaEn("vale@example.com", "2026-11-01", "2026-11-03", escenario.room());

    // El hotel sube el precio de la segunda noche DESPUÉS de la reserva: 150.000 -> 200.000.
    mvc.perform(post("/api/admin/tarifas").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(json(Map.of("ratePlanId", escenario.plan(), "roomTypeId", escenario.tipo(),
          "fecha", "2026-11-02", "precioCents", 200_000))))
      .andExpect(status().isCreated());

    mvc.perform(get("/api/reservas/" + codigo + "/comprobante").param("email", "vale@example.com"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.reserva.totalCents").value(300000));
  }

  @Test
  @DisplayName("el panel ve el comprobante sin necesidad del correo")
  void comprobanteAdminSinCorreo() throws Exception {
    var escenario = habitacionTarifada("D" + System.nanoTime() % 100000);
    String codigo = crearReservaEn("vale@example.com", "2026-11-01", "2026-11-03", escenario.room());

    mvc.perform(get("/api/admin/reservas/" + codigo + "/comprobante").with(ADMIN))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.reserva.totalCents").value(300000))
      .andExpect(jsonPath("$.habitacion.codigo").exists());
  }

  @Test
  @DisplayName("una reserva cancelada ya no se puede volver a confirmar")
  void cancelarEsTerminal() throws Exception {
    String codigo = crearReserva("terminal@example.com", "2026-12-01", "2026-12-03");

    mvc.perform(post("/api/admin/reservas/" + codigo + "/estado").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(json(Map.of("estado", "CANCELADA"))))
      .andExpect(status().isOk());

    mvc.perform(post("/api/admin/reservas/" + codigo + "/estado").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(json(Map.of("estado", "CONFIRMADA"))))
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.error").value(containsString("CANCELADA")));

    mvc.perform(get("/api/admin/reservas/" + codigo).with(ADMIN))
      .andExpect(jsonPath("$.reserva.estado").value("CANCELADA"));
  }

  @Test
  @DisplayName("rechazar tampoco tiene vuelta atrás")
  void rechazarEsTerminal() throws Exception {
    String codigo = crearReserva("rechazo@example.com", "2026-12-05", "2026-12-07");
    cambiarEstado(codigo, "RECHAZADA");

    mvc.perform(post("/api/admin/reservas/" + codigo + "/estado").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(json(Map.of("estado", "PENDIENTE"))))
      .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("los caminos de cada día siguen abiertos: confirmar y cancelar")
  void losCaminosNormalesSiguenAbiertos() throws Exception {
    String a = crearReserva("camino1@example.com", "2026-12-09", "2026-12-11");
    mvc.perform(post("/api/admin/reservas/" + a + "/estado").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("estado", "CONFIRMADA"))))
      .andExpect(status().isOk());
    mvc.perform(post("/api/admin/reservas/" + a + "/estado").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("estado", "CANCELADA"))))
      .andExpect(status().isOk());

    String b = crearReserva("camino2@example.com", "2026-12-09", "2026-12-11");
    mvc.perform(post("/api/admin/reservas/" + b + "/estado").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("estado", "CONFIRMADA"))))
      .andExpect(status().isOk());
  }

  @Test
  @DisplayName("buscar por fragmento de correo encuentra sin traer el resto")
  void buscarPorCorreoEncuentra() throws Exception {
    var escenario = habitacionTarifada("Q" + System.nanoTime() % 100000);
    crearReservaEn("zuniga-unica@example.com", "2026-11-01", "2026-11-03", escenario.room());

    mvc.perform(get("/api/admin/reservas").with(ADMIN).param("q", "zuniga-unica"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.length()").value(1))
      .andExpect(jsonPath("$[0].email").value("zuniga-unica@example.com"));
  }

  @Test
  @DisplayName("buscar por código encuentra la reserva exacta")
  void buscarPorCodigoEncuentra() throws Exception {
    var escenario = habitacionTarifada("W" + System.nanoTime() % 100000);
    String codigo = crearReservaEn("codigo@example.com", "2026-11-01", "2026-11-03", escenario.room());

    mvc.perform(get("/api/admin/reservas").with(ADMIN).param("q", codigo.substring(2, 6)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[?(@.codigo=='" + codigo + "')]").exists());
  }

  @Test
  @DisplayName("filtrar por estado trae la cancelada y esconde la pendiente")
  void filtrarPorEstadoSoloTraeEseEstado() throws Exception {
    var escenario = habitacionTarifada("E" + System.nanoTime() % 100000);
    String pendiente = crearReservaEn("queda@example.com", "2026-11-01", "2026-11-03", escenario.room());
    var otro = habitacionTarifada("R" + System.nanoTime() % 100000);
    String cancelada = crearReservaEn("secierra@example.com", "2026-11-01", "2026-11-03", otro.room());
    cambiarEstado(cancelada, "CANCELADA");

    var cuerpo = mvc.perform(get("/api/admin/reservas").with(ADMIN).param("estado", "CANCELADA"))
      .andExpect(status().isOk())
      .andReturn().getResponse().getContentAsString();
    assertTrue(JSON.readTree(cuerpo).toString().contains(cancelada), "la cancelada debe salir");
    assertTrue(!JSON.readTree(cuerpo).toString().contains(pendiente), "la pendiente no debe salir");
  }

  @Test
  @DisplayName("un estado que no existe filtra con 400, no con lista vacía silenciosa")
  void estadoDeFiltroInvalidoEs400() throws Exception {
    mvc.perform(get("/api/admin/reservas").with(ADMIN).param("estado", "VOLADORA"))
      .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("el panel recibe del servidor los estados alcanzables, no los deduce")
  void elPanelRecibeLasTransicionesValidas() throws Exception {
    String cancelada = crearReserva("siguientes1@example.com", "2026-12-17", "2026-12-19");
    cambiarEstado(cancelada, "CANCELADA");

    mvc.perform(get("/api/admin/reservas/" + cancelada).with(ADMIN))
      .andExpect(jsonPath("$.reserva.siguientes").isEmpty());

    mvc.perform(get("/api/admin/reservas").with(ADMIN))
      .andExpect(jsonPath("$[?(@.codigo=='" + cancelada + "')][0].siguientes").isEmpty())
      .andExpect(jsonPath("$[0].siguientes").exists());
  }

  @Test
  @DisplayName("una confirmada solo ofrece cancelar; una pendiente ofrece las tres")
  void lasTransicionesQueOfreceCadaEstado() throws Exception {
    String confirmada = crearReserva("siguientes2@example.com", "2026-12-21", "2026-12-23");
    cambiarEstado(confirmada, "CONFIRMADA");
    mvc.perform(get("/api/admin/reservas/" + confirmada).with(ADMIN))
      .andExpect(jsonPath("$.reserva.siguientes[0]").value("CANCELADA"))
      .andExpect(jsonPath("$.reserva.siguientes.length()").value(1));

    String pendiente = crearReserva("siguientes3@example.com", "2026-12-25", "2026-12-27");
    mvc.perform(get("/api/admin/reservas/" + pendiente).with(ADMIN))
      .andExpect(jsonPath("$.reserva.siguientes.length()").value(3));
  }

  @Test
  @DisplayName("un estado que no existe sigue siendo un 400, no un 409")
  void estadoInexistenteSigoSiendo400() throws Exception {
    String codigo = crearReserva("inexistente@example.com", "2026-12-13", "2026-12-15");
    mvc.perform(post("/api/admin/reservas/" + codigo + "/estado").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("estado", "VOLADORA"))))
      .andExpect(status().isBadRequest());
  }
}