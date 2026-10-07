package co.hotel.amenidades;

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
 * Servicios por tipo: catálogo sembrado, lectura pública y marca del panel que reemplaza,
 * no que acumula. Los ids inventados se rechazan antes de guardar.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AmenidadesTest {

  private static final Path DB = crearBase();
  private static final ObjectMapper JSON = new ObjectMapper();

  private static Path crearBase() {
    try {
      Path p = Files.createTempFile("hotel-amen-", ".sqlite3");
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

  private long tipo() {
    jdbc.update("INSERT INTO room_types(codigo,nombre,capacidad_max) VALUES('AMEN','Amenable',2)");
    return jdbc.queryForObject("SELECT id FROM room_types WHERE codigo='AMEN'", Long.class);
  }

  private String cuerpo(Object o) {
    try {
      return JSON.writeValueAsString(o);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  @DisplayName("el catálogo viene sembrado y es público")
  void catalogoSembrado() throws Exception {
    mvc.perform(get("/api/amenidades"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.amenidades.length()").value(10));
  }

  @Test
  @DisplayName("la marca reemplaza, rechaza inventos y exige tipo real")
  void marcaReemplazaYValida() throws Exception {
    long tipoId = tipo();
    var admin = user("admin@hotel.test").roles("ADMIN");

    mvc.perform(put("/api/admin/tipos/" + tipoId + "/amenidades").with(admin).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo(Map.of("ids", java.util.List.of(1, 2, 3)))))
      .andExpect(status().isOk());

    mvc.perform(get("/api/amenidades/por-tipo?ids=" + tipoId))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.porTipo['" + tipoId + "'].length()").value(3));

    mvc.perform(put("/api/admin/tipos/" + tipoId + "/amenidades").with(admin).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo(Map.of("ids", java.util.List.of(2)))))
      .andExpect(status().isOk());

    Integer n = jdbc.queryForObject(
      "SELECT COUNT(*) FROM room_type_amenidades WHERE room_type_id=?", Integer.class, tipoId);
    assertEquals(1, n);

    mvc.perform(put("/api/admin/tipos/" + tipoId + "/amenidades").with(admin).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo(Map.of("ids", java.util.List.of(9999)))))
      .andExpect(status().isBadRequest());

    mvc.perform(put("/api/admin/tipos/999999/amenidades").with(admin).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo(Map.of("ids", java.util.List.of(1)))))
      .andExpect(status().isNotFound());

    mvc.perform(put("/api/admin/tipos/" + tipoId + "/amenidades").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo(Map.of("ids", java.util.List.of(1)))))
      .andExpect(status().isUnauthorized());
  }
}
