package co.hotel.lugares;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Dónde queda el hotel y qué hay cerca. Lectura pública (el mapa de la landing); la escritura
 * es del panel. Las coordenadas se validan aquí también, no solo en la base: un NaN o un
 * infinito rompería el mapa sin que Flyway se queje.
 */
@RestController
public class LugaresController {
  private final LugaresRepository repo;
  private final co.hotel.hotel.HotelConfigRepository config;

  public LugaresController(LugaresRepository repo, co.hotel.hotel.HotelConfigRepository config) {
    this.repo = repo;
    this.config = config;
  }

  @GetMapping("/api/lugares")
  public Map<String, Object> publicos() {
    Map<String, String> valores = config.valores();
    Double lat = numero(valores.get("latitud"));
    Double lng = numero(valores.get("longitud"));
    Map<String, Object> hotel = lat == null || lng == null
      ? Map.of("ubicado", false)
      : Map.of("ubicado", true, "latitud", lat, "longitud", lng);
    // La distancia en metros solo existe si el hotel está ubicado: sin su punto no hay a qué
    // medir, y un número inventado es peor que un campo ausente. Cada lugar sale con su
    // `metros` (o sin él), que es lo que el mapa muestra como "a X m" / "a X km".
    var lugares = repo.activos().stream().map(l -> conDistancia(l, lat, lng)).toList();
    return Map.of("hotel", hotel, "lugares", porCercania(lugares));
  }

  /**
   * La lista pública va de más cerca a más lejos: quien lee "qué hay cerca" quiere primero lo
   * que tiene al lado, no lo que empieza por A. La distancia se ordena una sola vez, aquí,
   * con el mismo número que se muestra en pantalla.
   *
   * Sin hotel ubicado no hay con qué ordenar, así que se respeta el orden por nombre del
   * repositorio en vez de inventar una cercanía.
   */
  private static List<Map<String, Object>> porCercania(List<Map<String, Object>> lugares) {
    // Si no hay `metros` en el primero es que el hotel no está ubicado: no hay con qué
    // ordenar y se respeta el orden por nombre del repositorio.
    if (lugares.isEmpty() || lugares.get(0).get("metros") == null) return lugares;
    return lugares.stream()
      .sorted(java.util.Comparator.comparingLong(l -> ((Number) l.get("metros")).longValue()))
      .toList();
  }

  /** Distancia en línea recta sobre la esfera (haversine). El viaje real es más largo. */
  private static double metros(double lat1, double lng1, double lat2, double lng2) {
    double rad = Math.PI / 180;
    double dLat = (lat2 - lat1) * rad;
    double dLng = (lng2 - lng1) * rad;
    double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
      + Math.cos(lat1 * rad) * Math.cos(lat2 * rad)
        * Math.sin(dLng / 2) * Math.sin(dLng / 2);
    return 2 * 6371000 * Math.asin(Math.min(1, Math.sqrt(a)));
  }

  /**
   * El lugar listo para el navegador, con `metros` al hotel si se sabe dónde está el hotel. Lo
   * comparten la web pública y el panel: una sola forma de escribir un lugar y la distancia
   * calculada en un solo sitio. Sin hotel ubicado no se inventa distancia: el campo no existe.
   */
  private static Map<String, Object> conDistancia(LugaresRepository.Lugar l, Double lat, Double lng) {
    var fila = new java.util.LinkedHashMap<String, Object>();
    fila.put("id", l.id());
    fila.put("nombre", l.nombre());
    fila.put("descripcion", l.descripcion());
    fila.put("latitud", l.latitud());
    fila.put("longitud", l.longitud());
    fila.put("activo", l.activo());
    if (lat != null && lng != null) {
      fila.put("metros", Math.round(metros(lat, lng, l.latitud(), l.longitud())));
    }
    return fila;
  }

  public record LugarReq(String nombre, String descripcion, Double latitud, Double longitud,
                         Boolean activo) {}

  @GetMapping("/api/admin/lugares")
  public Map<String, Object> todos() {
    Map<String, String> valores = config.valores();
    Double lat = numero(valores.get("latitud"));
    Double lng = numero(valores.get("longitud"));
    return Map.of("lugares", repo.todos().stream().map(l -> conDistancia(l, lat, lng)).toList());
  }

  @PostMapping("/api/admin/lugares")
  public ResponseEntity<?> crear(@RequestBody LugarReq req) {
    return ResponseEntity.status(HttpStatus.CREATED)
      .body(Map.of("id", repo.crear(exigirNombre(req), texto(req.descripcion()),
        exigirLat(req), exigirLng(req))));
  }

  @PutMapping("/api/admin/lugares/{id}")
  public ResponseEntity<?> actualizar(@PathVariable long id, @RequestBody LugarReq req) {
    int filas = repo.actualizar(id, exigirNombre(req), texto(req.descripcion()), exigirLat(req),
      exigirLng(req), req.activo() == null || req.activo());
    if (filas == 0)
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "lugar no existe"));
    return ResponseEntity.ok(Map.of("estado", "lugar actualizado"));
  }

  @DeleteMapping("/api/admin/lugares/{id}")
  public ResponseEntity<?> eliminar(@PathVariable long id) {
    int filas = repo.eliminar(id);
    if (filas == 0)
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "lugar no existe"));
    return ResponseEntity.ok(Map.of("estado", "lugar eliminado"));
  }

  private static String texto(String valor) {
    return valor == null ? "" : valor.trim();
  }

  private static String exigirNombre(LugarReq req) {
    String nombre = texto(req.nombre());
    if (nombre.length() < 2 || nombre.length() > 120)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
        "el nombre necesita entre 2 y 120 caracteres");
    return nombre;
  }

  private static double exigirLat(LugarReq req) {
    if (req.latitud() == null || Double.isNaN(req.latitud())
      || req.latitud() < -90 || req.latitud() > 90)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "latitud inválida (-90 a 90)");
    return req.latitud();
  }

  private static double exigirLng(LugarReq req) {
    if (req.longitud() == null || Double.isNaN(req.longitud())
      || req.longitud() < -180 || req.longitud() > 180)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
        "longitud inválida (-180 a 180)");
    return req.longitud();
  }

  private static Double numero(String valor) {
    if (valor == null || valor.isBlank()) return null;
    try {
      double n = Double.parseDouble(valor.trim());
      return Double.isFinite(n) ? n : null;
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
