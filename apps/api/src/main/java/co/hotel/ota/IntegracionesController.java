package co.hotel.ota;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pantalla de integraciones del panel: estado real por canal, requisitos pendientes, última
 * sincronización con su resultado, mapeos y reintento controlado.
 */
@RestController
@RequestMapping("/api/admin/integraciones")
public class IntegracionesController {
  private final IntegracionesService integraciones;

  public IntegracionesController(IntegracionesService integraciones) {
    this.integraciones = integraciones;
  }

  @GetMapping
  public Map<String, Object> panel() { return integraciones.panel(); }

  public record MapeoReq(String canal, Long roomId, Long roomTypeId, Long ratePlanId, String externalId) {}

  /** Declara el enlace entre inventario local e identificador del proveedor. No publica por sí solo. */
  @PostMapping("/mapeos")
  public ResponseEntity<?> crearMapeo(@RequestBody MapeoReq req) {
    try {
      if (req.canal() == null || req.canal().isBlank())
        throw new MapeoInvalidoException("canal obligatorio: BOOKING, DESPEGAR o AIRBNB");
      Canal canal = Canal.valueOf(req.canal().toUpperCase());
      return ResponseEntity.status(201).body(integraciones.crearMapeo(canal, req.roomId(),
        req.roomTypeId(), req.ratePlanId(), req.externalId()));
    } catch (MapeoInvalidoException e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("error", "canal desconocido: " + req.canal()));
    }
  }

  /** Retira un enlace declarado. No borra habitaciones, tipos, planes ni reservas. */
  @PostMapping("/mapeos/{id}/eliminar")
  public ResponseEntity<?> eliminarMapeo(@PathVariable long id) {
    try {
      if (!integraciones.eliminarMapeo(id))
        return ResponseEntity.status(404).body(Map.of("error", "mapeo no encontrado: " + id));
      return ResponseEntity.ok(Map.of("eliminado", true, "id", id));
    } catch (MapeoInvalidoException e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
  }

  /** Sincronización bajo demanda: el hotel reintenta cuando el proveedor vuelve. */
  @PostMapping("/{canal}/sincronizar")
  public ResponseEntity<?> sincronizar(@PathVariable String canal) {
    try {
      Canal c = Canal.valueOf(canal.toUpperCase());
      ResultadoSync resultado = integraciones.sincronizar(c);
      return ResponseEntity.status(resultado.exitosa() ? 200 : 502)
        .body(Map.of("canal", c.name(), "exitosa", resultado.exitosa(), "detalle", resultado.detalle()));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("error", "canal desconocido: " + canal));
    }
  }
}