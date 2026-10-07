package co.hotel.reservas;

import co.hotel.auditoria.ActorActual;
import co.hotel.auditoria.AuditoriaRepository;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Panel de reservas del hotel: listado, detalle con historial y cambio de estado.
 * Traduce HTTP <-> dominio; las reglas están en {@link ReservaService}.
 */
@RestController
public class AdminReservasController {
  private final ReservaService svc;
  private final AuditoriaRepository auditoria;
  private final ComprobanteService comprobantes;

  public AdminReservasController(ReservaService svc, AuditoriaRepository auditoria,
                                 ComprobanteService comprobantes) {
    this.svc = svc;
    this.auditoria = auditoria;
    this.comprobantes = comprobantes;
  }

  /**
   * El actor no viene en el cuerpo a propósito: lo fija la sesión. Antes este endpoint guardaba el
   * campo `actor` del JSON, así que el panel escribía el literal "panel" y cualquier sesión podía
   * atribuir un cambio a otro.
   */
  public record CambioEstadoReq(String estado) {}

  @GetMapping("/api/admin/reservas")
  public List<Map<String, Object>> listar(@RequestParam(defaultValue = "50") int limite) {
    return svc.listar(limite).stream().map(AdminReservasController::fila).toList();
  }

  @GetMapping("/api/admin/reservas/{codigo}")
  public ResponseEntity<?> detalle(@PathVariable String codigo) {
    var reserva = svc.buscar(codigo);
    if (reserva.isEmpty()) return ResponseEntity.status(404).body(Map.of("error", "reserva no encontrada"));
    return ResponseEntity.ok(Map.of(
      "reserva", fila(reserva.get()),
      "historial", auditoria.historialDe(svc.idDe(codigo))));
  }

  /** Comprobante para el panel: no pide el correo porque la sesión ya es del hotel. */
  @GetMapping("/api/admin/reservas/{codigo}/comprobante")
  public ResponseEntity<?> comprobante(@PathVariable String codigo) {
    return comprobantes.comprobante(codigo, null)
      .map(ResponseEntity::ok)
      .orElseGet(() -> ResponseEntity.status(404).body(Map.of("error", "reserva no encontrada")));
  }

  @PostMapping("/api/admin/reservas/{codigo}/estado")
  public ResponseEntity<?> cambiar(@PathVariable String codigo, @RequestBody CambioEstadoReq req) {
    try {
      EstadoReserva nuevo = EstadoReserva.valueOf(req.estado().toUpperCase());
      Reserva actualizada = svc.cambiarEstado(codigo, nuevo, ActorActual.correo());
      return ResponseEntity.ok(fila(actualizada));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("error", "estado no válido: " + req.estado()));
    } catch (DatosInvalidosException e) {
      return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
    }
  }

  private static Map<String, Object> fila(Reserva r) {
    return Map.of(
      "codigo", r.codigo(),
      "email", r.email(),
      "nombre", r.nombre(),
      "llegada", r.llegada().toString(),
      "salida", r.salida().toString(),
      "noches", r.noches(),
      "huespedes", r.huespedes(),
      "estado", r.estado().name(),
      "origen", r.origen().name(),
      "creadoEn", r.creadoEn());
  }
}