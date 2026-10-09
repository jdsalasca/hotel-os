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

  /**
   * La base es la misma para los tres métodos (static final), así que el hotel que ubica uno
   * se le queda a los siguientes: `crudConValidacion` empezaba(){false} y se encontraba con
   * {true} por culpa del anterior. Sin este borrado, probar "sin hotel ubicado" era imposible.
   */
  @org.junit.jupiter.api.BeforeEach
  void dejaLaBaseLimpia() {
    jdbc.update("DELETE FROM hotel_config WHERE clave IN ('latitud','longitud')");
    jdbc.update("DELETE FROM lugares_interes");
  }

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

  @Test
  @DisplayName("cada lugar dice a que distancia esta del hotel")
  void lugaresConDistancia() throws Exception {
    var admin = user("admin@hotel.test").roles("ADMIN");
    jdbc.update("INSERT INTO hotel_config(clave,valor,actualizado_en) VALUES"
      + "('latitud','5.65',datetime('now')),('longitud','-73.52',datetime('now'))");

    // Al lado: menos de 300 m. Lejos (Santiago de Chile): miles de km.
    mvc.perform(post("/api/admin/lugares").with(admin).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo(Map.of("nombre", "Al lado", "latitud", 5.648, "longitud", -73.520))))
      .andExpect(status().isCreated());
    mvc.perform(post("/api/admin/lugares").with(admin).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo(Map.of("nombre", "Lejos", "latitud", -33.45, "longitud", -70.67))))
      .andExpect(status().isCreated());

    mvc.perform(get("/api/lugares"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.lugares.length()").value(2))
      .andExpect(jsonPath("$.lugares[?(@.nombre=='Al lado')].metros").isNotEmpty())
      .andExpect(jsonPath("$.lugares[?(@.nombre=='Lejos')].metros").isNotEmpty());

    var raiz = JSON.readTree(mvc.perform(get("/api/lugares")).andReturn().getResponse()
      .getContentAsString());
    long alLado = -1, lejos = -1;
    for (var l : raiz.get("lugares")) {
      if (l.get("nombre").asText().equals("Al lado")) alLado = l.get("metros").asLong();
      if (l.get("nombre").asText().equals("Lejos")) lejos = l.get("metros").asLong();
    }
    assertTrue(alLado >= 0 && alLado < 300, "lo que esta al lado debe salir a menos de 300 m, no " + alLado);
    assertTrue(lejos > 1_000_000, "lo lejano sale en km, no " + lejos + " m");
  }

  @Test
  @DisplayName("sin hotel ubicado no se inventa ninguna distancia")
  void sinHotelNoHayDistancias() throws Exception {
    var admin = user("admin@hotel.test").roles("ADMIN");
    mvc.perform(post("/api/admin/lugares").with(admin).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo(Map.of("nombre", "Sitio", "latitud", 5.6, "longitud", -73.5))))
      .andExpect(status().isCreated());

    mvc.perform(get("/api/lugares"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.hotel.ubicado").value(false))
      .andExpect(jsonPath("$.lugares[0].metros").doesNotExist());
  }
}
