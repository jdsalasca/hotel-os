package co.hotel.huespedes;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Las reservas de un huésped, por su `usuario_id`. */
@Repository
public class ReservaServiceHuesped {
  private final JdbcTemplate jdbc;
  private final UsuariosHuespedRepository usuarios;

  public ReservaServiceHuesped(JdbcTemplate jdbc, UsuariosHuespedRepository usuarios) {
    this.jdbc = jdbc;
    this.usuarios = usuarios;
  }

  /** Id del huésped con ese correo, o null si esa cuenta no existe todavía. */
  public Long idPorEmail(String email) { return usuarios.idPorEmail(email); }

  /**
   * Reservas del usuario, primero las más recientes. Solo por `usuario_id`: el correo coincide en
   * dos cuentas distintas y esas reservas no se mezclan. Cada fila trae lo abonado vigente y lo
   * pendiente (total menos abonos): el saldo no se guarda en la base, se calcula al leer, igual
   * que en el comprobante del panel.
   */
  public List<Map<String, Object>> de(long usuarioId) {
    return jdbc.queryForList("SELECT r.codigo, r.llegada, r.salida, r.huespedes, r.estado, "
      + "r.creado_en, r.total_cents, r.moneda, "
      + "COALESCE((SELECT SUM(p.monto_cents) FROM pagos p "
      + "WHERE p.reservation_id=r.id AND p.anulado_en IS NULL),0) AS abonado_cents, "
      + "CASE WHEN r.total_cents IS NULL THEN NULL "
      + "ELSE r.total_cents - COALESCE((SELECT SUM(p.monto_cents) FROM pagos p "
      + "WHERE p.reservation_id=r.id AND p.anulado_en IS NULL),0) END AS pendiente_cents "
      + "FROM reservations r WHERE r.usuario_id=? ORDER BY r.llegada DESC", usuarioId);
  }

  public boolean tieneAlguna(long usuarioId) {
    Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM reservations WHERE usuario_id=?",
      Integer.class, usuarioId);
    return n != null && n > 0;
  }

  /** Engancha la reserva al usuario que la está haciendo, para que la vea en "Mis reservas". */
  public void vincular(String codigoReserva, long usuarioId) {
    jdbc.update("UPDATE reservations SET usuario_id=? WHERE codigo=? AND usuario_id IS NULL",
      usuarioId, codigoReserva);
  }
}