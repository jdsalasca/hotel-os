package co.hotel.disponibilidad;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

/**
 * Flujo público de búsqueda y APIs de administración del inventario.
 *
 * Lo que se fija aquí es lo que una persona ve: si el hotel no ha configurado precios, la web no
 * ofrece habitaciones con un importe inventado.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DisponibilidadControllerTest {

  private static final Path DB = crearBase();
  private static final RequestPostProcessor ADMIN = user("admin@hotel.test").roles("ADMIN");
  private static final ObjectMapper JSON = new ObjectMapper();

  private static Path crearBase() {
    try {
      Path p = Files.createTempFile("hotel-disp-", ".sqlite3");
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

  private String admin(String ruta, Map<String, ?> cuerpo) throws Exception {
    String res = mvc.perform(post(ruta).with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(cuerpo)))
      .andReturn().getResponse().getContentAsString();
    return res;
  }

  @Test
  @DisplayName("el hotel da de alta tipo, habitación, plan y precios desde la API de administración")
  void elHotelConfiguraSuInventarioYTarifas() throws Exception {
    long tipoId = JSON.readTree(admin("/api/admin/tipos",
      Map.of("codigo", "DOBLE", "nombre", "Habitación doble", "capacidadMax", 2))).get("id").asLong();

    long habitacionId = JSON.readTree(admin("/api/admin/habitaciones",
      Map.of("codigo", "101", "roomTypeId", tipoId, "nombre", "Habitación 101"))).get("id").asLong();
    org.junit.jupiter.api.Assertions.assertTrue(habitacionId > 0);

    long planId = JSON.readTree(admin("/api/admin/planes",
      Map.of("codigo", "PES", "nombre", "Plan pesos", "moneda", "COP"))).get("id").asLong();

    mvc.perform(post("/api/admin/tarifas").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("ratePlanId", planId, "roomTypeId", tipoId,
          "fecha", "2026-11-01", "precioCents", 150_000))))
      .andExpect(status().isCreated());
    mvc.perform(post("/api/admin/tarifas").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("ratePlanId", planId, "roomTypeId", tipoId,
          "fecha", "2026-11-02", "precioCents", 150_000))))
      .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("sin tarifas configuradas, la búsqueda pública devuelve una lista vacía, no un precio inventado")
  void busquedaSinTarifasNoInventaPrecios() throws Exception {
    long tipoId = JSON.readTree(admin("/api/admin/tipos",
      Map.of("codigo", "SIMPLE", "nombre", "Habitación simple", "capacidadMax", 2))).get("id").asLong();
    admin("/api/admin/habitaciones", Map.of("codigo", "201", "roomTypeId", tipoId, "nombre", "Habitación 201"));

    mvc.perform(get("/api/disponibilidad")
        .param("llegada", "2026-11-01").param("salida", "2026-11-03").param("huespedes", "2"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.ofertas").isEmpty());
  }

  @Test
  @DisplayName("con tarifas, la búsqueda devuelve el total y la moneda del plan")
  void busquedaConTarifasDevuelveTotal() throws Exception {
    long tipoId = JSON.readTree(admin("/api/admin/tipos",
      Map.of("codigo", "DOBLE_T", "nombre", "Habitación doble", "capacidadMax", 2))).get("id").asLong();
    admin("/api/admin/habitaciones", Map.of("codigo", "301", "roomTypeId", tipoId, "nombre", "Habitación 301"));
    long planId = JSON.readTree(admin("/api/admin/planes",
      Map.of("codigo", "PES_T", "nombre", "Plan pesos", "moneda", "COP"))).get("id").asLong();
    for (String fecha : new String[] { "2026-12-01", "2026-12-02" }) {
      mvc.perform(post("/api/admin/tarifas").with(ADMIN).with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(JSON.writeValueAsString(Map.of("ratePlanId", planId, "roomTypeId", tipoId,
            "fecha", fecha, "precioCents", 150_000))))
        .andExpect(status().isCreated());
    }

    mvc.perform(get("/api/disponibilidad")
        .param("llegada", "2026-12-01").param("salida", "2026-12-03").param("huespedes", "2"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.ofertas[0].habitacion.codigo").value("301"))
      .andExpect(jsonPath("$.ofertas[0].totalCents").value(300000))
      .andExpect(jsonPath("$.ofertas[0].moneda").value("COP"));
  }

  @Test
  @DisplayName("el hotel puede bloquear una habitación por mantenimiento y desaparece de la oferta")
  void bloqueoPorMantenimientoSacaDeLaOferta() throws Exception {
    long tipoId = JSON.readTree(admin("/api/admin/tipos",
      Map.of("codigo", "DOBLE_B", "nombre", "Habitación doble", "capacidadMax", 2))).get("id").asLong();
    long habitacionId = JSON.readTree(admin("/api/admin/habitaciones",
      Map.of("codigo", "401", "roomTypeId", tipoId, "nombre", "Habitación 401"))).get("id").asLong();
    long planId = JSON.readTree(admin("/api/admin/planes",
      Map.of("codigo", "PES_B", "nombre", "Plan pesos", "moneda", "COP"))).get("id").asLong();
    for (String fecha : new String[] { "2027-01-01", "2027-01-02" }) {
      admin("/api/admin/tarifas", Map.of("ratePlanId", planId, "roomTypeId", tipoId,
        "fecha", fecha, "precioCents", 150_000));
    }

    mvc.perform(post("/api/admin/bloqueos").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("roomId", habitacionId, "desde", "2027-01-01",
          "hasta", "2027-01-05", "motivo", "Mantenimiento"))))
      .andExpect(status().isCreated());

    mvc.perform(get("/api/disponibilidad")
        .param("llegada", "2027-01-01").param("salida", "2027-01-03").param("huespedes", "2"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.ofertas").isEmpty());
  }

  @Test
  @DisplayName("el calendario del mes dice qué días tienen habitaciones y desde qué precio")
  void calendarioMensualMuestraDisponibilidadPorDia() throws Exception {
    long tipoId = JSON.readTree(admin("/api/admin/tipos",
      Map.of("codigo", "DOBLE_C", "nombre", "Habitación doble", "capacidadMax", 2))).get("id").asLong();
    admin("/api/admin/habitaciones", Map.of("codigo", "501", "roomTypeId", tipoId, "nombre", "Habitación 501"));
    long planId = JSON.readTree(admin("/api/admin/planes",
      Map.of("codigo", "PES_C", "nombre", "Plan pesos", "moneda", "COP"))).get("id").asLong();
    for (String fecha : new String[] { "2026-12-01", "2026-12-02" }) {
      mvc.perform(post("/api/admin/tarifas").with(ADMIN).with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(JSON.writeValueAsString(Map.of("ratePlanId", planId, "roomTypeId", tipoId,
            "fecha", fecha, "precioCents", 150_000))))
        .andExpect(status().isCreated());
    }

    mvc.perform(get("/api/disponibilidad/calendario").param("mes", "2026-12").param("huespedes", "2"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.mes").value("2026-12"))
      .andExpect(jsonPath("$.dias.length()").value(31))
      .andExpect(jsonPath("$.dias[0].fecha").value("2026-12-01"))
      .andExpect(jsonPath("$.dias[0].disponibles", greaterThanOrEqualTo(1)))
      .andExpect(jsonPath("$.dias[0].desdeCents").value(150000))
      .andExpect(jsonPath("$.dias[0].moneda").value("COP"))
      .andExpect(jsonPath("$.dias[2].fecha").value("2026-12-03"))
      .andExpect(jsonPath("$.dias[2].disponibles").value(0));
  }

  @Test
  @DisplayName("el calendario con mes mal formado es un 400, no un 500")
  void calendarioConMesInvalidoEs400() throws Exception {
    mvc.perform(get("/api/disponibilidad/calendario").param("mes", "12-2026").param("huespedes", "2"))
      .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("las APIs de administración exigen sesión")
  void lasApisDeAdminExigenSesion() throws Exception {
    mvc.perform(post("/api/admin/tipos").with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{}")).andExpect(status().isUnauthorized());
  }
}