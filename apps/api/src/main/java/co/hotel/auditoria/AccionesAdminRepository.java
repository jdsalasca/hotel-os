package co.hotel.auditoria;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Persistencia del rastro de acciones del panel. */
@Repository
public class AccionesAdminRepository {
  private final JdbcTemplate jdbc;

  public AccionesAdminRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public void registrar(String actor, String metodo, String ruta, int estado) {
    jdbc.update("INSERT INTO admin_actions(actor,metodo,ruta,estado,en) VALUES(?,?,?,?,?)",
      actor, metodo, ruta, estado, LocalDateTime.now().toString());
  }

  /** Las más recientes primero, que es como las lee un hotel: lo último que pasó. */
  public List<Map<String, Object>> recientes(int limite) {
    return jdbc.queryForList("SELECT actor, metodo, ruta, estado, en FROM admin_actions "
      + "ORDER BY id DESC LIMIT ?", limite);
  }
}