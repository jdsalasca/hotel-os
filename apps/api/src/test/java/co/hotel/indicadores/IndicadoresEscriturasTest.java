package co.hotel.indicadores;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
 * Contratos de escritura del informe: lo rechazado responde 400 (nunca 200 con error ni 500)
 * y no deja datos. Un POST inválido no escribe; un GET no escribe (eso ya lo cubre
 * IndicadoresSoloLecturaTest).
 */
@SpringBootTest
@AutoConfigureMockMvc
class IndicadoresEscriturasTest {

  private static final Path DB = crearBase();
  private static final RequestPostProcessor ADMIN = user("admin@hotel.test").roles("ADMIN");
  private static final ObjectMapper JSON = new ObjectMapper();

  private static Path crearBase() {
    try {
      Path p = Files.createTempFile("hotel-ind-esc-", ".sqlite3");
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
  void limpiar() {
    jdbc.update("DELETE FROM adoption_activities");
  }

  private int actividades() {
    return jdbc.queryForObject("SELECT COUNT(*) FROM adoption_activities", Integer.class);
  }

  @Test
  @DisplayName("fijar inventario esperado inválido es 400, no 200 con error")
  void inventarioEsperadoInvalidoEs400() throws Exception {
    mvc.perform(post("/api/admin/indicadores/inventario-esperado").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("habitaciones", -1))))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.error").exists());
  }

  @Test
  @DisplayName("fijar inventario esperado válido responde 200 con lo guardado")
  void inventarioEsperadoValidoEs200() throws Exception {
    mvc.perform(post("/api/admin/indicadores/inventario-esperado").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("habitaciones", 4))))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.inventarioEsperado").value(4));
  }

  @Test
  @DisplayName("un periodo con mes imposible es 400, no 500")
  void periodoConMesImposibleEs400() throws Exception {
    mvc.perform(get("/api/admin/indicadores").with(ADMIN).param("periodo", "2026-99"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.error").exists());
    mvc.perform(get("/api/admin/indicadores.csv").with(ADMIN).param("periodo", "2026-99"))
      .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("una actividad con fecha inválida es 400 y no queda guardada")
  void actividadInvalidaEs400SinEscribir() throws Exception {
    mvc.perform(post("/api/admin/indicadores/actividades").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(Map.of(
          "tipo", "CAPACITACION", "descripcion", "Uso del panel", "fecha", "ayer"))))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.error").exists());
    assert actividades() == 0 : "un POST inválido no deja datos";
  }

  @Test
  @DisplayName("fijar referencia válida es 200 y se refleja en el informe")
  void referenciaValidaEs200() throws Exception {
    mvc.perform(post("/api/admin/indicadores/definiciones/f3_ocupacion/referencia").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(Map.of(
          "lineaBase", "60% en temporada baja", "meta", "75%", "responsable", "Gerencia"))))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.clave").value("f3_ocupacion"))
      .andExpect(jsonPath("$.meta").value("75%"))
      .andExpect(jsonPath("$.responsable").value("Gerencia"));
    mvc.perform(get("/api/admin/indicadores").with(ADMIN).param("periodo", "2026-11"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.indicadores[?(@.clave == 'f3_ocupacion')].meta", hasItem("75%")));
  }

  @Test
  @DisplayName("la referencia de una clave inexistente es 404")
  void referenciaInexistenteEs404() throws Exception {
    mvc.perform(post("/api/admin/indicadores/definiciones/f9_no_existe/referencia").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(Map.of("meta", "75%"))))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.error").exists());
  }

  @Test
  @DisplayName("una actividad válida se registra con 201")
  void actividadValidaEs201() throws Exception {
    mvc.perform(post("/api/admin/indicadores/actividades").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(Map.of(
          "tipo", "CAPACITACION", "descripcion", "Uso del panel", "fecha", "2026-11-03",
          "participantes", 3))))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.fecha").value("2026-11-03"));
    assert actividades() == 1;
  }
}
