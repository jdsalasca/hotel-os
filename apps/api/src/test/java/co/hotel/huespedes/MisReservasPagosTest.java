package co.hotel.huespedes;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * "Mis reservas" muestra lo abonado y lo pendiente de cada reserva. El saldo se calcula al
 * leer (total menos abonos vigentes): el anublado no suma y sin precio acordado no hay
 * pendiente que mostrar.
 */
@SpringBootTest
class MisReservasPagosTest {

  private static final Path DB = crearBase();

  private static Path crearBase() {
    try {
      Path p = Files.createTempFile("hotel-pagos-", ".sqlite3");
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

  @Autowired JdbcTemplate jdbc;
  @Autowired ReservaServiceHuesped servicio;

  private long reservaDe(long usuarioId, String codigo, Long total) {
    jdbc.update("INSERT INTO reservations(codigo,email,nombre,llegada,salida,huespedes,estado,"
      + "origen,idempotencia,creado_en,total_cents,moneda,usuario_id)"
      + " VALUES(?, 'pago@hotel.test','Pagos','2030-02-10','2030-02-12',1,'CONFIRMADA','WEB',"
      + "?,datetime('now'),?, 'COP',?)",
      codigo, "idem-" + codigo, total, usuarioId);
    return jdbc.queryForObject("SELECT id FROM reservations WHERE codigo=?", Long.class, codigo);
  }

  private void abono(long reservaId, long monto, boolean anulado) {
    jdbc.update("INSERT INTO pagos(reservation_id,monto_cents,moneda,concepto,actor,creado_en,"
      + (anulado ? "anulado_en,anulado_por" : "anulado_en") + ") VALUES(?,?,'COP','test','caja',"
      + "datetime('now')" + (anulado ? ",datetime('now'),'caja'" : ",NULL") + ")",
      reservaId, monto);
  }

  @Test
  @DisplayName("cada reserva trae abonado vigente y pendiente")
  void traeAbonadoYPendiente() {
    jdbc.update("INSERT INTO usuarios(google_sub,email,nombre,creado_en)"
      + " VALUES('sub-pagos','pago@hotel.test','Pagos','2030-01-01')");
    long uid = jdbc.queryForObject("SELECT id FROM usuarios WHERE email='pago@hotel.test'",
      Long.class);

    long r1 = reservaDe(uid, "PAGO01", 200000L);
    abono(r1, 80000L, false);
    abono(r1, 50000L, false);
    abono(r1, 30000L, true);

    long r2 = reservaDe(uid, "PAGO02", 100000L);

    reservaDe(uid, "PAGO03", null);

    var filas = servicio.de(uid);
    assertEquals(3, filas.size());

    var f1 = filas.stream().filter(f -> "PAGO01".equals(f.get("codigo"))).findFirst().orElseThrow();
    assertEquals(130000L, ((Number) f1.get("abonado_cents")).longValue());
    assertEquals(70000L, ((Number) f1.get("pendiente_cents")).longValue());

    var f2 = filas.stream().filter(f -> "PAGO02".equals(f.get("codigo"))).findFirst().orElseThrow();
    assertEquals(0L, ((Number) f2.get("abonado_cents")).longValue());
    assertEquals(100000L, ((Number) f2.get("pendiente_cents")).longValue());

    var f3 = filas.stream().filter(f -> "PAGO03".equals(f.get("codigo"))).findFirst().orElseThrow();
    assertNull(f3.get("pendiente_cents"));
  }
}
