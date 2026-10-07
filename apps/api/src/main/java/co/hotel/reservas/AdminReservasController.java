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
    } catch (ExcepcionDeEstado e) {
      return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
    } catch (DatosInvalidosException e) {
      return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
    }
  }

  /**
   * Parte del día para la recepción: quién llega y quién se va, con habitación y huéspedes.
   * Un día sin movimiento trae listas vacías, no un 404: el silencio también es información.
   */
  @GetMapping("/api/admin/ocupacion/dia")
  public ResponseEntity<?> parteDelDia(@RequestParam String fecha) {
    final java.time.LocalDate dia;
    try {
      dia = java.time.LocalDate.parse(fecha);
    } catch (java.time.format.DateTimeParseException e) {
      return ResponseEntity.badRequest().body(Map.of("error", "la fecha debe tener formato YYYY-MM-DD"));
    }
    var parte = svc.parteDelDia(dia);
    return ResponseEntity.ok(Map.of(
      "fecha", dia.toString(),
      "llegadas", parte.llegadas().stream().map(AdminReservasController::movimiento).toList(),
      "salidas", parte.salidas().stream().map(AdminReservasController::movimiento).toList()));
  }

  private static Map<String, Object> movimiento(ReservaRepository.Movimiento m) {
    return Map.of("codigo", m.codigo(), "email", m.email(), "nombre", m.nombre(),
      "huespedes", m.huespedes(), "habitacion", m.habitacion());
  }

  private static Map<String, Object> fila(Reserva r) {
    return Map.ofEntries(
      Map.entry("codigo", r.codigo()),
      Map.entry("email", r.email()),
      Map.entry("nombre", r.nombre()),
      Map.entry("llegada", r.llegada().toString()),
      Map.entry("salida", r.salida().toString()),
      Map.entry("noches", r.noches()),
      Map.entry("huespedes", r.huespedes()),
      Map.entry("estado", r.estado().name()),
      // Los estados alcanzables los decide el dominio, no el panel. El navegador no lleva su propia
      // copia de las reglas: cuando V6 perdió RECHAZADA del CHECK, dos listas de transiciones
      // divergentes se habrían desincronizado en silencio.
      Map.entry("siguientes", r.estado().desde().stream().map(Enum::name).toList()),
      Map.entry("origen", r.origen().name()),
      Map.entry("creadoEn", r.creadoEn()));
  }
}