package co.hotel.lugares;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Sitios y experiencias que el hotel recomienda, con su punto en el mapa. */
@Repository
public class LugaresRepository {
  private final JdbcTemplate jdbc;

  public LugaresRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public record Lugar(long id, String nombre, String descripcion, double latitud, double longitud,
                      boolean activo) {}

  private static Lugar fila(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
    return new Lugar(rs.getLong("id"), rs.getString("nombre"), rs.getString("descripcion"),
      rs.getDouble("latitud"), rs.getDouble("longitud"), rs.getInt("activo") == 1);
  }

  public List<Lugar> activos() {
    return jdbc.query("SELECT id,nombre,descripcion,latitud,longitud,activo FROM lugares_interes"
      + " WHERE activo=1 ORDER BY nombre", LugaresRepository::fila);
  }

  public List<Lugar> todos() {
    return jdbc.query("SELECT id,nombre,descripcion,latitud,longitud,activo FROM lugares_interes"
      + " ORDER BY nombre", LugaresRepository::fila);
  }

  public long crear(String nombre, String descripcion, double latitud, double longitud) {
    jdbc.update("INSERT INTO lugares_interes(nombre,descripcion,latitud,longitud,activo,creado_en)"
      + " VALUES(?,?,?, ?,1,datetime('now'))", nombre, descripcion, latitud, longitud);
    Long id = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
    return id == null ? -1 : id;
  }

  public int actualizar(long id, String nombre, String descripcion, double latitud,
                        double longitud, boolean activo) {
    return jdbc.update("UPDATE lugares_interes SET nombre=?, descripcion=?, latitud=?,"
      + " longitud=?, activo=? WHERE id=?", nombre, descripcion, latitud, longitud,
      activo ? 1 : 0, id);
  }

  public int eliminar(long id) {
    return jdbc.update("DELETE FROM lugares_interes WHERE id=?", id);
  }
}
