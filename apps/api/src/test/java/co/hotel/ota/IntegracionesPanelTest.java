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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

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
}