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

  public record Lugar(long id, String nombre, String descripcion, String categoria,
                      double latitud, double longitud, boolean activo) {}

  private static Lugar fila(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
    return new Lugar(rs.getLong("id"), rs.getString("nombre"), rs.getString("descripcion"),
      rs.getString("categoria"), rs.getDouble("latitud"), rs.getDouble("longitud"),
      rs.getInt("activo") == 1);
  }

  public List<Lugar> activos() {
    return jdbc.query("SELECT id,nombre,descripcion,categoria,latitud,longitud,activo FROM lugares_interes"
      + " WHERE activo=1 ORDER BY nombre", LugaresRepository::fila);
  }

  public List<Lugar> todos() {
    return jdbc.query("SELECT id,nombre,descripcion,categoria,latitud,longitud,activo FROM lugares_interes"
      + " ORDER BY nombre", LugaresRepository::fila);
  }

  public long crear(String nombre, String descripcion, String categoria, double latitud,
                    double longitud) {
    // last_insert_rowid() es por conexión: con pool equivale a otra conexión y devuelve 0.
    // Las llaves generadas viajan con el INSERT y no dependen de la conexión que toque.
    var llaves = new org.springframework.jdbc.support.GeneratedKeyHolder();
    jdbc.update(con -> {
      var ps = con.prepareStatement("INSERT INTO lugares_interes(nombre,descripcion,categoria,"
        + "latitud,longitud,activo,creado_en) VALUES(?,?,?,?,?,1,datetime('now'))",
        java.sql.Statement.RETURN_GENERATED_KEYS);
      ps.setString(1, nombre);
      ps.setString(2, descripcion);
      ps.setString(3, categoria);
      ps.setDouble(4, latitud);
      ps.setDouble(5, longitud);
      return ps;
    }, llaves);
    Number id = llaves.getKey();
    if (id == null) throw new IllegalStateException("no se pudo crear el lugar");
    return id.longValue();
  }

  public int actualizar(long id, String nombre, String descripcion, String categoria,
                        double latitud, double longitud, boolean activo) {
    return jdbc.update("UPDATE lugares_interes SET nombre=?, descripcion=?, categoria=?, latitud=?,"
      + " longitud=?, activo=? WHERE id=?", nombre, descripcion, categoria, latitud, longitud,
      activo ? 1 : 0, id);
  }

  public int eliminar(long id) {
    return jdbc.update("DELETE FROM lugares_interes WHERE id=?", id);
  }
}
