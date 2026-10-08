package co.hotel.chat;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Hilo de mensajes por reserva. `visto` lo marca quien lee, no quien escribe. */
@Repository
public class ChatRepository {
  private final JdbcTemplate jdbc;

  public ChatRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public record Mensaje(long id, String autor, String texto, String creadoEn, boolean visto) {}

  private static Mensaje fila(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
    return new Mensaje(rs.getLong("id"), rs.getString("autor"), rs.getString("texto"),
      rs.getString("creado_en"), rs.getInt("visto") == 1);
  }

  public List<Mensaje> hilo(long reservationId) {
    return jdbc.query("SELECT id,autor,texto,creado_en,visto FROM mensajes"
      + " WHERE reservation_id=? ORDER BY id", ChatRepository::fila, reservationId);
  }

  public long agregar(long reservationId, String autor, String texto, String ahora) {
    jdbc.update("INSERT INTO mensajes(reservation_id,autor,texto,creado_en,visto)"
      + " VALUES(?,?,?, ?,0)", reservationId, autor, texto, ahora);
    Long id = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
    return id == null ? -1 : id;
  }

  /** Al abrir el hilo se marcan como vistos los del otro lado. Devuelve cuántos marcó. */
  public int marcarVistos(long reservationId, String lector) {
    String otro = "HOTEL".equals(lector) ? "HUESPED" : "HOTEL";
    Integer n = jdbc.queryForObject(
      "SELECT COUNT(*) FROM mensajes WHERE reservation_id=? AND autor=? AND visto=0",
      Integer.class, reservationId, otro);
    jdbc.update("UPDATE mensajes SET visto=1 WHERE reservation_id=? AND autor=? AND visto=0",
      reservationId, otro);
    return n == null ? 0 : n;
  }

  public int nuevosParaHuesped(long usuarioId) {
    Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM mensajes m "
      + "JOIN reservations r ON r.id=m.reservation_id "
      + "WHERE r.usuario_id=? AND m.autor='HOTEL' AND m.visto=0", Integer.class, usuarioId);
    return n == null ? 0 : n;
  }

  public java.util.Map<String, Integer> nuevosPorReservaDeHuesped(long usuarioId) {
    var mapa = new java.util.LinkedHashMap<String, Integer>();
    jdbc.query("SELECT r.codigo, COUNT(*) AS n FROM mensajes m "
      + "JOIN reservations r ON r.id=m.reservation_id "
      + "WHERE r.usuario_id=? AND m.autor='HOTEL' AND m.visto=0 GROUP BY r.codigo",
      rs -> { mapa.put(rs.getString("codigo"), rs.getInt("n")); }, usuarioId);
    return mapa;
  }

  public int nuevosParaHotel() {
    Integer n = jdbc.queryForObject(
      "SELECT COUNT(*) FROM mensajes WHERE autor='HUESPED' AND visto=0", Integer.class);
    return n == null ? 0 : n;
  }

  /** Mensajes de la última hora en esa reserva, de ambos lados: el tope antispam. */
  public int recientes(long reservationId) {
    Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM mensajes WHERE reservation_id=?"
      + " AND creado_en >= datetime('now','-1 hour')", Integer.class, reservationId);
    return n == null ? 0 : n;
  }

  public Long reservaDe(String codigo) {
    try {
      return jdbc.queryForObject("SELECT id FROM reservations WHERE codigo=?", Long.class, codigo);
    } catch (Exception e) {
      return null;
    }
  }

  public Long duenaDe(String codigo) {
    try {
      return jdbc.queryForObject("SELECT usuario_id FROM reservations WHERE codigo=?", Long.class,
        codigo);
    } catch (Exception e) {
      return null;
    }
  }
}
