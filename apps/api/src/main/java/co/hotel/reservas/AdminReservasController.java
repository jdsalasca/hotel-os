package co.hotel.reservas;

import co.hotel.auditoria.ActorActual;
import co.hotel.auditoria.AuditoriaRepository;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
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

  public record CrearManualReq(String email, String nombre, String llegada, String salida,
                               Integer huespedes, Long roomId, String idempotencia) {}

  /**
   * Alta manual desde el panel: la recepción registra al huésped que llama o llega sin
   * pasar por la web. La habitación la asigna el personal (requerida); el precio es el
   * vigente y queda congelado igual que en la web. Origen OTRO: no es venta directa.
   * 400 con entrada inválida; 409 si no hay disponibilidad para esas fechas.
   */
  @PostMapping("/api/admin/reservas")
  public ResponseEntity<?> crearManual(@RequestBody CrearManualReq req) {
    if (req.roomId() == null) {
      return ResponseEntity.badRequest().body(Map.of("error",
        "habitación requerida: la recepción asigna la habitación"));
    }
    final java.time.LocalDate llegada;
    final java.time.LocalDate salida;
    try {
      llegada = java.time.LocalDate.parse(req.llegada());
      salida = java.time.LocalDate.parse(req.salida());
    } catch (java.time.format.DateTimeParseException | NullPointerException e) {
      return ResponseEntity.badRequest().body(Map.of("error", "las fechas deben tener formato YYYY-MM-DD"));
    }
    int h = req.huespedes() == null ? 0 : req.huespedes();
    var datos = new CrearReserva(req.email(), req.nombre(), llegada, salida,
      h, Origen.OTRO, req.idempotencia(), req.roomId());
    try {
      String codigo = svc.crear(datos);
      var reserva = svc.buscar(codigo).orElseThrow(() -> new DatosInvalidosException("reserva no encontrada"));
      return ResponseEntity.status(201).body(fila(reserva));
    } catch (SinDisponibilidadException | ConflictoIdempotenciaException e) {
      return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
    } catch (DatosInvalidosException e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
  }

  public record ReasignarReq(Long roomId) {}

  public record CambiarFechasReq(String llegada, String salida) {}

  /**
   * Cambia las fechas de una reserva vigente sin pisar a otra ni a un bloqueo, recalculando el
   * precio con las nuevas. 400 con fechas ausentes o invertidas; 404 si no existe; 409 si no
   * está vigente o las fechas no están libres o a la venta.
   */
  @PostMapping("/api/admin/reservas/{codigo}/fechas")
  public ResponseEntity<?> cambiarFechas(@PathVariable String codigo, @RequestBody CambiarFechasReq req) {
    final java.time.LocalDate llegada;
    final java.time.LocalDate salida;
    try {
      llegada = java.time.LocalDate.parse(req.llegada());
      salida = java.time.LocalDate.parse(req.salida());
    } catch (java.time.format.DateTimeParseException | NullPointerException e) {
      return ResponseEntity.badRequest().body(Map.of("error", "las fechas deben tener formato YYYY-MM-DD"));
    }
    if (svc.buscar(codigo).isEmpty()) {
      return ResponseEntity.status(404).body(Map.of("error", "reserva no encontrada"));
    }
    try {
      Reserva movida = svc.cambiarFechas(codigo, llegada, salida, ActorActual.correo());
      return ResponseEntity.ok(fila(movida));
    } catch (ExcepcionDeEstado | SinDisponibilidadException e) {
      return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
    } catch (DatosInvalidosException e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
  }

  /**
   * Reasigna la reserva a otra habitación libre y vendible, recalculando el precio. 404 si no
   * existe la reserva o la habitación; 409 si la reserva no está vigente o la habitación no
   * está libre o a la venta.
   */
  @PostMapping("/api/admin/reservas/{codigo}/habitacion")
  public ResponseEntity<?> reasignar(@PathVariable String codigo, @RequestBody ReasignarReq req) {
    if (req.roomId() == null) {
      return ResponseEntity.badRequest().body(Map.of("error", "roomId requerido"));
    }
    try {
      Reserva reasignada = svc.reasignar(codigo, req.roomId(), ActorActual.correo());
      return ResponseEntity.ok(fila(reasignada));
    } catch (ExcepcionDeEstado | SinDisponibilidadException e) {
      return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
    } catch (DatosInvalidosException e) {
      return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
    }
  }

  /**
   * Límite que no es número (`?limite=muchas`): Spring lo rechaza antes de entrar con su
   * 400 por defecto, sin `error`. Aquí vuelve al contrato de la casa.
   */
  @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
  public ResponseEntity<?> parametroNoNumerico(
      org.springframework.web.method.annotation.MethodArgumentTypeMismatchException e) {
    return ResponseEntity.badRequest().body(Map.of("error",
      "límite inválido: debe ser un número"));
  }

  @GetMapping("/api/admin/reservas")
  public ResponseEntity<?> listar(@RequestParam(defaultValue = "50") int limite,
                                  @RequestParam(required = false) String q,
                                  @RequestParam(required = false) String estado) {
    // En SQLite LIMIT -1 es sin tope: sin validar, ?limite=-1 vuelca la tabla entera.
    if (limite < 1) {
      return ResponseEntity.badRequest().body(Map.of("error", "el límite debe ser al menos 1"));
    }
    final EstadoReserva filtro;
    try {
      filtro = estado == null || estado.isBlank() ? null : EstadoReserva.valueOf(estado.toUpperCase());
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("error", "estado no válido: " + estado));
    }
    return ResponseEntity.ok(svc.listar(q, filtro, limite).stream()
      .map(AdminReservasController::fila).toList());
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
      "salidas", parte.salidas().stream().map(AdminReservasController::movimiento).toList(),
      "enCasa", parte.enCasa().stream().map(AdminReservasController::movimiento).toList()));
  }

  private static Map<String, Object> movimiento(ReservaRepository.Movimiento m) {
    return Map.of("codigo", m.codigo(), "email", m.email(), "nombre", m.nombre(),
      "huespedes", m.huespedes(), "habitacion", m.habitacion());
  }

  /**
   * La lista en CSV para trabajar fuera del panel: mismos filtros que la pantalla. Las celdas
   * salen neutralizadas como en el resto de exportaciones.
   */
  @GetMapping(value = "/api/admin/reservas.csv", produces = "text/csv;charset=UTF-8")
  public ResponseEntity<String> exportarCsv(@RequestParam(defaultValue = "200") int limite,
                                           @RequestParam(required = false) String q,
                                           @RequestParam(required = false) String estado) {
    if (limite < 1) {
      return ResponseEntity.badRequest().body("error,límite inválido: debe ser al menos 1\n");
    }
    final EstadoReserva filtro;
    try {
      filtro = estado == null || estado.isBlank() ? null : EstadoReserva.valueOf(estado.toUpperCase());
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body("error,estado no válido: " + estado + "\n");
    }
    StringBuilder csv = new StringBuilder();
    csv.append("codigo,email,nombre,llegada,salida,noches,huespedes,estado,origen,"
      + "total_cents,moneda,creado_en\n");
    for (Reserva r : svc.listar(q, filtro, limite)) {
      csv.append(co.hotel.util.Csv.celda(r.codigo())).append(',')
        .append(co.hotel.util.Csv.celda(r.email())).append(',')
        .append(co.hotel.util.Csv.celda(r.nombre())).append(',')
        .append(co.hotel.util.Csv.celda(r.llegada().toString())).append(',')
        .append(co.hotel.util.Csv.celda(r.salida().toString())).append(',')
        .append(r.noches()).append(',')
        .append(r.huespedes()).append(',')
        .append(co.hotel.util.Csv.celda(r.estado().name())).append(',')
        .append(co.hotel.util.Csv.celda(r.origen().name())).append(',')
        .append(r.totalCents() == null ? "" : r.totalCents()).append(',')
        .append(co.hotel.util.Csv.celda(r.moneda())).append(',')
        .append(co.hotel.util.Csv.celda(r.creadoEn()))
        .append('\n');
    }
    return ResponseEntity.ok()
      .header("Content-Disposition", "attachment; filename=reservas.csv")
      .contentType(org.springframework.http.MediaType.parseMediaType("text/csv"))
      .body(csv.toString());
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