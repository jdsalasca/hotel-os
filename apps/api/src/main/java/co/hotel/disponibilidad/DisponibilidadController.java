package co.hotel.disponibilidad;

import co.hotel.inventario.DatosInvalidosException;
import co.hotel.inventario.InventarioService;
import co.hotel.inventario.OpcionOferta;
import co.hotel.seguridad.LimiteConsultasPublicas;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Búsqueda pública de disponibilidad. Solo devuelve habitaciones que el hotel ha dado de alta,
 * que están libres y cuyo precio puede totalizarse con tarifas configuradas.
 */
@RestController
public class DisponibilidadController {
  private final InventarioService inventario;
  private final LimiteConsultasPublicas lecturas;

  public DisponibilidadController(InventarioService inventario, LimiteConsultasPublicas lecturas) {
    this.inventario = inventario;
    this.lecturas = lecturas;
  }

  @GetMapping("/api/disponibilidad")
  public ResponseEntity<?> buscar(@RequestParam String llegada, @RequestParam String salida,
                                  @RequestParam(defaultValue = "2") int huespedes,
                                  HttpServletRequest peticion) {
    // Es la lectura más pesada y es pública: sin tope, basta un bucle para martillear la base.
    if (!lecturas.permitir(peticion.getRemoteAddr())) {
      return ResponseEntity.status(429).body(Map.of("error",
        "demasiadas consultas desde esta conexión. Espera un minuto e inténtalo de nuevo.",
        "ofertas", List.of()));
    }
    try {
      List<Map<String, Object>> ofertas = inventario
        .disponiblesConPrecio(LocalDate.parse(llegada), LocalDate.parse(salida), huespedes)
        .stream().map(DisponibilidadController::oferta).toList();
      return ResponseEntity.ok(
        Map.of("llegada", llegada, "salida", salida, "huespedes", huespedes, "ofertas", ofertas));
    } catch (DatosInvalidosException e) {
      return ResponseEntity.ok(Map.of("error", e.getMessage(), "ofertas", List.of()));
    } catch (java.time.format.DateTimeParseException e) {
      return ResponseEntity.ok(Map.of("error", "las fechas deben tener formato YYYY-MM-DD", "ofertas", List.of()));
    }
  }

  private static Map<String, Object> oferta(OpcionOferta o) {
    return Map.of(
      "habitacion", Map.of("id", o.habitacion().id(), "codigo", o.habitacion().codigo(),
        "nombre", o.habitacion().nombre()),
      "tipo", Map.of("id", o.tipo().id(), "codigo", o.tipo().codigo(), "nombre", o.tipo().nombre(),
        "capacidadMax", o.tipo().capacidadMax()),
      "totalCents", o.totalCents(),
      "moneda", o.moneda(),
      "noches", o.noches(),
      "descuentoPct", o.descuentoPct(),
      "totalSinDescuentoCents", o.totalSinDescuentoCents(),
      "plan", Map.of("id", o.plan().id(), "codigo", o.plan().codigo(), "nombre", o.plan().nombre()));
  }

  /**
   * Calendario público del mes: qué días tienen habitaciones a la venta y desde qué precio.
   * Una sola petición en vez de treinta búsquedas, con las mismas reglas que la búsqueda para no
   * mostrar dos verdades distintas. El precio es el de una noche; el total del viaje lo da la
   * búsqueda con las fechas elegidas.
   */
  @GetMapping("/api/disponibilidad/calendario")
  public ResponseEntity<?> calendario(@RequestParam String mes,
                                      @RequestParam(defaultValue = "2") int huespedes,
                                      HttpServletRequest peticion) {
    if (!lecturas.permitir(peticion.getRemoteAddr())) {
      return ResponseEntity.status(429).body(Map.of("error",
        "demasiadas consultas desde esta conexión. Espera un minuto e inténtalo de nuevo.",
        "dias", List.of()));
    }
    final java.time.YearMonth periodo;
    try {
      periodo = java.time.YearMonth.parse(mes);
    } catch (java.time.format.DateTimeParseException e) {
      return ResponseEntity.badRequest().body(Map.of("error", "el mes debe tener formato YYYY-MM",
        "dias", List.of()));
    }
    if (huespedes < 1) {
      return ResponseEntity.badRequest().body(Map.of("error", "número de huéspedes inválido",
        "dias", List.of()));
    }
    try {
      var dias = inventario.calendarioMensual(periodo, huespedes).stream().map(d -> {
        var dia = new java.util.LinkedHashMap<String, Object>();
        dia.put("fecha", d.fecha().toString());
        dia.put("disponibles", d.disponibles());
        if (d.disponibles() > 0) {
          dia.put("desdeCents", d.desdeCents());
          dia.put("moneda", d.moneda());
        }
        return dia;
      }).toList();
      return ResponseEntity.ok(Map.of("mes", periodo.toString(), "huespedes", huespedes, "dias", dias));
    } catch (DatosInvalidosException e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "dias", List.of()));
    }
  }

  /**
   * Detalle de una oferta: habitación, tipo, plan y precio noche por noche.
   *
   * Es lo que la tarjeta de la búsqueda resume en una línea. Si la habitación no está a la venta
   * para esas fechas, 404 con motivo en vez de un desglose vacío.
   */
  @GetMapping("/api/disponibilidad/detalle")
  public ResponseEntity<?> detalle(@RequestParam Long roomId, @RequestParam String llegada,
                                   @RequestParam String salida,
                                   @RequestParam(defaultValue = "2") int huespedes,
                                   HttpServletRequest peticion) {
    if (!lecturas.permitir(peticion.getRemoteAddr())) {
      return ResponseEntity.status(429).body(Map.of("error",
        "demasiadas consultas desde esta conexión. Espera un minuto e inténtalo de nuevo."));
    }
    final java.time.LocalDate desde;
    final java.time.LocalDate hasta;
    try {
      desde = java.time.LocalDate.parse(llegada);
      hasta = java.time.LocalDate.parse(salida);
    } catch (java.time.format.DateTimeParseException e) {
      return ResponseEntity.badRequest().body(Map.of("error", "las fechas deben tener formato YYYY-MM-DD"));
    }
    if (!desde.isBefore(hasta)) {
      return ResponseEntity.badRequest().body(Map.of("error", "la salida debe ser posterior a la llegada"));
    }
    try {
      return inventario.detalleOferta(roomId, desde, hasta, huespedes)
        .map(d -> ResponseEntity.ok((Object) Map.of(
          "habitacion", Map.of("id", d.habitacion().id(), "codigo", d.habitacion().codigo(),
            "nombre", d.habitacion().nombre()),
          "tipo", Map.of("id", d.tipo().id(), "codigo", d.tipo().codigo(),
            "nombre", d.tipo().nombre(), "capacidadMax", d.tipo().capacidadMax()),
          "plan", Map.of("codigo", d.plan().codigo(), "nombre", d.plan().nombre()),
          "noches", d.noches().stream()
            .map(n -> Map.of("fecha", n.fecha().toString(), "precioCents", n.precioCents())).toList(),
          "totalCents", d.totalCents(),
          "moneda", d.moneda(),
          "descuentoPct", d.descuentoPct(),
          "totalSinDescuentoCents", d.totalSinDescuentoCents())))
        .orElseGet(() -> ResponseEntity.status(404).body(Map.of("error",
          "esa habitación no está a la venta para esas fechas y huéspedes")));
    } catch (DatosInvalidosException e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
  }
}