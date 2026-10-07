package co.hotel.pagos;

import co.hotel.auditoria.ActorActual;
import co.hotel.inventario.DatosInvalidosException;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Libro de abonos del panel: registrar, anular y saldar por reserva. Todo con sesión ADMIN por
 * la cadena de seguridad; aquí no se decide quién entra.
 *
 * El 404 distingue "no existe" por el texto del error: es el único DatosInvalidos que estos
 * métodos lanzan con "no encontrad…". Frágil a propósito y probado: si el mensaje cambia, el
 * test lo dice.
 */
@RestController
public class PagosAdminController {
  private final PagosService pagos;

  public PagosAdminController(PagosService pagos) { this.pagos = pagos; }

  public record AbonoReq(Long montoCents, String moneda, String concepto) {}

  @PostMapping("/api/admin/reservas/{codigo}/abonos")
  public ResponseEntity<?> abonar(@PathVariable String codigo, @RequestBody AbonoReq req) {
    try {
      long id = pagos.abonar(codigo, req.montoCents() == null ? 0 : req.montoCents(),
        req.moneda(), req.concepto(), ActorActual.correo());
      return ResponseEntity.status(201).body(Map.of("id", id));
    } catch (DatosInvalidosException e) {
      return error(e);
    }
  }

  @PostMapping("/api/admin/abonos/{id}/anular")
  public ResponseEntity<?> anular(@PathVariable long id) {
    try {
      pagos.anular(id, ActorActual.correo());
      return ResponseEntity.ok(Map.of("estado", "abono anulado"));
    } catch (DatosInvalidosException e) {
      return error(e);
    }
  }

  @GetMapping("/api/admin/reservas/{codigo}/saldo")
  public ResponseEntity<?> saldo(@PathVariable String codigo) {
    try {
      var s = pagos.saldo(codigo);
      return ResponseEntity.ok(Map.of(
        "totalCents", s.totalCents(),
        "moneda", s.moneda() == null ? "" : s.moneda(),
        "abonadoCents", s.abonadoCents(),
        "pendienteCents", s.pendienteCents(),
        "movimientos", s.movimientos()));
    } catch (DatosInvalidosException e) {
      return error(e);
    }
  }

  private static ResponseEntity<Map<String, String>> error(DatosInvalidosException e) {
    String mensaje = e.getMessage() == null ? "" : e.getMessage();
    if (mensaje.contains("no encontrad")) {
      return ResponseEntity.status(404).body(Map.of("error", mensaje));
    }
    return ResponseEntity.badRequest().body(Map.of("error", mensaje));
  }
}
