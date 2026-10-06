package co.hotel.disponibilidad;

import co.hotel.inventario.DatosInvalidosException;
import co.hotel.inventario.InventarioService;
import co.hotel.inventario.OpcionOferta;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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

  public DisponibilidadController(InventarioService inventario) { this.inventario = inventario; }

  @GetMapping("/api/disponibilidad")
  public Map<String, Object> buscar(@RequestParam String llegada, @RequestParam String salida,
                                    @RequestParam(defaultValue = "2") int huespedes) {
    try {
      List<Map<String, Object>> ofertas = inventario
        .disponiblesConPrecio(LocalDate.parse(llegada), LocalDate.parse(salida), huespedes)
        .stream().map(DisponibilidadController::oferta).toList();
      return Map.of("llegada", llegada, "salida", salida, "huespedes", huespedes, "ofertas", ofertas);
    } catch (DatosInvalidosException e) {
      return Map.of("error", e.getMessage(), "ofertas", List.of());
    } catch (java.time.format.DateTimeParseException e) {
      return Map.of("error", "las fechas deben tener formato YYYY-MM-DD", "ofertas", List.of());
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
      "noches", o.noches());
  }
}