package co.hotel.inventario;

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
 * El calendario de ocupación que ve el hotel. Lo que se fija aquí es que la pantalla reciba
 * habitaciones con sus noches, y que una fecha mal escrita sea un 400 con mensaje claro y no un
 * error 500 que el panel no sabe explicar.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CalendarioControllerTest {

  private static final Path DB = crearBase();
  private static final RequestPostProcessor ADMIN = user("admin@hotel.test").roles("ADMIN");
  private static final ObjectMapper JSON = new ObjectMapper();

  private static Path crearBase() {
    try {
      Path p = Files.createTempFile("hotel-cal-", ".sqlite3");
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
    return mvc.perform(post(ruta).with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(cuerpo)))
      .andReturn().getResponse().getContentAsString();
  }

  @Test
  @DisplayName("el calendario devuelve cada habitación con una noche por día del periodo")
  void calendarioHabitacionPorHabitacion() throws Exception {
    long tipoId = JSON.readTree(admin("/api/admin/tipos",
      Map.of("codigo", "CAL", "nombre", "Doble calendario", "capacidadMax", 2))).get("id").asLong();
    admin("/api/admin/habitaciones", Map.of("codigo", "301", "roomTypeId", tipoId, "nombre", "301"));

    mvc.perform(get("/api/admin/calendario").with(ADMIN)
        .param("desde", "2027-03-01").param("hasta", "2027-03-04"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].codigo").value("301"))
      .andExpect(jsonPath("$[0].noches.length()").value(3))
      .andExpect(jsonPath("$[0].noches[0].fecha").value("2027-03-01"))
      .andExpect(jsonPath("$[0].noches[0].estado").value("LIBRE"));
  }

  @Test
  @DisplayName("una fecha mal escrita devuelve 400 con mensaje, no un error 500")
  void fechaMalFormadaEs400() throws Exception {
    mvc.perform(get("/api/admin/calendario").with(ADMIN)
        .param("desde", "ayer").param("hasta", "2027-03-04"))
      .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("un periodo invertido devuelve 400 en lugar de una tabla vacía sin explicación")
  void periodoInvertidoEs400() throws Exception {
    mvc.perform(get("/api/admin/calendario").with(ADMIN)
        .param("desde", "2027-03-04").param("hasta", "2027-03-01"))
      .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("un bloqueo con fecha mal escrita devuelve 400, no un error 500")
  void bloqueoFechaMalaEs400() throws Exception {
    mvc.perform(post("/api/admin/bloqueos").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("desde", "ayer", "hasta", "2027-04-02", "motivo", "prueba"))))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.error").exists());
  }

  @Test
  @DisplayName("una tarifa con fecha mal escrita devuelve 400, no un error 500")
  void tarifaFechaMalaEs400() throws Exception {
    mvc.perform(post("/api/admin/tarifas").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("ratePlanId", 1, "roomTypeId", 1, "fecha", "ayer",
          "precioCents", 100))))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.error").exists());
  }

  @Test
  @DisplayName("el calendario solo lo ve el administrador")
  void sinRolNoHayCalendario() throws Exception {
    var anonimo = user("visitante").roles("USER");
    mvc.perform(get("/api/admin/calendario").with(anonimo)
        .param("desde", "2027-03-01").param("hasta", "2027-03-04"))
      .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("sin sesión, el calendario no responde datos")
  void sinSesionNoHayCalendario() throws Exception {
    mvc.perform(get("/api/admin/calendario")
        .param("desde", "2027-03-01").param("hasta", "2027-03-04"))
      .andExpect(status().is4xxClientError());
  }
}
