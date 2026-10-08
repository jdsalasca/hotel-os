package co.hotel.hotel;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Identidad operativa del hotel. La ruta pública solo entrega marca y contacto; la administración
 * exige sesión y rol, como el resto del panel.
 */
@RestController
public class HotelConfigController {
  private final HotelConfigService hotel;

  public HotelConfigController(HotelConfigService hotel) { this.hotel = hotel; }

  @GetMapping("/api/hotel")
  public Map<String, String> publicos() { return hotel.publicos(); }

  /** La web lo pregunta antes de pintar el calendario: vacío no es lo mismo que lleno. */
  @GetMapping("/api/hotel/venta")
  public Map<String, Boolean> venta() { return Map.of("a_la_venta", hotel.aLaVenta()); }

  @GetMapping("/api/admin/hotel-config")
  public Map<String, String> administracion() { return hotel.administracion(); }

  @PostMapping("/api/admin/hotel-config")
  public ResponseEntity<?> guardar(@RequestBody Map<String, String> valores) {
    try {
      return ResponseEntity.ok(hotel.guardar(valores));
    } catch (ConfiguracionInvalidaException e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
  }
}
