package co.hotel.panel;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * El panel dice lo que falta por configurar: con todo vacío, nada hecho; sembrando
 * inventario, hotel ubicado y un lugar, todo en verde. Sin sesión es 401.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PreparacionTest {

  private static final Path DB = crearBase();
  private static final Path RESPALDOS = crearDir();
  private static final ObjectMapper JSON = new ObjectMapper();

  private static Path crearDir() {
    try {
      return Files.createTempDirectory("hotel-resp-");
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static Path crearBase() {
    try {
      Path p = Files.createTempFile("hotel-prep-", ".sqlite3");
      Files.delete(p);
      return p;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @DynamicPropertySource
  static void propiedades(DynamicPropertyRegistry reg) {
    reg.add("hotel.jdbc-path", () -> DB.toAbsolutePath().toString());
    reg.add("hotel.respaldos-dir", () -> RESPALDOS.toAbsolutePath().toString());
  }

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  private Map<String, Boolean> estado() throws Exception {
    var admin = user("admin@hotel.test").roles("ADMIN");
    String cuerpo = mvc.perform(get("/api/admin/preparacion").with(admin))
      .andExpect(status().isOk())
      .andReturn().getResponse().getContentAsString();
    var items = JSON.readTree(cuerpo).get("items");
    var mapa = new java.util.LinkedHashMap<String, Boolean>();
    for (var item : items) {
      mapa.put(item.get("clave").asText(), item.get("hecho").asBoolean());
    }
    return mapa;
  }

  @Test
  @DisplayName("vacío todo pendiente, sembrado todo hecho, sin sesión 401")
  void checklistReflejaRealidad() throws Exception {
    var vacio = estado();
    assertEquals(6, vacio.size());
    assertTrue(vacio.values().stream().noneMatch(Boolean::booleanValue));

    jdbc.update("INSERT INTO room_types(codigo,nombre,capacidad_max) VALUES('P','P',2)");
    long tipo = jdbc.queryForObject("SELECT id FROM room_types WHERE codigo='P'", Long.class);
    jdbc.update("INSERT INTO rooms(codigo,estado,nombre,room_type_id)"
      + " VALUES('101','ACTIVA','Habitación 101',?)", tipo);
    jdbc.update("INSERT INTO rate_plans(codigo,nombre,moneda,activo,descuento_pct)"
      + " VALUES('STD','Estándar','COP',1,0)");
    long plan = jdbc.queryForObject("SELECT id FROM rate_plans WHERE codigo='STD'", Long.class);
    jdbc.update("INSERT INTO rates(rate_plan_id,room_type_id,fecha,precio_cents)"
      + " VALUES(?,?,date('now','+1 day'),150000)", plan, tipo);
    jdbc.update("INSERT INTO hotel_config(clave,valor,actualizado_en) VALUES"
      + "('nombre','Hotel Test',datetime('now')),"
      + "('latitud','5.65',datetime('now')),"
      + "('longitud','-73.52',datetime('now'))");
    jdbc.update("INSERT INTO lugares_interes(nombre,descripcion,latitud,longitud,activo,creado_en)"
      + " VALUES('Plaza','','5.6','-73.5',1,datetime('now'))");
    Files.write(RESPALDOS.resolve("hotel-20300101T000000Z.sqlite3"), new byte[] { 1 });

    var lleno = estado();
    assertTrue(lleno.values().stream().allMatch(Boolean::booleanValue));

    mvc.perform(get("/api/admin/preparacion").with(csrf()))
      .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("el respaldo cuenta si es reciente y avisa si se enfría")
  void respaldoReciente() throws Exception {
    try (var archivos = Files.list(RESPALDOS)) {
      for (Path p : archivos.toList()) Files.deleteIfExists(p);
    }
    assertEquals(false, hechoRespaldo());

    Path fresco = RESPALDOS.resolve("hotel-20300101T000000Z.sqlite3");
    Files.write(fresco, new byte[] { 1, 2, 3 });
    assertEquals(true, hechoRespaldo());

    Files.delete(fresco);
    Path viejo = RESPALDOS.resolve("hotel-20200101T000000Z.sqlite3");
    Files.write(viejo, new byte[] { 1, 2, 3 });
    Files.setLastModifiedTime(viejo,
      java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() - 40L * 3_600_000));
    assertEquals(false, hechoRespaldo());
    Files.deleteIfExists(viejo);
  }

  private Boolean hechoRespaldo() throws Exception {
    var admin = user("admin@hotel.test").roles("ADMIN");
    String cuerpo = mvc.perform(get("/api/admin/preparacion").with(admin))
      .andExpect(status().isOk())
      .andReturn().getResponse().getContentAsString();
    for (var item : JSON.readTree(cuerpo).get("items")) {
      if ("respaldo".equals(item.get("clave").asText())) return item.get("hecho").asBoolean();
    }
    throw new IllegalStateException("la checklist no trae el punto de respaldo");
  }
}
