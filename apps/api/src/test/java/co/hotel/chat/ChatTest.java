package co.hotel.chat;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Chat por reserva: el huésped solo en las suyas, el hotel en todas, leer marca vistos y
 * los contadores solo cuentan lo del otro lado. El texto vacío o kilométrico se rechaza.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ChatTest {

  private static final Path DB = crearBase();
  private static final ObjectMapper JSON = new ObjectMapper();

  private static Path crearBase() {
    try {
      Path p = Files.createTempFile("hotel-chat-", ".sqlite3");
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

  private String cuerpo(Object o) {
    try {
      return JSON.writeValueAsString(o);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private void sembrar() {
    if (jdbc.queryForObject("SELECT COUNT(*) FROM reservations WHERE codigo='CHAT01'",
      Integer.class) == 0) {
      jdbc.update("INSERT INTO usuarios(google_sub,email,nombre,creado_en)"
        + " VALUES('sub-chat','chat@hotel.test','Chat','2030-01-01')");
      jdbc.update("INSERT INTO usuarios(google_sub,email,nombre,creado_en)"
        + " VALUES('sub-otro','otro@hotel.test','Otro','2030-01-01')");
      long uid = jdbc.queryForObject("SELECT id FROM usuarios WHERE email='chat@hotel.test'",
        Long.class);
      jdbc.update("INSERT INTO reservations(codigo,email,nombre,llegada,salida,huespedes,estado,"
        + "origen,idempotencia,creado_en,usuario_id)"
        + " VALUES('CHAT01','chat@hotel.test','Chat','2030-03-10','2030-03-12',1,'CONFIRMADA',"
        + "'WEB','idem-chat01',datetime('now'),?)", uid);
    }
  }

  @Test
  @DisplayName("más de 30 mensajes por hora y reserva se frena con 429")
  void topePorHoraYReserva() throws Exception {
    sembrar();
    var huesped = user("chat@hotel.test").roles("HUESPED");
    Long reservaId = jdbc.queryForObject("SELECT id FROM reservations WHERE codigo='CHAT01'",
      Long.class);
    for (int i = 0; i < 30; i++) {
      jdbc.update("INSERT INTO mensajes(reservation_id,autor,texto,creado_en,visto)"
        + " VALUES(?,'HUESPED',? ,datetime('now'),1)", reservaId, "relleno " + i);
    }
    mvc.perform(post("/api/mis-reservas/CHAT01/mensajes").with(huesped).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo(Map.of("texto", "uno más"))))
      .andExpect(status().isTooManyRequests())
      .andExpect(jsonPath("$.error", org.hamcrest.Matchers.containsString("minuto")));
  }

  @Test
  @DisplayName("conversación de ida y vuelta con vistos y contadores")
  void conversacionCompleta() throws Exception {
    sembrar();
    var huesped = user("chat@hotel.test").roles("HUESPED");
    var otro = user("otro@hotel.test").roles("HUESPED");
    var admin = user("admin@hotel.test").roles("ADMIN");

    mvc.perform(post("/api/mis-reservas/CHAT01/mensajes").with(huesped).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo(Map.of("texto", "Hola, ¿tienen cuna?"))))
      .andExpect(status().isCreated());

    mvc.perform(get("/api/mis-reservas/CHAT01/mensajes").with(otro))
      .andExpect(status().isNotFound());

    mvc.perform(get("/api/admin/reservas/CHAT01/mensajes").with(admin))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.mensajes.length()").value(1));

    mvc.perform(get("/api/admin/mensajes/nuevos").with(admin))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.nuevos").value(0));

    mvc.perform(post("/api/admin/reservas/CHAT01/mensajes").with(admin).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo(Map.of("texto", "Sí, la dejamos lista"))))
      .andExpect(status().isCreated());

    mvc.perform(get("/api/mis-reservas/mensajes/nuevos").with(huesped))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.nuevos").value(1))
      .andExpect(jsonPath("$.porReserva.CHAT01").value(1));

    mvc.perform(get("/api/mis-reservas/CHAT01/mensajes").with(huesped))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.mensajes.length()").value(2));

    mvc.perform(get("/api/mis-reservas/mensajes/nuevos").with(huesped))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.nuevos").value(0));

    mvc.perform(post("/api/mis-reservas/CHAT01/mensajes").with(huesped).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo(Map.of("texto", "   "))))
      .andExpect(status().isBadRequest());

    mvc.perform(get("/api/mis-reservas/CHAT01/mensajes"))
      .andExpect(status().isUnauthorized());
  }
}
