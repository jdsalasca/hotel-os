package co.hotel.config;

import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Salud del servicio, en dos niveles:
 *
 * - Liveness (`/api/health/vivo`): el proceso responde. No toca la base: si SQLite se queda
 *   colgada, tumbar el proceso no la arregla y reiniciarlo sí puede empeorarlo.
 * - Readiness (`/api/health`): la base está accesible y con el esquema completo. Lista
 *   responde 200; si no, 503 para que el orquestador deje de mandar tráfico, no un 200 con
 *   un "degradado" que nadie mira.
 */
@RestController
public class HealthController {
  private final JdbcTemplate jdbc;

  public HealthController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  @GetMapping("/api/health/vivo")
  public ResponseEntity<Map<String, String>> vivo() {
    return ResponseEntity.ok(Map.of("estado", "ok"));
  }

  @GetMapping("/api/health")
  public ResponseEntity<Map<String, String>> salud() {
    final int tablas;
    try {
      Integer n = jdbc.queryForObject(
        "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name IN "
          + "('reservations','rooms','users','hotel_config')", Integer.class);
      tablas = n == null ? 0 : n;
    } catch (DataAccessException e) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(Map.of("estado", "no-lista", "base", "inaccesible", "tablas", "0"));
    }
    if (tablas != 4) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(Map.of("estado", "degradado", "base", "esquema incompleto",
          "tablas", String.valueOf(tablas)));
    }
    return ResponseEntity.ok(Map.of("estado", "ok", "base", "accesible",
      "tablas", String.valueOf(tablas)));
  }
}
