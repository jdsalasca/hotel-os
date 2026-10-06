package co.hotel.config;

import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Salud del servicio. Consulta la base de verdad: un healthcheck que solo dice "ok" sin tocar el
 * archivo SQLite serviría de cobertura a una base inaccesible.
 */
@RestController
public class HealthController {
  private final JdbcTemplate jdbc;

  public HealthController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  @GetMapping("/api/health")
  public Map<String, String> salud() {
    Integer tablas = jdbc.queryForObject(
      "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name IN "
        + "('reservations','rooms','users','hotel_config')", Integer.class);
    boolean esquemaCompleto = tablas != null && tablas == 4;
    return Map.of(
      "estado", esquemaCompleto ? "ok" : "degradado",
      "base", esquemaCompleto ? "accesible" : "esquema incompleto",
      "tablas", String.valueOf(tablas));
  }
}