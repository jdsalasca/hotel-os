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
  @DisplayName("una fecha mal escrita es 400 con motivo, no 200 con error y ofertas vacías")
  void fechaMalEscritaEs400() throws Exception {
    mvc.perform(get("/api/disponibilidad")
        .param("llegada", "mal").param("salida", "2026-11-03").param("huespedes", "2"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.error").exists());
  }

  @Test
  @DisplayName("la salida anterior a la llegada es 400, no un 200 que parece sin disponibilidad")
  void salidaAnteriorALaLlegadaEs400() throws Exception {
    mvc.perform(get("/api/disponibilidad")
        .param("llegada", "2026-11-03").param("salida", "2026-11-01").param("huespedes", "2"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.error").exists());
  }

  @Test
  @DisplayName("huéspedes que no es número es 400 con motivo en español, no el 400 de Spring")
  void huespedesNoNumericoEs400ConMotivo() throws Exception {
    mvc.perform(get("/api/disponibilidad")
        .param("llegada", "2026-11-01").param("salida", "2026-11-03").param("huespedes", "muchos"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.error").exists());
  }

  @Test
  @DisplayName("cero huéspedes es 400 con motivo en las tres lecturas públicas")
  void ceroHuespedesEs400EnLasTresLecturas() throws Exception {
    mvc.perform(get("/api/disponibilidad")
        .param("llegada", "2026-11-01").param("salida", "2026-11-03").param("huespedes", "0"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.error").exists());
    mvc.perform(get("/api/disponibilidad/detalle")
        .param("roomId", "1")
        .param("llegada", "2026-11-01").param("salida", "2026-11-03").param("huespedes", "0"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.error").exists());
    mvc.perform(get("/api/disponibilidad/calendario").param("mes", "2026-11").param("huespedes", "0"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.error").exists());
  }

  @Test
  @DisplayName("detalle con habitación que no es número es 400 con motivo")
  void detalleConRoomIdNoNumericoEs400() throws Exception {
    mvc.perform(get("/api/disponibilidad/detalle")
        .param("roomId", "cualquiera")
        .param("llegada", "2026-11-01").param("salida", "2026-11-03").param("huespedes", "2"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.error").exists());
  }

  @Test
  @DisplayName("sin llegada la búsqueda es 400 con motivo, no el 400 de Spring")
  void busquedaSinLlegadaEs400ConMotivo() throws Exception {
    mvc.perform(get("/api/disponibilidad")
        .param("salida", "2026-11-03").param("huespedes", "2"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.error").exists());
  }

  @Test
  @DisplayName("200 con ofertas vacías es sin disponibilidad, no entrada inválida")
  void vacioEs200ConOfertasVacias() throws Exception {
    mvc.perform(get("/api/disponibilidad")
        .param("llegada", "2031-01-01").param("salida", "2031-01-03").param("huespedes", "2"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.ofertas").isArray())
      .andExpect(jsonPath("$.error").doesNotExist());
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
      .andExpect(jsonPath("$.ofertas[0].moneda").value("COP"))
      .andExpect(jsonPath("$.ofertas[0].plan.id").value(planId))
      .andExpect(jsonPath("$.ofertas[0].plan.codigo").value("PES_T"))
      .andExpect(jsonPath("$.ofertas[0].descuentoPct").value(0))
      .andExpect(jsonPath("$.ofertas[0].totalSinDescuentoCents").value(300000));
  }

  @Test
  @DisplayName("precio válido con mínimo inválido por HTTP: 400 y la noche intacta")
  void tarifaInvalidaPorHttpNoTocaNada() throws Exception {
    String sufijo = "Z" + System.nanoTime() % 100000;
    long tipoId = JSON.readTree(admin("/api/admin/tipos",
      Map.of("codigo", "DOBLE_" + sufijo, "nombre", "Habitación doble", "capacidadMax", 2))).get("id").asLong();
    long planId = JSON.readTree(admin("/api/admin/planes",
      Map.of("codigo", "PES_" + sufijo, "nombre", "Plan pesos", "moneda", "COP"))).get("id").asLong();
    var tarifa = Map.of("ratePlanId", planId, "roomTypeId", tipoId, "fecha", "2027-05-01");
    var base = new java.util.HashMap<String, Object>(tarifa);
    base.put("precioCents", 150_000);
    mvc.perform(post("/api/admin/tarifas").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(base)))
      .andExpect(status().isCreated());

    var mala = new java.util.HashMap<String, Object>(tarifa);
    mala.put("precioCents", 200_000);
    mala.put("minEstancia", 0);
    mvc.perform(post("/api/admin/tarifas").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(mala)))
      .andExpect(status().isBadRequest());

    mvc.perform(get("/api/admin/tarifas").with(ADMIN)
        .param("planId", String.valueOf(planId)).param("tipoId", String.valueOf(tipoId))
        .param("desde", "2027-05-01").param("hasta", "2027-05-02"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].precioCents").value(150000));
  }

  @Test
  @DisplayName("el hotel fija el descuento del plan por API y la oferta lo refleja")
  void descuentoPorApiLlegaALaOferta() throws Exception {
    long tipoId = JSON.readTree(admin("/api/admin/tipos",
      Map.of("codigo", "DOBLE_W", "nombre", "Habitación doble", "capacidadMax", 2))).get("id").asLong();
    admin("/api/admin/habitaciones", Map.of("codigo", "701", "roomTypeId", tipoId, "nombre", "Habitación 701"));
    long planId = JSON.readTree(admin("/api/admin/planes",
      Map.of("codigo", "PES_W", "nombre", "Plan pesos", "moneda", "COP"))).get("id").asLong();
    for (String fecha : new String[] { "2027-02-01", "2027-02-02" }) {
      mvc.perform(post("/api/admin/tarifas").with(ADMIN).with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(JSON.writeValueAsString(Map.of("ratePlanId", planId, "roomTypeId", tipoId,
            "fecha", fecha, "precioCents", 200_000))))
        .andExpect(status().isCreated());
    }

    mvc.perform(post("/api/admin/planes/" + planId + "/descuento").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("descuentoPct", 25))))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.descuentoPct").value(25));

    mvc.perform(post("/api/admin/planes/" + planId + "/descuento").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("descuentoPct", 101))))
      .andExpect(status().isBadRequest());

    mvc.perform(get("/api/disponibilidad")
        .param("llegada", "2027-02-01").param("salida", "2027-02-03").param("huespedes", "2"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.ofertas.length()").value(1))
      .andExpect(jsonPath("$.ofertas[0].habitacion.codigo").value("701"))
      .andExpect(jsonPath("$.ofertas[0].totalCents").value(300000))
      .andExpect(jsonPath("$.ofertas[0].totalSinDescuentoCents").value(400000))
      .andExpect(jsonPath("$.ofertas[0].descuentoPct").value(25));
  }

  @Test
  @DisplayName("el hotel retira un bloqueo y la habitación vuelve a la oferta")
  void retirarBloqueoDevuelveALaOferta() throws Exception {
    long tipoId = JSON.readTree(admin("/api/admin/tipos",
      Map.of("codigo", "DOBLE_R", "nombre", "Habitación doble", "capacidadMax", 2))).get("id").asLong();
    long habitacionId = JSON.readTree(admin("/api/admin/habitaciones",
      Map.of("codigo", "801", "roomTypeId", tipoId, "nombre", "Habitación 801"))).get("id").asLong();
    long planId = JSON.readTree(admin("/api/admin/planes",
      Map.of("codigo", "PES_R", "nombre", "Plan pesos", "moneda", "COP"))).get("id").asLong();
    for (String fecha : new String[] { "2027-03-01", "2027-03-02" }) {
      mvc.perform(post("/api/admin/tarifas").with(ADMIN).with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(JSON.writeValueAsString(Map.of("ratePlanId", planId, "roomTypeId", tipoId,
            "fecha", fecha, "precioCents", 150_000))))
        .andExpect(status().isCreated());
    }
    long bloqueoId = JSON.readTree(mvc.perform(post("/api/admin/bloqueos").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("roomId", habitacionId, "desde", "2027-03-01",
          "hasta", "2027-03-05", "motivo", "Mantenimiento"))))
      .andExpect(status().isCreated())
      .andReturn().getResponse().getContentAsString()).get("bloqueoId").asLong();

    mvc.perform(get("/api/disponibilidad")
        .param("llegada", "2027-03-01").param("salida", "2027-03-03").param("huespedes", "2"))
      .andExpect(jsonPath("$.ofertas[?(@.habitacion.codigo=='801')]").doesNotExist());

    mvc.perform(post("/api/admin/bloqueos/" + bloqueoId + "/retirar").with(ADMIN).with(csrf()))
      .andExpect(status().isOk());

    mvc.perform(get("/api/disponibilidad")
        .param("llegada", "2027-03-01").param("salida", "2027-03-03").param("huespedes", "2"))
      .andExpect(jsonPath("$.ofertas[?(@.habitacion.codigo=='801')]").exists());
  }

  @Test
  @DisplayName("retirar un bloqueo que no existe es un 404, no un 200 silencioso")
  void retirarBloqueoInexistenteEs404() throws Exception {
    mvc.perform(post("/api/admin/bloqueos/999999/retirar").with(ADMIN).with(csrf()))
      .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("el panel lista los bloqueos vigentes con su motivo")
  void listaBloqueosVigentes() throws Exception {
    long tipoId = JSON.readTree(admin("/api/admin/tipos",
      Map.of("codigo", "DOBLE_L", "nombre", "Habitación doble", "capacidadMax", 2))).get("id").asLong();
    long habitacionId = JSON.readTree(admin("/api/admin/habitaciones",
      Map.of("codigo", "802", "roomTypeId", tipoId, "nombre", "Habitación 802"))).get("id").asLong();

    mvc.perform(post("/api/admin/bloqueos").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("roomId", habitacionId, "desde", "2027-04-01",
          "hasta", "2027-04-05", "motivo", "Mantenimiento"))))
      .andExpect(status().isCreated());

    mvc.perform(get("/api/admin/bloqueos").with(ADMIN))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[?(@.motivo=='Mantenimiento')].habitacion").value("802"));
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
      .andExpect(jsonPath("$.dias[0].precios.length()").value(1))
      .andExpect(jsonPath("$.dias[0].precios[0].desdeCents").value(150000))
      .andExpect(jsonPath("$.dias[0].precios[0].moneda").value("COP"))
      .andExpect(jsonPath("$.dias[2].fecha").value("2026-12-03"))
      .andExpect(jsonPath("$.dias[2].disponibles").value(0))
      .andExpect(jsonPath("$.dias[2].precios").isEmpty());
  }

  @Test
  @DisplayName("el calendario cuenta habitaciones y agrupa el mínimo por moneda")
  void calendarioCuentaHabitacionesPorMoneda() throws Exception {
    long tipoId = JSON.readTree(admin("/api/admin/tipos",
      Map.of("codigo", "DOBLE_MC", "nombre", "Habitación doble", "capacidadMax", 2))).get("id").asLong();
    admin("/api/admin/habitaciones", Map.of("codigo", "901", "roomTypeId", tipoId, "nombre", "Habitación 901"));
    long copId = JSON.readTree(admin("/api/admin/planes",
      Map.of("codigo", "PES_MC", "nombre", "Plan pesos", "moneda", "COP"))).get("id").asLong();
    long usdId = JSON.readTree(admin("/api/admin/planes",
      Map.of("codigo", "USD_MC", "nombre", "Plan dólares", "moneda", "USD"))).get("id").asLong();
    for (long planId : new long[] { copId, usdId }) {
      mvc.perform(post("/api/admin/tarifas").with(ADMIN).with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(JSON.writeValueAsString(Map.of("ratePlanId", planId, "roomTypeId", tipoId,
            "fecha", "2028-01-10", "precioCents", planId == copId ? 100000 : 5000))))
        .andExpect(status().isCreated());
    }

    mvc.perform(get("/api/disponibilidad/calendario").param("mes", "2028-01").param("huespedes", "2"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.dias[9].fecha").value("2028-01-10"))
      .andExpect(jsonPath("$.dias[9].disponibles").value(1))
      .andExpect(jsonPath("$.dias[9].precios.length()").value(2))
      .andExpect(jsonPath("$.dias[9].precios[0].moneda").value("COP"))
      .andExpect(jsonPath("$.dias[9].precios[0].desdeCents").value(100000))
      .andExpect(jsonPath("$.dias[9].precios[1].moneda").value("USD"))
      .andExpect(jsonPath("$.dias[9].precios[1].desdeCents").value(5000));
  }

  @Test
  @DisplayName("el calendario con mes mal formado es un 400, no un 500")
  void calendarioConMesInvalidoEs400() throws Exception {
    mvc.perform(get("/api/disponibilidad/calendario").param("mes", "12-2026").param("huespedes", "2"))
      .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("el detalle de una oferta desglosa el precio noche por noche con su plan")
  void detalleOfertaDesglosaPorNoche() throws Exception {
    long tipoId = JSON.readTree(admin("/api/admin/tipos",
      Map.of("codigo", "DOBLE_D", "nombre", "Habitación doble", "capacidadMax", 2))).get("id").asLong();
    long habitacionId = JSON.readTree(admin("/api/admin/habitaciones",
      Map.of("codigo", "601", "roomTypeId", tipoId, "nombre", "Habitación 601"))).get("id").asLong();
    long planId = JSON.readTree(admin("/api/admin/planes",
      Map.of("codigo", "PES_D", "nombre", "Plan pesos", "moneda", "COP"))).get("id").asLong();
    for (String[] noche : new String[][] { { "2026-12-10", "150000" }, { "2026-12-11", "180000" } }) {
      mvc.perform(post("/api/admin/tarifas").with(ADMIN).with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(JSON.writeValueAsString(Map.of("ratePlanId", planId, "roomTypeId", tipoId,
            "fecha", noche[0], "precioCents", Integer.parseInt(noche[1])))))
        .andExpect(status().isCreated());
    }

    mvc.perform(get("/api/disponibilidad/detalle")
        .param("roomId", String.valueOf(habitacionId))
        .param("llegada", "2026-12-10").param("salida", "2026-12-12").param("huespedes", "2"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.habitacion.codigo").value("601"))
      .andExpect(jsonPath("$.tipo.capacidadMax").value(2))
      .andExpect(jsonPath("$.plan.codigo").value("PES_D"))
      .andExpect(jsonPath("$.noches.length()").value(2))
      .andExpect(jsonPath("$.noches[0].fecha").value("2026-12-10"))
      .andExpect(jsonPath("$.noches[0].precioCents").value(150000))
      .andExpect(jsonPath("$.noches[1].precioCents").value(180000))
      .andExpect(jsonPath("$.totalCents").value(330000))
      .andExpect(jsonPath("$.moneda").value("COP"));
  }

  @Test
  @DisplayName("el detalle de una habitación ocupada es 404: lo ocupada no se vende")
  void detalleDeHabitacionOcupadaEs404() throws Exception {
    long tipoId = JSON.readTree(admin("/api/admin/tipos",
      Map.of("codigo", "DOBLE_O", "nombre", "Habitación doble", "capacidadMax", 2))).get("id").asLong();
    long habitacionId = JSON.readTree(admin("/api/admin/habitaciones",
      Map.of("codigo", "603", "roomTypeId", tipoId, "nombre", "Habitación 603"))).get("id").asLong();
    long planId = JSON.readTree(admin("/api/admin/planes",
      Map.of("codigo", "PES_O", "nombre", "Plan pesos", "moneda", "COP"))).get("id").asLong();
    for (String fecha : new String[] { "2026-12-10", "2026-12-11" }) {
      mvc.perform(post("/api/admin/tarifas").with(ADMIN).with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(JSON.writeValueAsString(Map.of("ratePlanId", planId, "roomTypeId", tipoId,
            "fecha", fecha, "precioCents", 150000))))
        .andExpect(status().isCreated());
    }
    mvc.perform(post("/api/reservas").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("email", "ana@example.com", "nombre", "Ana",
          "llegada", "2026-12-10", "salida", "2026-12-12", "huespedes", 2, "roomId", habitacionId,
          "idempotencia", "idem-detalle-603", "totalEsperadoCents", 300000, "monedaEsperada", "COP",
          "ratePlanIdEsperado", planId))))
      .andExpect(status().isCreated());

    mvc.perform(get("/api/disponibilidad/detalle")
        .param("roomId", String.valueOf(habitacionId))
        .param("llegada", "2026-12-10").param("salida", "2026-12-12").param("huespedes", "2"))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.error").exists());
  }

  @Test
  @DisplayName("el detalle de una habitación sin tarifa es un 404, no un desglose vacío")
  void detalleSinTarifaEs404() throws Exception {
    long tipoId = JSON.readTree(admin("/api/admin/tipos",
      Map.of("codigo", "SIMPLE_D", "nombre", "Habitación simple", "capacidadMax", 1))).get("id").asLong();
    long habitacionId = JSON.readTree(admin("/api/admin/habitaciones",
      Map.of("codigo", "602", "roomTypeId", tipoId, "nombre", "Habitación 602"))).get("id").asLong();

    mvc.perform(get("/api/disponibilidad/detalle")
        .param("roomId", String.valueOf(habitacionId))
        .param("llegada", "2026-12-10").param("salida", "2026-12-12").param("huespedes", "1"))
      .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("las APIs de administración exigen sesión")
  void lasApisDeAdminExigenSesion() throws Exception {
    mvc.perform(post("/api/admin/tipos").with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{}")).andExpect(status().isUnauthorized());
  }
}