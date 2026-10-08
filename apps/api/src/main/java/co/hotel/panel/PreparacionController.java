package co.hotel.panel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
  private final JdbcTemplate jdbc;

  public PreparacionController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
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
    return Map.of("items", items);
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
