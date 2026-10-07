package co.hotel.huespedes;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Persistencia de los huéspedes que entran con Google. */
@Repository
public class UsuariosHuespedRepository {
  private final JdbcTemplate jdbc;

  public UsuariosHuespedRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  /**
   * Id por correo, en minúsculas. La comparación sin mayúsculas es la misma que en el login, para
   * que Google y la web traten el correo igual.
   */
  public Long idPorEmail(String email) {
    if (email == null) return null;
    try {
      return jdbc.queryForObject("SELECT id FROM usuarios WHERE lower(email)=lower(?)",
        Long.class, email);
    } catch (org.springframework.dao.EmptyResultDataAccessException e) {
      return null;
    }
  }

  public String nombrePorId(long id) {
    try {
      return jdbc.queryForObject("SELECT nombre FROM usuarios WHERE id=?", String.class, id);
    } catch (org.springframework.dao.EmptyResultDataAccessException e) {
      return "";
    }
  }

  /**
   * Id del huésped con ese identificador de Google, creándolo la primera vez. Se hace en una sola
   * sentencia porque entre "buscar" y "crear" hay una ventana en la que dos peticiones del mismo
   * primer login insertarían dos filas.
   */
  public long porSubOCrear(String googleSub, String email, String nombre) {
    jdbc.update("INSERT INTO usuarios(google_sub,email,nombre,creado_en) VALUES(?,?,?,?) "
      + "ON CONFLICT(google_sub) DO UPDATE SET email=excluded.email, nombre=excluded.nombre",
      googleSub, email, nombre == null ? "" : nombre, ahora());
    return jdbc.queryForObject("SELECT id FROM usuarios WHERE google_sub=?", Long.class, googleSub);
  }

  public Long idPorSub(String googleSub) {
    try {
      return jdbc.queryForObject("SELECT id FROM usuarios WHERE google_sub=?", Long.class, googleSub);
    } catch (org.springframework.dao.EmptyResultDataAccessException e) {
      return null;
    }
  }

  private static String ahora() { return java.time.LocalDateTime.now().toString(); }
}