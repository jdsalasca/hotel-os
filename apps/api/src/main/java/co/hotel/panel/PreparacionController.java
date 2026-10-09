package co.hotel.panel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lo que le falta al hotel para estar a punto, calculado de su propia base. La puerta del
 * panel muestra esta lista para que el estreno no sea adivinar qué falta: cada ítem dice
 * si está hecho y a dónde ir a hacerlo.
 */
@RestController
public class PreparacionController {
  /** Los respaldos corren a diario: con más de 36 h sin copia, algo se atascó. */
  private static final long FRESCO_HORAS = 36;

  private final JdbcTemplate jdbc;
  private final String respaldosDir;

  public PreparacionController(JdbcTemplate jdbc,
      @Value("${hotel.respaldos-dir:/backups}") String respaldosDir) {
    this.jdbc = jdbc;
    this.respaldosDir = respaldosDir;
  }

  public record Punto(String clave, String titulo, boolean hecho, String url) {}

  @GetMapping("/api/admin/preparacion")
  public Map<String, Object> preparacion() {
    Map<String, String> config = new LinkedHashMap<>();
    jdbc.query("SELECT clave, valor FROM hotel_config",
      rs -> { config.put(rs.getString("clave"), rs.getString("valor")); });
    List<Punto> items = new ArrayList<>();
    items.add(new Punto("habitaciones", "Habitaciones registradas", contar("rooms") > 0,
      "/admin/inventario"));
    items.add(new Punto("tarifas", "Precios publicados a futuro",
      contarDonde("rates", "fecha >= date('now')") > 0, "/admin/inventario"));
    String nombre = config.getOrDefault("nombre", "").trim();
    items.add(new Punto("hotel", "Nombre y datos del hotel", !nombre.isEmpty(), "/admin/hotel"));
    boolean ubicado = !config.getOrDefault("latitud", "").isBlank()
      && !config.getOrDefault("longitud", "").isBlank();
    items.add(new Punto("ubicacion", "Ubicación en el mapa", ubicado, "/admin/hotel"));
    items.add(new Punto("lugares", "Sitios cercanos en el mapa",
      contarDonde("lugares_interes", "activo=1") > 0, "/admin/lugares"));
    items.add(new Punto("respaldo", "Respaldo reciente", respaldoFresco(), null));
    return Map.of("items", items);
  }

  /**
   * La copia más nueva de hotel-*.sqlite3 con menos de 36 h. Sin pantalla que lo
   * arregle, el punto no lleva url: avisa en texto, no finge una puerta.
   */
  private boolean respaldoFresco() {
    try (var archivos = java.nio.file.Files.list(java.nio.file.Path.of(respaldosDir))) {
      long hace36h = System.currentTimeMillis() - FRESCO_HORAS * 3_600_000L;
      return archivos
        .filter(p -> p.getFileName().toString().matches("hotel-.*\\.sqlite3"))
        .anyMatch(p -> {
          try {
            return java.nio.file.Files.getLastModifiedTime(p).toMillis() > hace36h;
          } catch (Exception e) {
            return false;
          }
        });
    } catch (Exception e) {
      return false;
    }
  }

  private int contar(String tabla) {
    Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla, Integer.class);
    return n == null ? 0 : n;
  }

  private int contarDonde(String tabla, String condicion) {
    Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla + " WHERE " + condicion,
      Integer.class);
    return n == null ? 0 : n;
  }
}
