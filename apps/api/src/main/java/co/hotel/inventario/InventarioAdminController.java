package co.hotel.inventario;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
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
  public record PlanReq(String codigo, String nombre, String moneda, Integer descuentoPct) {}
  public record DescuentoReq(Integer descuentoPct) {}
  public record TarifaReq(Long ratePlanId, Long roomTypeId, String fecha, Long precioCents,
                          Integer minEstancia, Integer maxEstancia, Boolean cerrado) {}
  public record NocheLoteReq(String fecha, Long precioCents,
                             Integer minEstancia, Integer maxEstancia, Boolean cerrado) {}
  public record LoteReq(Long ratePlanId, Long roomTypeId, java.util.List<NocheLoteReq> noches) {}
  public record BloqueoReq(Long roomId, String desde, String hasta, String motivo) {}

  @PostMapping("/api/admin/tipos")
  public ResponseEntity<?> crearTipo(@RequestBody TipoReq req) {
    if (req == null) return ResponseEntity.badRequest().body(Map.of("error", "cuerpo requerido"));
    return ok(() -> inventario.crearTipo(req.codigo(), req.nombre(),
      req.capacidadMax() == null ? 0 : req.capacidadMax()));
  }

  @GetMapping("/api/admin/tipos")
  public List<RoomType> listarTipos() { return inventario.listarTipos(); }

  @PostMapping("/api/admin/habitaciones")
  public ResponseEntity<?> crearHabitacion(@RequestBody HabitacionReq req) {
    if (req == null) return ResponseEntity.badRequest().body(Map.of("error", "cuerpo requerido"));
    if (req.roomTypeId() == null) return ResponseEntity.badRequest().body(Map.of("error", "roomTypeId requerido"));
    return ok(() -> inventario.crearHabitacion(req.codigo(), req.roomTypeId(), req.nombre()));
  }

  @GetMapping("/api/admin/habitaciones")
  public List<Habitacion> listarHabitaciones() { return inventario.listarHabitaciones(); }

  @PostMapping("/api/admin/habitaciones/{id}/estado")
  public ResponseEntity<?> cambiarEstado(@PathVariable long id, @RequestBody EstadoReq req) {
    if (req == null || req.estado() == null || req.estado().isBlank())
      return ResponseEntity.badRequest().body(Map.of("error", "estado inválido: use ACTIVA o FUERA_DE_SERVICIO"));
    final EstadoHabitacion nuevo;
    try {
      nuevo = EstadoHabitacion.valueOf(req.estado().trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("error", "estado inválido: use ACTIVA o FUERA_DE_SERVICIO"));
    }
    return ok(() -> inventario.cambiarEstado(id, nuevo));
  }

  @PostMapping("/api/admin/planes")
  public ResponseEntity<?> crearPlan(@RequestBody PlanReq req) {
    if (req == null) return ResponseEntity.badRequest().body(Map.of("error", "cuerpo requerido"));
    return ok(() -> tarifas.crearPlan(req.codigo(), req.nombre(), req.moneda(),
      req.descuentoPct() == null ? 0 : req.descuentoPct()));
  }

  /** Descuento del plan (0-100). Las reservas ya guardadas no se tocan: solo las nuevas. */
  @PostMapping("/api/admin/planes/{id}/descuento")
  public ResponseEntity<?> fijarDescuento(@PathVariable long id, @RequestBody DescuentoReq req) {
    if (req.descuentoPct() == null)
      return ResponseEntity.badRequest().body(Map.of("error", "descuentoPct es obligatorio"));
    try {
      // 200 y no 201: esto modifica, no crea. El ok() de este controlador es para altas.
      return ResponseEntity.ok(tarifas.fijarDescuento(id, req.descuentoPct()));
    } catch (DatosInvalidosException e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
  }

  @GetMapping("/api/admin/planes")
  public List<PlanTarifario> listarPlanes() { return tarifas.listarPlanes(); }

  @GetMapping("/api/admin/tarifas")
  public ResponseEntity<?> listarTarifas(@RequestParam Long planId, @RequestParam Long tipoId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
    return lectura(() -> tarifas.nochesDe(planId, tipoId, desde, hasta));
  }

  /**
   * Calendario de ocupación del hotel: una fila por habitación y una noche por día. Es el dato
   * que el panel necesita para mostrar qué noche vende y cuál no.
   */
  @GetMapping("/api/admin/calendario")
  public ResponseEntity<?> calendario(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
    return lectura(() -> inventario.ocupacion(desde, hasta));
  }

  /**
   * Alta o actualización de una noche: precio, restricciones y cierre en una sola operación
   * atómica. Repetir la misma fecha la sobrescribe; lo omitido se conserva. Lo rechazado no
   * cambia nada y responde 400 con el motivo.
   */
  @PostMapping("/api/admin/tarifas")
  public ResponseEntity<?> fijarTarifa(@RequestBody TarifaReq req) {
    if (req.ratePlanId() == null || req.roomTypeId() == null || req.fecha() == null)
      return ResponseEntity.badRequest()
        .body(Map.of("error", "ratePlanId, roomTypeId y fecha son obligatorios"));
    return ok(() -> {
      LocalDate fecha = LocalDate.parse(req.fecha());
      var noche = tarifas.fijarNoche(req.ratePlanId(), req.roomTypeId(), fecha,
        req.precioCents(), req.minEstancia(), req.maxEstancia(), req.cerrado());
      return Map.of("ratePlanId", req.ratePlanId(), "roomTypeId", req.roomTypeId(),
        "fecha", noche.fecha().toString(), "precioCents", noche.precioCents(),
        "cerrado", noche.cerrado());
    });
  }

  /** Bloqueo de una habitación (roomId) o de todo el hotel si se omite. */
  @PostMapping("/api/admin/bloqueos")
  public ResponseEntity<?> bloquear(@RequestBody BloqueoReq req) {
    return ok(() -> {
      LocalDate desde = LocalDate.parse(req.desde());
      LocalDate hasta = LocalDate.parse(req.hasta());
      return Map.of("bloqueoId", req.roomId() == null
        ? inventario.bloquearTodo(desde, hasta, req.motivo())
        : inventario.bloquear(req.roomId(), desde, hasta, req.motivo()));
    });
  }

  /**
   * Previa de un lote de noches: valida cada fila y dice qué se guardaría, sin escribir nada.
   * Siempre responde 200 con el detalle por fila; lo que impide guardar viaja por fila, no
   * como un error genérico.
   */
  @PostMapping("/api/admin/tarifas/lote/preview")
  public ResponseEntity<?> previsualizarLote(@RequestBody LoteReq req) {
    if (req == null || req.ratePlanId() == null || req.roomTypeId() == null || req.noches() == null)
      return ResponseEntity.badRequest()
        .body(Map.of("error", "ratePlanId, roomTypeId y noches son obligatorios"));
    try {
      var previa = tarifas.previsualizarLote(req.ratePlanId(), req.roomTypeId(), convertirLote(req.noches()));
      return ResponseEntity.ok(Map.of("lista", previa.lista(), "filas", filasJson(previa, req.noches())));
    } catch (DatosInvalidosException e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
  }

  /**
   * Aplica un lote de noches en una sola transacción: una fila inválida revierte todo y la
   * respuesta 400 trae el motivo por fila. Lo omitido se conserva, incluido el cierre.
   */
  @PostMapping("/api/admin/tarifas/lote")
  public ResponseEntity<?> aplicarLote(@RequestBody LoteReq req) {
    if (req == null || req.ratePlanId() == null || req.roomTypeId() == null || req.noches() == null)
      return ResponseEntity.badRequest()
        .body(Map.of("error", "ratePlanId, roomTypeId y noches son obligatorios"));
    try {
      var guardadas = tarifas.aplicarLote(req.ratePlanId(), req.roomTypeId(), convertirLote(req.noches()));
      return ResponseEntity.status(201).body(Map.of("guardadas", guardadas.size(), "noches", guardadas));
    } catch (TarifaService.LoteRechazadoException e) {
      return ResponseEntity.badRequest()
        .body(Map.of("error", e.getMessage(), "filas", filasJson(e.previa(), req.noches())));
    } catch (DatosInvalidosException e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
  }

  /** Convierte las filas del lote; una fecha mal escrita es error de su fila, no de la petición. */
  private static java.util.List<TarifaService.CambioNoche> convertirLote(
      java.util.List<NocheLoteReq> noches) {
    var cambios = new java.util.ArrayList<TarifaService.CambioNoche>(noches.size());
    for (var n : noches) {
      LocalDate fecha = null;
      if (n.fecha() != null && !n.fecha().isBlank()) {
        try {
          fecha = LocalDate.parse(n.fecha().trim());
        } catch (DateTimeParseException e) {
          fecha = null;
        }
      }
      cambios.add(new TarifaService.CambioNoche(fecha, n.precioCents(), n.minEstancia(),
        n.maxEstancia(), n.cerrado()));
    }
    return cambios;
  }

  /** Detalle por fila para la previa y el rechazo: si la fecha no parseó, se muestra la cruda. */
  private static java.util.List<Map<String, Object>> filasJson(TarifaService.PreviaLote previa,
      java.util.List<NocheLoteReq> pedidas) {
    var filas = new java.util.ArrayList<Map<String, Object>>(previa.filas().size());
    for (int i = 0; i < previa.filas().size(); i++) {
      var f = previa.filas().get(i);
      Map<String, Object> fila = new java.util.LinkedHashMap<>();
      String cruda = i < pedidas.size() && pedidas.get(i).fecha() != null ? pedidas.get(i).fecha() : "";
      fila.put("fecha", f.fecha() != null ? f.fecha().toString() : cruda);
      fila.put("valida", f.valida());
      if (f.valida()) {
        fila.put("precioCents", f.precioCents());
        fila.put("cerrado", f.cerrado());
        fila.put("nueva", f.nueva());
      } else {
        fila.put("motivo", f.motivo());
      }
      filas.add(fila);
    }
    return filas;
  }

  /** Bloqueos vigentes con su motivo, para gestionarlos desde el panel. */
  @GetMapping("/api/admin/bloqueos")
  public List<Map<String, Object>> listarBloqueos() {
    return inventario.bloqueosVigentes().stream().map(b -> {
      Map<String, Object> fila = new java.util.LinkedHashMap<>();
      fila.put("id", b.id());
      fila.put("habitacion", b.habitacion() == null ? "Todo el hotel" : b.habitacion());
      fila.put("desde", b.desde().toString());
      fila.put("hasta", b.hasta().toString());
      fila.put("motivo", b.motivo());
      return fila;
    }).toList();
  }

  /**
   * Retira un bloqueo: la habitación vuelve a la venta. Es POST y no DELETE para que quede en la
   * auditoría del panel como el resto de escrituras. Lo inexistente es 404, no un 200 silencioso.
   */
  @PostMapping("/api/admin/bloqueos/{id}/retirar")
  public ResponseEntity<?> retirarBloqueo(@PathVariable long id) {
    try {
      inventario.retirarBloqueo(id);
      return ResponseEntity.ok(Map.of("estado", "bloqueo retirado"));
    } catch (DatosInvalidosException e) {
      return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
    }
  }

  /** Escritura: 201 al crear, 200 si no hubo nada que crear. */
  private ResponseEntity<?> ok(java.util.function.Supplier<Object> trabajo) {
    return responder(trabajo, true);
  }

  /** Lectura: siempre 200. El 201 es para crear; un GET que responde 201 confunde a quien lo llame. */
  private ResponseEntity<?> lectura(java.util.function.Supplier<Object> trabajo) {
    return responder(trabajo, false);
  }

  private ResponseEntity<?> responder(java.util.function.Supplier<Object> trabajo, boolean creacion) {
    try {
      Object resultado = trabajo.get();
      if (!creacion) return ResponseEntity.ok(resultado);
      return resultado instanceof Integer i && i == 0
        ? ResponseEntity.ok(resultado)
        : ResponseEntity.status(201).body(resultado);
    } catch (DatosInvalidosException e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    } catch (DateTimeParseException e) {
      return ResponseEntity.badRequest().body(Map.of("error", "las fechas deben tener formato YYYY-MM-DD"));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("error", "dato inválido: " + e.getMessage()));
    }
  }
}
