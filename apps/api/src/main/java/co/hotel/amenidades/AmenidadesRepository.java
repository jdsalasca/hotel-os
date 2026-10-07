package co.hotel.amenidades;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Catálogo cerrado de servicios y su marca por tipo de habitación. Cerrado a propósito: con
 * texto libre, "wifi", "WiFi" y "WIFI" acaban siendo tres servicios distintos en la web pública.
 */
@Repository
public class AmenidadesRepository {
  private final JdbcTemplate jdbc;

  public AmenidadesRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public record Amenidad(long id, String codigo, String nombre) {}

  public List<Amenidad> catalogo() {
    return jdbc.query("SELECT id,codigo,nombre FROM amenidades ORDER BY id",
      (rs, n) -> new Amenidad(rs.getLong("id"), rs.getString("codigo"), rs.getString("nombre")));
  }

  public boolean existeTipo(long tipoId) {
    Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM room_types WHERE id=?", Integer.class,
      tipoId);
    return n != null && n > 0;
  }

  public List<Amenidad> deTipo(long tipoId) {
    return jdbc.query("SELECT a.id,a.codigo,a.nombre FROM amenidades a "
      + "JOIN room_type_amenidades r ON r.amenidad_id=a.id WHERE r.room_type_id=? ORDER BY a.id",
      (rs, n) -> new Amenidad(rs.getLong("id"), rs.getString("codigo"), rs.getString("nombre")),
      tipoId);
  }

  /** Reemplaza la marca completa del tipo. Los ids desconocidos se rechazan antes, aquí no. */
  public void fijarParaTipo(long tipoId, List<Long> amenidadIds) {
    jdbc.update("DELETE FROM room_type_amenidades WHERE room_type_id=?", tipoId);
    for (Long amenidadId : amenidadIds) {
      jdbc.update("INSERT INTO room_type_amenidades(room_type_id,amenidad_id) VALUES(?,?)",
        tipoId, amenidadId);
    }
  }

  public int contarConIds(List<Long> ids) {
    if (ids.isEmpty()) return 0;
    String marcas = String.join(",", ids.stream().map(i -> "?").toList());
    Integer n = jdbc.queryForObject(
      "SELECT COUNT(*) FROM amenidades WHERE id IN (" + marcas + ")", Integer.class,
      ids.toArray());
    return n == null ? 0 : n;
  }

  public Map<Long, List<Amenidad>> porTipos(List<Long> tipoIds) {
    var mapa = new java.util.LinkedHashMap<Long, List<Amenidad>>();
    for (Long tipoId : tipoIds) {
      mapa.put(tipoId, deTipo(tipoId));
    }
    return mapa;
  }
}
