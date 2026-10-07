package co.hotel.reservas;

import co.hotel.seguridad.LimiteConsultasPublicas;
import co.hotel.seguridad.LimiteReservas;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Controller HTTP. Solo traduce HTTP <-> dominio; las reglas viven en {@link ReservaService}. */
@RestController
public class ReservaController {
  private final ReservaService svc;
  private final RoomSelector rooms;
  private final ComprobanteService comprobantes;
  private final LimiteReservas limite;
  private final LimiteConsultasPublicas lecturas;

  public ReservaController(ReservaService svc, RoomSelector rooms, ComprobanteService comprobantes,
                           LimiteReservas limite, LimiteConsultasPublicas lecturas) {
    this.svc = svc;
    this.rooms = rooms;
    this.comprobantes = comprobantes;
    this.limite = limite;
    this.lecturas = lecturas;
  }

  /** Contrato de entrada del flujo público. El cliente envía ISO-8601 (YYYY-MM-DD). */
  public record CrearReq(String email, String nombre, String llegada, String salida,
                         Integer huespedes, Long roomId, String origen, String idempotencia) {}

  /** Respuesta pública. No expone datos internos ni datos de pago. */
  public record ReservaResp(String codigo, String email, String nombre, String llegada, String salida,
                            int huespedes, String estado, String origen, String mensaje) {
    static ReservaResp de(Reserva r, String mensaje) {
      return new ReservaResp(r.codigo(), r.email(), r.nombre(), r.llegada().toString(), r.salida().toString(),
        r.huespedes(), r.estado().name(), r.origen().name(), mensaje);
    }
  }

  @PostMapping("/api/reservas")
  public ResponseEntity<?> crear(@RequestBody CrearReq req,
                                 @RequestHeader(value = "Idempotency-Key", required = false) String claveCabecera,
                                 HttpServletRequest peticion) {
    // El límite va antes de tocar la base: si no, un bucle llenaría la tabla de descartes.
    // getRemoteAddr() ya es la IP del visitante porque server.forward-headers-strategy=framework
    // reescribe la petición cuando hay proxy delante.
    if (!limite.permitir(peticion.getRemoteAddr())) {
      return ResponseEntity.status(429).body(Map.of("error",
        "demasiadas reservas seguidas desde esta conexión. Inténtalo en unos minutos."));
    }
    String clave = (claveCabecera != null && !claveCabecera.isBlank()) ? claveCabecera : req.idempotencia();
    try {
      LocalDate llegada = LocalDate.parse(req.llegada());
      LocalDate salida = LocalDate.parse(req.salida());
      Long habitacion = req.roomId() != null ? req.roomId() : rooms.primeraDisponible(llegada, salida);
      var datos = new CrearReserva(req.email(), req.nombre(), llegada, salida,
        req.huespedes() == null ? 0 : req.huespedes(), Origen.WEB, clave, habitacion);
      String codigo = svc.crear(datos);
      return ResponseEntity.status(201).body(ReservaResp.de(svc.buscar(codigo).orElseThrow(),
        "Reserva registrada. Queda pendiente de confirmación: el hotel aún no ha configurado pago ni "
          + "confirmación automática."));
    } catch (DatosInvalidosException e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    } catch (DateTimeParseException e) {
      return ResponseEntity.badRequest().body(Map.of("error", "las fechas deben tener formato YYYY-MM-DD"));
    } catch (SinDisponibilidadException e) {
      return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
    }
  }

  /** Consulta segura: exige el correo con el que se reservó, y se topa por IP. */
  @GetMapping("/api/reservas/{codigo}")
  public ResponseEntity<?> consultar(@PathVariable String codigo, @RequestParam String email,
                                     HttpServletRequest peticion) {
    if (!lecturas.permitir(peticion.getRemoteAddr())) return Demasiadas();
    var reserva = svc.consultar(codigo, email);
    if (reserva.isEmpty()) {
      return ResponseEntity.status(404).body(Map.of("error", "reserva no encontrada"));
    }
    return ResponseEntity.ok(ReservaResp.de(reserva.get(), null));
  }

  /**
   * Comprobante público: lo acordado, la habitación, el hotel y el historial. La misma compuerta
   * que la consulta (código + correo): nadie imprime reservas ajenas adivinando el código.
   */
  @GetMapping("/api/reservas/{codigo}/comprobante")
  public ResponseEntity<?> comprobante(@PathVariable String codigo, @RequestParam String email,
                                        HttpServletRequest peticion) {
    if (!lecturas.permitir(peticion.getRemoteAddr())) return Demasiadas();
    return comprobantes.comprobante(codigo, email)
      .map(ResponseEntity::ok)
      .orElseGet(() -> ResponseEntity.status(404).body(Map.of("error", "reserva no encontrada")));
  }

  /** 429 con un motivo que el huésped pueda entender. El frontend lo muestra tal cual. */
  private static ResponseEntity<Map<String, String>> Demasiadas() {
    return ResponseEntity.status(429).body(Map.of("error",
      "demasiadas consultas seguidas desde esta conexión. Espera un minuto e inténtalo de nuevo."));
  }
}