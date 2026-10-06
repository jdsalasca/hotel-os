package co.hotel.ota;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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