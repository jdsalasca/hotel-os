package co.hotel.inventario;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * El lote de tarifas que usa el panel: la previa no escribe y el lote rechazado no deja
 * nada a medias. Lo que el hotel ve en la previa es lo que se guarda al confirmar.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LoteTarifasControllerTest {

  private static final Path DB = crearBase();
  private static final RequestPostProcessor ADMIN = user("admin@hotel.test").roles("ADMIN");
  private static final ObjectMapper JSON = new ObjectMapper();

  private static Path crearBase() {
    try {
      Path p = Files.createTempFile("hotel-lote-", ".sqlite3");
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

  private Map<String, Object> lote(Object planId, Object tipoId, List<Map<String, Object>> noches) {
    return Map.of("ratePlanId", planId, "roomTypeId", tipoId, "noches", noches);
  }

  private Map<String, Object> noche(String fecha, long precioCents) {
    return Map.of("fecha", fecha, "precioCents", precioCents);
  }

  private Map<String, Object> rango(Object planId, Object tipoId, String desde, String hasta,
      List<Integer> dias, long precioCents) {
    return Map.of("ratePlanId", planId, "roomTypeId", tipoId,
      "rango", Map.of("desde", desde, "hasta", hasta, "diasSemana", dias),
      "precioCents", precioCents);
  }

  private long[] inventario(String sufijo) throws Exception {
    long tipoId = JSON.readTree(postear("/api/admin/tipos",
      Map.of("codigo", "LOTE" + sufijo, "nombre", "Doble lote", "capacidadMax", 2))).get("id").asLong();
    long planId = JSON.readTree(postear("/api/admin/planes",
      Map.of("codigo", "LOTEP" + sufijo, "nombre", "Plan lote", "moneda", "COP"))).get("id").asLong();
    return new long[] { planId, tipoId };
  }

  private String postear(String ruta, Map<String, ?> cuerpo) throws Exception {
    return mvc.perform(post(ruta).with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(cuerpo)))
      .andReturn().getResponse().getContentAsString();
  }

  private String tarifas(long planId, long tipoId) throws Exception {
    return tarifas(planId, tipoId, "2027-05-01", "2027-05-04");
  }

  private String tarifas(long planId, long tipoId, String desde, String hasta) throws Exception {
    return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
        "/api/admin/tarifas").with(ADMIN)
        .param("planId", String.valueOf(planId)).param("tipoId", String.valueOf(tipoId))
        .param("desde", desde).param("hasta", hasta))
      .andReturn().getResponse().getContentAsString();
  }

  @Test
  @DisplayName("la previa responde 200 con el detalle y no guarda nada")
  void previaNoEscribe() throws Exception {
    long[] ids = inventario("A");
    String previa = mvc.perform(post("/api/admin/tarifas/lote/preview").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(lote(ids[0], ids[1],
          List.of(noche("2027-05-01", 10000), noche("2027-05-02", 12000))))))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.lista").value(true))
      .andExpect(jsonPath("$.filas.length()").value(2))
      .andReturn().getResponse().getContentAsString();
    JsonNode raiz = JSON.readTree(previa);
    assert raiz.get("filas").get(0).get("precioCents").asLong() == 10000L;
    assert JSON.readTree(tarifas(ids[0], ids[1])).size() == 0 : "la previa no guarda";
  }

  @Test
  @DisplayName("un lote válido se confirma con 201 y lo guardado es lo previado")
  void loteValidoEs201() throws Exception {
    long[] ids = inventario("B");
    mvc.perform(post("/api/admin/tarifas/lote").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(lote(ids[0], ids[1],
          List.of(noche("2027-05-01", 10000), noche("2027-05-02", 12000))))))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.guardadas").value(2));
    assert JSON.readTree(tarifas(ids[0], ids[1])).size() == 2;
  }

  @Test
  @DisplayName("una fila inválida es 400 con motivos y nada se guarda")
  void loteInvalidoEs400SinEscribir() throws Exception {
    long[] ids = inventario("C");
    var noches = new java.util.ArrayList<Map<String, Object>>();
    noches.add(noche("2027-05-01", 10000));
    var mala = new java.util.HashMap<String, Object>();
    mala.put("fecha", "2027-05-02");
    mala.put("precioCents", 10000);
    mala.put("minEstancia", 0);
    noches.add(mala);
    mvc.perform(post("/api/admin/tarifas/lote").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(lote(ids[0], ids[1], noches))))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.error").exists())
      .andExpect(jsonPath("$.filas.length()").value(2))
      .andExpect(jsonPath("$.filas[1].valida").value(false))
      .andExpect(jsonPath("$.filas[1].motivo").exists());
    assert JSON.readTree(tarifas(ids[0], ids[1])).size() == 0 : "lo rechazado no toca nada";
  }

  @Test
  @DisplayName("una fecha mal escrita es error de su fila, no de la petición")
  void fechaMalaEsErrorDeFila() throws Exception {
    long[] ids = inventario("D");
    mvc.perform(post("/api/admin/tarifas/lote/preview").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(lote(ids[0], ids[1],
          List.of(noche("ayer", 10000))))))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.lista").value(false))
      .andExpect(jsonPath("$.filas[0].fecha").value("ayer"))
      .andExpect(jsonPath("$.filas[0].valida").value(false));
  }

  @Test
  @DisplayName("el rango expande lunes a viernes en seis filas sin escribir")
  void rangoEntreSemanaExpandeCinco() throws Exception {
    long[] ids = inventario("E");
    mvc.perform(post("/api/admin/tarifas/lote/rango/preview").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(
          rango(ids[0], ids[1], "2027-05-03", "2027-05-10", List.of(1, 2, 3, 4, 5), 15000))))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.lista").value(true))
      .andExpect(jsonPath("$.filas.length()").value(6))
      .andExpect(jsonPath("$.filas[0].fecha").value("2027-05-03"))
      .andExpect(jsonPath("$.filas[5].fecha").value("2027-05-10"));
    assert JSON.readTree(tarifas(ids[0], ids[1], "2027-05-01", "2027-05-11")).size() == 0 : "la previa no guarda";
  }

  @Test
  @DisplayName("el rango se confirma atómico con lo previado")
  void rangoSeConfirmaAtomico() throws Exception {
    long[] ids = inventario("F");
    mvc.perform(post("/api/admin/tarifas/lote/rango").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(
          rango(ids[0], ids[1], "2027-05-03", "2027-05-04", List.of(1, 2), 15000))))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.guardadas").value(2));
    assert JSON.readTree(tarifas(ids[0], ids[1], "2027-05-01", "2027-05-05")).size() == 2;
  }

  @Test
  @DisplayName("un día de semana imposible o un rango desmedido es 400")
  void rangoInvalidoEs400() throws Exception {
    long[] ids = inventario("G");
    mvc.perform(post("/api/admin/tarifas/lote/rango/preview").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(
          rango(ids[0], ids[1], "2027-05-03", "2027-05-10", List.of(0), 15000))))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.error").exists());
    mvc.perform(post("/api/admin/tarifas/lote/rango/preview").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(
          rango(ids[0], ids[1], "2027-01-01", "2028-06-01", List.of(1), 15000))))
      .andExpect(status().isBadRequest());
  }
}
