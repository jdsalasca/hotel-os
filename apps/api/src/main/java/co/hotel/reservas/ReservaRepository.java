package co.hotel.reservas;

import java.util.*;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReservaRepository {
  private final JdbcTemplate jdbc;
  public ReservaRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public Map<String, Object> porCodigo(String codigo) {
    try {
      return jdbc.queryForMap("SELECT codigo,email,nombre,llegada,salida,huespedes,estado,origen FROM reservations WHERE codigo=?", codigo);
    } catch (EmptyResultDataAccessException e) { return null; }
  }

  public List<Map<String, Object>> listar(int limit) {
    return jdbc.queryForList("SELECT codigo,email,nombre,llegada,salida,huespedes,estado,origen FROM reservations ORDER BY id DESC LIMIT ?", Math.min(limit, 200));
  }
}
