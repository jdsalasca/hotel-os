package co.hotel.auditoria;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Persistencia del rastro de cambios de una reserva. */
@Repository
public class AuditoriaRepository {
  private final JdbcTemplate jdbc;

  public AuditoriaRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public void registrarEstado(long reservationId, String estadoAnterior, String estadoNuevo, String actor) {
    registrar(reservationId, estadoAnterior, estadoNuevo, null, actor);
  }

  /** Movimiento con detalle libre ("habitación 101 → 102"): el estado no cambió, el qué sí. */
  public void registrarMovimiento(long reservationId, String estadoActual, String detalle, String actor) {
    registrar(reservationId, estadoActual, estadoActual, detalle, actor);
  }

  private void registrar(long reservationId, String estadoAnterior, String estadoNuevo, String detalle,
      String actor) {
    jdbc.update("INSERT INTO reservation_history(reservation_id,estado_ant,estado_nuevo,detalle,actor,en)"
        + " VALUES(?,?,?,?,?,?)",
      reservationId, estadoAnterior, estadoNuevo, detalle, actor, LocalDateTime.now().toString());
  }

  public List<Map<String, Object>> historialDe(long reservationId) {
    return jdbc.queryForList("SELECT estado_ant,estado_nuevo,detalle,actor,en FROM reservation_history "
      + "WHERE reservation_id=? ORDER BY id", reservationId);
  }
}