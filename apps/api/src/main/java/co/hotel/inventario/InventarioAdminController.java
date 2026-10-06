package co.hotel.inventario;

import java.time.LocalDate;
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
 * Administración del inventario: tipos, habitaciones, planes tarifarios, precios por fecha y
 * bloqueos. Es donde el hotel registra lo suyo; nada de esto tiene valores por defecto inventados.
 */
@RestController
public class InventarioAdminController {
  private final InventarioService inventario;
  private final TarifaService tarifas;

  public InventarioAdminController(InventarioService inventario, TarifaService tarifas) {
    this.inventario = inventario;
    this.tarifas = tarifas;
  }

  public record TipoReq(String codigo, String nombre, Integer capacidadMax) {}
  public record HabitacionReq(String codigo, Long roomTypeId, String nombre) {}
  public record EstadoReq(String estado) {}
  public record PlanReq(String codigo, String nombre, String moneda) {}
  public record TarifaReq(Long ratePlanId, Long roomTypeId, String fecha, Long precioCents,
                          Integer minEstancia, Integer maxEstancia, Boolean cerrado) {}
  public record BloqueoReq(Long roomId, String desde, String hasta, String motivo) {}

  @PostMapping("/api/admin/tipos")
  public ResponseEntity<?> crearTipo(@RequestBody TipoReq req) {
    return ok(() -> inventario.crearTipo(req.codigo(), req.nombre(),
      req.capacidadMax() == null ? 0 : req.capacidadMax()));
  }

  @GetMapping("/api/admin/tipos")
  public List<RoomType> listarTipos() { return inventario.listarTipos(); }

  @PostMapping("/api/admin/habitaciones")
  public ResponseEntity<?> crearHabitacion(@RequestBody HabitacionReq req) {
    if (req.roomTypeId() == null) return ResponseEntity.badRequest().body(Map.of("error", "roomTypeId requerido"));
    return ok(() -> inventario.crearHabitacion(req.codigo(), req.roomTypeId(), req.nombre()));
  }

  @GetMapping("/api/admin/habitaciones")
  public List<Habitacion> listarHabitaciones() { return inventario.listarHabitaciones(); }

  @PostMapping("/api/admin/habitaciones/{id}/estado")
  public ResponseEntity<?> cambiarEstado(@PathVariable long id, @RequestBody EstadoReq req) {
    return ok(() -> inventario.cambiarEstado(id, EstadoHabitacion.valueOf(req.estado().toUpperCase())));
  }

  @PostMapping("/api/admin/planes")
  public ResponseEntity<?> crearPlan(@RequestBody PlanReq req) {
    return ok(() -> tarifas.crearPlan(req.codigo(), req.nombre(), req.moneda()));
  }

  @GetMapping("/api/admin/planes")
  public List<PlanTarifario> listarPlanes() { return tarifas.listarPlanes(); }

  /**
   * Noches con precio del tipo en el periodo, para que el hotel vea lo que ha fijado. `planId` y
   * `tipoId` son obligatorios: sin plan no hay tarifa que leer.
   */
  @GetMapping("/api/admin/tarifas")
  public ResponseEntity<?> listarTarifas(@RequestParam Long planId, @RequestParam Long tipoId,
                                          @RequestParam String desde, @RequestParam String hasta) {
    return ok(() -> tarifas.nochesDe(planId, tipoId, LocalDate.parse(desde), LocalDate.parse(hasta)));
  }

  /** Alta o actualización del precio de una noche. Repetir la misma fecha la sobrescribe. */
  @PostMapping("/api/admin/tarifas")
  public ResponseEntity<?> fijarTarifa(@RequestBody TarifaReq req) {
    if (req.ratePlanId() == null || req.roomTypeId() == null || req.fecha() == null || req.precioCents() == null)
      return ResponseEntity.badRequest()
        .body(Map.of("error", "ratePlanId, roomTypeId, fecha y precioCents son obligatorios"));
    return ok(() -> {
      PlanTarifario plan = plan(req.ratePlanId());
      LocalDate fecha = LocalDate.parse(req.fecha());
      if (req.precioCents() >= 0) tarifas.fijarPrecio(plan, req.roomTypeId(), fecha, req.precioCents());
      if (req.minEstancia() != null) tarifas.fijarMinimoEstancia(plan, req.roomTypeId(), fecha, req.minEstancia());
      if (req.maxEstancia() != null) tarifas.fijarMaximoEstancia(plan, req.roomTypeId(), fecha, req.maxEstancia());
      if (Boolean.TRUE.equals(req.cerrado())) tarifas.cerrarNoche(plan, req.roomTypeId(), fecha);
      if (Boolean.FALSE.equals(req.cerrado())) tarifas.abrirNoche(plan, req.roomTypeId(), fecha);
      return Map.of("ok", true, "ratePlanId", req.ratePlanId(), "roomTypeId", req.roomTypeId(),
        "fecha", req.fecha());
    });
  }

  /** Bloqueo de una habitación (roomId) o de todo el hotel si se omite. */
  @PostMapping("/api/admin/bloqueos")
  public ResponseEntity<?> bloquear(@RequestBody BloqueoReq req) {
    LocalDate desde = LocalDate.parse(req.desde());
    LocalDate hasta = LocalDate.parse(req.hasta());
    return ok(() -> Map.of("bloqueoId", req.roomId() == null
      ? inventario.bloquearTodo(desde, hasta, req.motivo())
      : inventario.bloquear(req.roomId(), desde, hasta, req.motivo())));
  }

  private PlanTarifario plan(long id) {
    return tarifas.planPorId(id);
  }

  private ResponseEntity<?> ok(java.util.function.Supplier<Object> trabajo) {
    try {
      Object resultado = trabajo.get();
      return resultado instanceof Integer i && i == 0
        ? ResponseEntity.ok(resultado)
        : ResponseEntity.status(201).body(resultado);
    } catch (DatosInvalidosException e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("error", "dato inválido: " + e.getMessage()));
    }
  }
}