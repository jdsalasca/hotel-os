package co.hotel.hotel;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.hotel.pruebas.HotelDePrueba;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Un hotel sin nada que vender no finge estar lleno: /api/hotel/venta lo dice para
 * que la web muestre "aún no publica" en vez de un calendario de Llenos. Hace falta
 * habitación activa Y tarifa: con habitaciones pero sin precio tampoco hay qué vender.
 */
@SpringBootTest
@AutoConfigureMockMvc
class HotelVentaTest {

  private static final Path DB = crearBase();

  private static Path crearBase() {
    try {
      Path p = Files.createTempFile("hotel-venta-", ".sqlite3");
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

  // Cada test parte de cero: la base se comparte en la clase y los restos de un test
  // (habitaciones, tarifas) decidirían el resultado del siguiente.
  @BeforeEach
  void limpio() {
    jdbc.update("DELETE FROM rates");
    jdbc.update("DELETE FROM reservations");
    jdbc.update("DELETE FROM rooms");
  }

  @Test
  @DisplayName("sin habitaciones ni tarifas no hay nada a la venta")
  void vacioNoVende() throws Exception {
    mvc.perform(get("/api/hotel/venta"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.a_la_venta").value(false));
  }

  @Test
  @DisplayName("con habitación activa y tarifa sí hay venta")
  void conInventarioVende() throws Exception {
    jdbc.update("INSERT INTO rooms(codigo, estado, nombre) VALUES('101','ACTIVA','Habitación 101')");
    HotelDePrueba.tarifarTodo(jdbc, LocalDate.parse("2030-01-01"), LocalDate.parse("2030-02-01"));
    mvc.perform(get("/api/hotel/venta"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.a_la_venta").value(true));
  }

  @Test
  @DisplayName("habitación sin tarifa tampoco vende")
  void sinTarifaNoVende() throws Exception {
    jdbc.update("INSERT INTO rooms(codigo, estado, nombre) VALUES('102','ACTIVA','Habitación 102')");
    mvc.perform(get("/api/hotel/venta"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.a_la_venta").value(false));
  }
}
