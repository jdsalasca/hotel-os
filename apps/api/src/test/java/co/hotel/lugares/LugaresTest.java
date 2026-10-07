package co.hotel.lugares;

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
 * Mapa administrable: el hotel fija su punto y sus sitios; la web solo ve activos. Coordenadas
 * imposibles se rechazan con 400 y la escritura exige ADMIN.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LugaresTest {

  private static final Path DB = crearBase();
  private static final ObjectMapper JSON = new ObjectMapper();

  private static Path crearBase() {
    try {
      Path p = Files.createTempFile("hotel-lug-", ".sqlite3");
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

  @Test
  @DisplayName("sin ubicar el hotel no hay punto, y el CRUD valida coordenadas y rol")
  void crudConValidacion() throws Exception {
    var admin = user("admin@hotel.test").roles("ADMIN");

    mvc.perform(get("/api/lugares"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.hotel.ubicado").value(false));

    jdbc.update("INSERT INTO hotel_config(clave,valor,actualizado_en) VALUES"
      + "('latitud','5.65',datetime('now')),('longitud','-73.52',datetime('now'))");

    String creado = mvc.perform(post("/api/admin/lugares").with(admin).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo(Map.of("nombre", "Plaza Mayor", "descripcion", "La plaza",
          "latitud", 5.635, "longitud", -73.525))))
      .andExpect(status().isCreated())
      .andReturn().getResponse().getContentAsString();
    assertTrue(JSON.readTree(creado).has("id"));

    mvc.perform(post("/api/admin/lugares").with(admin).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo(Map.of("nombre", "X", "latitud", 500, "longitud", 0))))
      .andExpect(status().isBadRequest());

    mvc.perform(post("/api/admin/lugares").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo(Map.of("nombre", "Plaza", "latitud", 5.6, "longitud", -73.5))))
      .andExpect(status().isUnauthorized());

    mvc.perform(get("/api/lugares"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.hotel.ubicado").value(true))
      .andExpect(jsonPath("$.hotel.latitud").value(5.65))
      .andExpect(jsonPath("$.lugares.length()").value(1));
  }
}
