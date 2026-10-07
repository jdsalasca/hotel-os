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
    return Map.of("hotel", hotel, "lugares", repo.activos());
  }

  public record LugarReq(String nombre, String descripcion, Double latitud, Double longitud,
                         Boolean activo) {}

  @GetMapping("/api/admin/lugares")
  public Map<String, Object> todos() {
    return Map.of("lugares", repo.todos());
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
