package co.hotel.pagos;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** SQL del libro de abonos. Sin JPA, SQL explícito como el resto del proyecto. */
@Repository
public class PagosRepository {
  private final JdbcTemplate jdbc;

  public PagosRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public long insertar(long reservaId, long montoCents, String moneda, String concepto, String actor,
      String creadoEn) {
    // GeneratedKeyHolder y no last_insert_rowid(): esa función habla de la conexión actual y el
    // pool puede dar otra para la siguiente consulta, devolviendo el id de otro abono.
    var llave = new org.springframework.jdbc.support.GeneratedKeyHolder();
    jdbc.update(conexion -> {
      var ps = conexion.prepareStatement("INSERT INTO pagos(reservation_id,monto_cents,moneda,"
        + "concepto,actor,creado_en) VALUES(?,?,?,?,?,?)", new String[] { "id" });
      ps.setLong(1, reservaId);
      ps.setLong(2, montoCents);
      ps.setString(3, moneda);
      ps.setString(4, concepto);
      ps.setString(5, actor);
      ps.setString(6, creadoEn);
      return ps;
    }, llave);
    Number id = llave.getKey();
    if (id == null) throw new IllegalStateException("el abono no devolvió id");
    return id.longValue();
  }

  public Optional<Long> reservaDe(long abonoId) {
    try {
      return Optional.ofNullable(jdbc.queryForObject("SELECT reservation_id FROM pagos WHERE id=?",
        Long.class, abonoId));
    } catch (EmptyResultDataAccessException e) {
      return Optional.empty();
    }
  }

  public boolean anulado(long abonoId) {
    Boolean sí = jdbc.queryForObject("SELECT anulado_en IS NOT NULL FROM pagos WHERE id=?",
      Boolean.class, abonoId);
    return Boolean.TRUE.equals(sí);
  }

  /** Anular marca, no borra: el dinero que entró y salió queda contado. */
  public void anular(long abonoId, String actor, String en) {
    jdbc.update("UPDATE pagos SET anulado_en=?, anulado_por=? WHERE id=?", en, actor, abonoId);
  }

  public long abonadoVigente(long reservaId) {
    Long n = jdbc.queryForObject("SELECT COALESCE(SUM(monto_cents),0) FROM pagos "
      + "WHERE reservation_id=? AND anulado_en IS NULL", Long.class, reservaId);
    return n == null ? 0L : n;
  }

  public List<Map<String, Object>> movimientos(long reservaId) {
    return jdbc.queryForList("SELECT id,monto_cents,moneda,concepto,actor,creado_en,"
      + "anulado_en,anulado_por FROM pagos WHERE reservation_id=? ORDER BY id", reservaId);
  }
}
