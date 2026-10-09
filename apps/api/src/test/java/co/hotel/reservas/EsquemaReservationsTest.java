package co.hotel.reservas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * La tabla que el código da por hecha, comprobada contra la que hay.
 *
 * Existe porque V6 reconstruyó `reservations` para cambiar la clave de idempotencia y en la copia
 * perdió RECHAZADA del CHECK; después V8 volvió a reconstruirla y sin querer se dejó `usuario_id`,
 * que había añadido V7. Los dos fallos los encontraron seis tests, por suerte, pero los encontraron
 * de rebote. Aquí se miran las columnas directamente y fallan en el sitio que explica el motivo.
 */
@SpringBootTest
@DisplayName("el esquema de reservations es el que espera el código")
class EsquemaReservationsTest {

  private static final java.nio.file.Path DB = crearBase();

  private static java.nio.file.Path crearBase() {
    try {
      return java.nio.file.Files.createTempFile("esquema", ".db");
    } catch (java.io.IOException e) {
      throw new IllegalStateException("no se pudo crear la base temporal", e);
    }
  }

  @DynamicPropertySource
  static void propiedades(DynamicPropertyRegistry reg) {
    reg.add("hotel.jdbc-path", () -> DB.toAbsolutePath().toString());
  }

  @Autowired JdbcTemplate jdbc;

  private List<String> columnas() {
    return jdbc.query("PRAGMA table_info(reservations)", (rs, n) -> rs.getString("name"));
  }

  @Test
  @DisplayName("no le falta ninguna columna, incluidas las que añadieron migraciones posteriores")
  void tieneTodasLasColumnas() {
    var esperadas = List.of("id", "codigo", "email", "nombre", "llegada", "salida", "huespedes",
      "estado", "origen", "idempotencia", "creado_en", "total_cents", "moneda", "rate_plan_id", "usuario_id");
    assertTrue(columnas().containsAll(esperadas),
      "faltan: " + esperadas.stream().filter(c -> !columnas().contains(c)).toList()
        + ". Una migración que reconstruye la tabla tiene que copiar TODAS las columnas.");
  }

  @Test
  @DisplayName("el enum y el CHECK de la base dicen lo mismo")
  void losEstadosCoinciden() {
    var enEnum = java.util.Arrays.stream(EstadoReserva.values()).map(Enum::name).sorted().toList();
    var sql = jdbc.queryForObject(
      "SELECT sql FROM sqlite_master WHERE type='table' AND name='reservations'", String.class);
    for (var estado : enEnum) {
      assertTrue(sql.contains("'" + estado + "'"),
        estado + " está en el enum pero no en el CHECK de la base: rechazarla sería un 500");
    }
  }

  @Test
  @DisplayName("todos los estados se pueden guardar de verdad")
  void todosLosEstadosSeGuardan() {
    for (var estado : EstadoReserva.values()) {
      jdbc.update("INSERT INTO reservations(codigo,email,nombre,llegada,salida,huespedes,estado,origen,"
        + " idempotencia,creado_en) VALUES(?,?,?,?,?,?,?,?,?,?)",
        "H-ESQ" + estado.name(), "esquema@example.com", null, "2036-01-01", "2036-01-02", 1,
        estado.name(), "WEB", "k-esq-" + estado.name(), "2036-01-01");
    }
    assertEquals(EstadoReserva.values().length,
      jdbc.queryForObject("SELECT count(*) FROM reservations", Integer.class));
  }
}
