package co.hotel.pruebas;

import java.time.LocalDate;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Inventario vendible para tests.
 *
 * Desde que la reserva pública exige precio acordado, ningún test puede reservar en una
 * habitación sin tipo ni tarifas: recibiría 409 y el fallo parecería del comportamiento que mide.
 * Este ayudante deja las habitaciones existentes con tipo doble, plan estándar y una tarifa por
 * noche del intervalo, para que cada clase mida lo suyo y no la falta de precio.
 */
public final class HotelDePrueba {

  private HotelDePrueba() {}

  /**
   * Deja vendible todo lo que haya en rooms: tipo doble a las que no tengan, plan estándar y
   * 150.000 por noche en [desde, hasta). Idempotente: se puede llamar en cada @BeforeEach.
   */
  public static void tarifarTodo(JdbcTemplate jdbc, LocalDate desde, LocalDate hasta) {
    long tipo = idOCrear(jdbc, "room_types", "DOBLE-PRUEBA",
      "INSERT INTO room_types(codigo,nombre,capacidad_max) VALUES('DOBLE-PRUEBA','Doble',2)");
    long plan = idOCrear(jdbc, "rate_plans", "STD-PRUEBA",
      "INSERT INTO rate_plans(codigo,nombre,moneda,activo) VALUES('STD-PRUEBA','Estándar','COP',1)");
    jdbc.update("UPDATE rooms SET room_type_id=? WHERE room_type_id IS NULL", tipo);
    for (LocalDate dia = desde; dia.isBefore(hasta); dia = dia.plusDays(1)) {
      jdbc.update("INSERT OR IGNORE INTO rates(rate_plan_id,room_type_id,fecha,precio_cents,cerrado)"
        + " VALUES(?,?,?,150000,0)", plan, tipo, dia.toString());
    }
  }

  private static long idOCrear(JdbcTemplate jdbc, String tabla, String codigo, String insert) {
    try {
      Long id = jdbc.queryForObject("SELECT id FROM " + tabla + " WHERE codigo=?", Long.class, codigo);
      if (id != null) return id;
    } catch (EmptyResultDataAccessException e) {
      // No existe: se crea abajo.
    }
    jdbc.update(insert);
    Long id = jdbc.queryForObject("SELECT id FROM " + tabla + " WHERE codigo=?", Long.class, codigo);
    if (id == null) throw new IllegalStateException("no se pudo crear " + tabla + "/" + codigo);
    return id;
  }
}
