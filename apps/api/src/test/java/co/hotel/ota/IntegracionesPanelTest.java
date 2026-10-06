package co.hotel.ota;

import static org.junit.jupiter.api.Assertions.assertTrue;
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
 * Pantalla de integraciones contra la app real y sin credenciales de proveedor configuradas.
 *
 * Es la prueba que impide que el sistema declare algo que no puede hacer: sin acceso de socio ni
 * credenciales, los tres canales deben aparecer como NO_CONFIGURADO con su bloqueo explicado.
 */
@SpringBootTest
@AutoConfigureMockMvc
class IntegracionesPanelTest {

  private static final RequestPostProcessor ADMIN = user("admin@hotel.test").roles("ADMIN");
  private static final ObjectMapper JSON = new ObjectMapper();

  @DynamicPropertySource
  static void propiedades(DynamicPropertyRegistry reg) {
    reg.add("hotel.jdbc-path", () -> {
      try {
        Path p = Files.createTempFile("hotel-ota-", ".sqlite3");
        Files.delete(p);
        return p.toAbsolutePath().toString();
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    });
  }

  @Autowired MockMvc mvc;

  @Test
  @DisplayName("sin credenciales, los tres canales aparecen NO_CONFIGURADO con requisitos pendientes")
  void sinCredencialesNadaSeDeclaraConectado() throws Exception {
    mvc.perform(get("/api/admin/integraciones").with(ADMIN))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.BOOKING.estado").value("NO_CONFIGURADO"))
      .andExpect(jsonPath("$.DESPEGAR.estado").value("NO_CONFIGURADO"))
      .andExpect(jsonPath("$.AIRBNB.estado").value("NO_CONFIGURADO"))
      .andExpect(jsonPath("$.BOOKING.requisitosPendientes").isNotEmpty())
      .andExpect(jsonPath("$.DESPEGAR.variablesRequeridas").isNotEmpty());
  }

  @Test
  @DisplayName("el panel muestra los bloqueos reales: acceso de socio y certificaciones pendientes")
  void elPanelExplicaElBloqueo() throws Exception {
    String panel = mvc.perform(get("/api/admin/integraciones").with(ADMIN))
      .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertTrue(panel.contains("partner"), "debe recordar que falta el acceso de partner");
    assertTrue(panel.contains("BOOKING_CLIENT_ID"), "debe decir qué credenciales faltan");
  }

  @Test
  @DisplayName("sincronizar sin credenciales responde 502 y deja constancia, sin fingir éxito")
  void sincronizarSinCredencialesNoInventaExito() throws Exception {
    mvc.perform(post("/api/admin/integraciones/BOOKING/sincronizar").with(ADMIN).with(csrf()))
      .andExpect(status().isBadGateway())
      .andExpect(jsonPath("$.exitosa").value(false));
  }

  @Test
  @DisplayName("un canal desconocido se rechaza con 400")
  void canalDesconocidoSeRechaza() throws Exception {
    mvc.perform(post("/api/admin/integraciones/EXpedia/sincronizar").with(ADMIN).with(csrf()))
      .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("la pantalla de integraciones exige sesión")
  void elPanelExigeSesion() throws Exception {
    mvc.perform(get("/api/admin/integraciones")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("el hotel registra el identificador externo de una habitación y lo ve en el panel")
  void crearMapeoYVerloEnElPanel() throws Exception {
    long habitacion = crearHabitacion("MAP-" + System.nanoTime(), "H-" + System.nanoTime());
    String externo = "BOOKING-" + System.nanoTime();
    String respuesta = mapear(Map.of("canal", "BOOKING", "roomId", habitacion, "externalId", externo));
    assertTrue(respuesta.contains(externo), "la creación debe devolver el mapeo creado");

    String panel = mvc.perform(get("/api/admin/integraciones").with(ADMIN))
      .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertTrue(panel.contains(externo), "el panel debe mostrar el identificador que el hotel registró");
  }

  @Test
  @DisplayName("un mapeo sin recurso local se rechaza: un identificador externo solo no publica nada")
  void mapeoSinRecursoSeRechaza() throws Exception {
    mvc.perform(post("/api/admin/integraciones/mapeos").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("canal", "BOOKING", "externalId", "BOOKING-SIN-" + System.nanoTime()))))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.error").exists());
  }

  @Test
  @DisplayName("un mapeo con habitación inexistente se rechaza sin tocar la base")
  void mapeoConHabitacionInexistenteSeRechaza() throws Exception {
    mvc.perform(post("/api/admin/integraciones/mapeos").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("canal", "BOOKING", "roomId", 9_999_999,
          "externalId", "BOOKING-NO-" + System.nanoTime()))))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.error").exists());
  }

  @Test
  @DisplayName("un identificador externo no puede apuntar a dos recursos del mismo canal")
  void mapeoDuplicadoSeRechaza() throws Exception {
    long primera = crearHabitacion("DUP1-" + System.nanoTime(), "H-" + System.nanoTime());
    long segunda = crearHabitacion("DUP2-" + System.nanoTime(), "H-" + System.nanoTime());
    String externo = "BOOKING-DUP-" + System.nanoTime();
    mapear(Map.of("canal", "BOOKING", "roomId", primera, "externalId", externo));

    mvc.perform(post("/api/admin/integraciones/mapeos").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("canal", "BOOKING", "roomId", segunda, "externalId", externo))))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.error").exists());
  }

  @Test
  @DisplayName("un mapeo eliminado deja de publicarse en el panel")
  void eliminarMapeoLoRetiraDelPanel() throws Exception {
    long habitacion = crearHabitacion("DEL-" + System.nanoTime(), "H-" + System.nanoTime());
    String externo = "BOOKING-DEL-" + System.nanoTime();
    long mapeo = JSON.readTree(mapear(Map.of("canal", "BOOKING", "roomId", habitacion,
      "externalId", externo))).get("id").asLong();

    mvc.perform(post("/api/admin/integraciones/mapeos/" + mapeo + "/eliminar").with(ADMIN).with(csrf()))
      .andExpect(status().isOk());

    String panel = mvc.perform(get("/api/admin/integraciones").with(ADMIN))
      .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertTrue(!panel.contains(externo), "el identificador retirado no puede seguir ofreciéndose");
  }

  @Test
  @DisplayName("eliminar un mapeo inexistente responde 404, no éxito")
  void eliminarMapeoInexistenteEs404() throws Exception {
    mvc.perform(post("/api/admin/integraciones/mapeos/999999/eliminar").with(ADMIN).with(csrf()))
      .andExpect(status().isNotFound());
  }

  private long crearHabitacion(String codigoTipo, String codigoHabitacion) throws Exception {
    String tipo = mvc.perform(post("/api/admin/tipos").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("codigo", codigoTipo, "nombre", "Tipo " + codigoTipo,
          "capacidadMax", 2))))
      .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    long tipoId = JSON.readTree(tipo).get("id").asLong();
    String habitacion = mvc.perform(post("/api/admin/habitaciones").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("codigo", codigoHabitacion, "roomTypeId", tipoId,
          "nombre", "Habitación " + codigoHabitacion))))
      .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    return JSON.readTree(habitacion).get("id").asLong();
  }

  private String mapear(Map<String, ?> cuerpo) throws Exception {
    return mvc.perform(post("/api/admin/integraciones/mapeos").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(cuerpo)))
      .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
  }
}