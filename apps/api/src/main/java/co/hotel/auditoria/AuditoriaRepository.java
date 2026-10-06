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
    jdbc.update("INSERT INTO reservation_history(reservation_id,estado_ant,estado_nuevo,actor,en) VALUES(?,?,?,?,?)",
      reservationId, estadoAnterior, estadoNuevo, actor, LocalDateTime.now().toString());
  }

  public List<Map<String, Object>> historialDe(long reservationId) {
    return jdbc.queryForList("SELECT estado_ant,estado_nuevo,actor,en FROM reservation_history "
      + "WHERE reservation_id=? ORDER BY id", reservationId);
  }
}