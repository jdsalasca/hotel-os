package co.hotel.reservas;

import co.hotel.huespedes.ReservaServiceHuesped;

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
  private final ReservaServiceHuesped servicioHuespedes;

  public ReservaController(ReservaService svc, RoomSelector rooms, ComprobanteService comprobantes,
                           LimiteReservas limite, LimiteConsultasPublicas lecturas,
                           ReservaServiceHuesped servicioHuespedes) {
    this.svc = svc;
    this.rooms = rooms;
    this.comprobantes = comprobantes;
    this.limite = limite;
    this.lecturas = lecturas;
    this.servicioHuespedes = servicioHuespedes;
  }

  /** Contrato de entrada del flujo público. El cliente envía ISO-8601 (YYYY-MM-DD). */
  public record CrearReq(String email, String nombre, String llegada, String salida,
                         Integer huespedes, Long roomId, String origen, String idempotencia,
                         Long totalEsperadoCents, String monedaEsperada, Long ratePlanIdEsperado) {}

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
      // Las fechas son obligatorias y LocalDate.parse(null) revienta con NPE: un cuerpo sin
      // fechas debe ser un 400 con motivo, no un 500.
      if (req.llegada() == null || req.salida() == null) {
        return ResponseEntity.badRequest().body(Map.of("error", "fechas requeridas"));
      }
      LocalDate llegada = LocalDate.parse(req.llegada());
      LocalDate salida = LocalDate.parse(req.salida());
      int h = req.huespedes() == null ? 0 : req.huespedes();
      Long habitacion = req.roomId();
      if (habitacion == null) {
        // Sin habitación elegida se ofrece la primera vendible, no la primera libre: la libre
        // sin precio o sin capacidad se rechaza igual en el servicio. Y cero huéspedes es un 400
        // con su motivo, no un 409 que culpe a la disponibilidad.
        if (h < 1) {
          return ResponseEntity.badRequest().body(Map.of("error", "número de huéspedes inválido"));
        }
        habitacion = rooms.primeraDisponible(llegada, salida, h);
        if (habitacion == null) {
          return ResponseEntity.status(409).body(Map.of("error",
            "no hay habitaciones a la venta para esas fechas y huéspedes"));
        }
      }
      var datos = new CrearReserva(req.email(), req.nombre(), llegada, salida,
        h, Origen.WEB, clave, habitacion);
      // El importe que el huésped vio en la búsqueda viaja con la petición: si la tarifa se
      // movió entre medias, el servicio lo rechaza con el vigente en vez de colarlo.
      String codigo = svc.crear(datos, req.totalEsperadoCents(), req.monedaEsperada(),
        req.ratePlanIdEsperado());
      // Si quien reserva tiene sesión de huésped, la reserva queda colgando de su cuenta para que
      // la vea en "Mis reservas". Si no, usuario_id queda NULL y sigue siendo consultable por
      // código + correo: nadie pierde su reserva por no haber entrado con Google. El personal del
      // panel reserva para sí mismo con su propio correo: ahí se le crea su identidad de huésped.
      // Si reserva para otro correo (recepción), no se engancha a nadie.
      String correoSesion = co.hotel.huespedes.ActualCorreo.deSesion();
      Long idUsuario = correoSesion == null ? null : servicioHuespedes.idPorEmail(correoSesion);
      if (idUsuario == null && correoSesion != null && esAdmin()
        && correoSesion.equalsIgnoreCase(req.email())) {
        idUsuario = servicioHuespedes.identidadPanelPara(correoSesion);
      }
      if (idUsuario != null) servicioHuespedes.vincular(codigo, idUsuario);
      return ResponseEntity.status(201).body(ReservaResp.de(svc.buscar(codigo).orElseThrow(),
        "Reserva registrada. Queda pendiente de confirmación: el hotel aún no ha configurado pago ni "
          + "confirmación automática."));
    } catch (DatosInvalidosException e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    } catch (DateTimeParseException e) {
      return ResponseEntity.badRequest().body(Map.of("error", "las fechas deben tener formato YYYY-MM-DD"));
    } catch (ConflictoIdempotenciaException e) {
      return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
    } catch (PrecioCambiadoException e) {
      return ResponseEntity.status(409).body(Map.of("error", e.getMessage(),
        "nuevoTotalCents", e.nuevoTotalCents(), "nuevaMoneda", e.nuevaMoneda(),
        "nuevoRatePlanId", e.nuevoRatePlanId()));
    } catch (SinDisponibilidadException e) {
      return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
    }
  }

  /** El personal con sesión ADMIN reserva para sí mismo sin perder el panel. */
  private static boolean esAdmin() {
    var auth = org.springframework.security.core.context.SecurityContextHolder.getContext()
      .getAuthentication();
    return auth != null && auth.getAuthorities().stream()
      .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
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

  public record CancelarReq(String email) {}

  /**
   * El huésped cancela su reserva con código y correo: la misma compuerta que la consulta, así
   * que nadie cancela reservas ajenas adivinando el código. Solo viaja de PENDIENTE o
   * CONFIRMADA a CANCELADA; lo cerrado no se toca. El actor queda en la auditoría con el
   * prefijo de huésped para distinguirlo del personal.
   */
  @PostMapping("/api/reservas/{codigo}/cancelar")
  public ResponseEntity<?> cancelar(@PathVariable String codigo, @RequestBody CancelarReq req,
                                    HttpServletRequest peticion) {
    if (!lecturas.permitir(peticion.getRemoteAddr())) return Demasiadas();
    String email = req.email() == null ? "" : req.email().trim();
    if (svc.consultar(codigo, email).isEmpty()) {
      return ResponseEntity.status(404).body(Map.of("error", "reserva no encontrada"));
    }
    try {
      var cancelada = svc.cambiarEstado(codigo, EstadoReserva.CANCELADA, "huésped:" + email);
      return ResponseEntity.ok(ReservaResp.de(cancelada,
        "Reserva cancelada. Si tus planes cambian, aquí te esperamos."));
    } catch (ExcepcionDeEstado e) {
      return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
    }
  }
}