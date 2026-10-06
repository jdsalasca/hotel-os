package co.hotel.reservas;

import java.util.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
public class ReservaController {
  private final String jdbcUrl;
  private final ReservaRepository repo;
  private final JdbcTemplate jdbc;
  public ReservaController(String jdbcUrl, ReservaRepository repo, JdbcTemplate jdbc) {
    this.jdbcUrl = jdbcUrl; this.repo = repo; this.jdbc = jdbc;
  }

  public record CrearReq(String email, String nombre, String llegada, String salida, Integer huespedes, String origen, String idempotencia, Long roomId) {}

  @GetMapping("/api/health") public Map<String, String> health() { return Map.of("status", "ok"); }

  @PostMapping("/api/reservas")
  public ResponseEntity<?> crear(@RequestBody CrearReq req, @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    try {
      String idem = (key != null && !key.isBlank()) ? key : req.idempotencia();
      Long roomId = req.roomId() != null ? req.roomId() : jdbc.queryForObject("SELECT id FROM rooms ORDER BY id LIMIT 1", Long.class);
      String codigo = new ReservaService(jdbcUrl).crear(req.email(), req.nombre(), req.llegada(), req.salida(), req.huespedes() == null ? 0 : req.huespedes(), req.origen(), idem, roomId);
      var r = repo.porCodigo(codigo);
      return ResponseEntity.status(201).body(Map.of("codigo", codigo, "estado", r.get("estado"), "mensaje", "pendiente de confirmación (sin pago/confirmación automática configurada)"));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    } catch (IllegalStateException e) {
      return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
    }
  }

  @GetMapping("/api/reservas/{codigo}")
  public ResponseEntity<?> una(@PathVariable String codigo, @RequestParam String email) {
    var r = repo.porCodigo(codigo);
    if (r == null || !email.equalsIgnoreCase((String) r.get("email")))
      return ResponseEntity.status(404).body(Map.of("error", "reserva no encontrada"));
    return ResponseEntity.ok(r);
  }

  @GetMapping("/api/admin/reservas")
  public List<Map<String, Object>> admin(@RequestParam(defaultValue = "50") int limit) { return repo.listar(limit); }
}
