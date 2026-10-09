package co.hotel.huespedes;

import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lo que ve el huésped con sesión: quién es y qué reservas tiene suyas.
 *
 * Las reservas salen por `usuario_id`, no por correo: si dos cuentas comparten correo, cada una
 * ve solo las suyas. Las reservas anónimas (sin cuenta) no aparecen aquí, y siguen siendo
 * consultables por código + correo como siempre.
 */
@RestController
public class HuespedController {
  private final UsuariosHuespedRepository usuarios;
  private final ReservaServiceHuesped reservas;
  private final co.hotel.reservas.ComprobanteService comprobantes;
  private final co.hotel.reservas.ReservaService servicioReservas;

  public HuespedController(UsuariosHuespedRepository usuarios, ReservaServiceHuesped reservas,
      co.hotel.reservas.ComprobanteService comprobantes,
      co.hotel.reservas.ReservaService servicioReservas) {
    this.usuarios = usuarios;
    this.reservas = reservas;
    this.comprobantes = comprobantes;
    this.servicioReservas = servicioReservas;
  }

  /**
   * 401 si no hay sesión (lo corta el filtro antes de llegar aquí). Con sesión pero sin fila
   * de huésped —el personal del panel que aún no reserva— responde vacío, no 401: ya está
   * dentro, solo que todavía no tiene nada.
   */
  @GetMapping("/api/yo")
  public ResponseEntity<?> yo() {
    String email = currentEmail();
    if (email == null) return ResponseEntity.status(401).body(Map.of("error", "sin sesión"));
    Long id = usuarios.idPorEmail(email);
    return ResponseEntity.ok(Map.of(
      "email", email,
      "nombre", id == null ? "" : usuarios.nombrePorId(id),
      "tieneReservas", id != null && reservas.tieneAlguna(id)));
  }

  @GetMapping("/api/mis-reservas")
  public ResponseEntity<?> misReservas() {
    Long id = idActual();
    if (id == null) return ResponseEntity.ok(Map.of("reservas", java.util.List.of()));
    List<Map<String, Object>> lista = reservas.de(id);
    return ResponseEntity.ok(Map.of("reservas", lista));
  }

  /**
   * Comprobante completo de una reserva propia, sin pasar el correo por la URL: la sesión ya
   * dice de quién es. La ajena responde 404 (no 403, para no confirmar que existe).
   */
  @GetMapping("/api/mis-reservas/{codigo}/comprobante")
  public ResponseEntity<?> comprobantePropio(@PathVariable String codigo) {
    Long id = idActual();
    if (id == null)
      return ResponseEntity.status(401).body(Map.of("error", "sin sesión"));
    Long duena = reservas.duenaDe(codigo);
    if (duena == null || !duena.equals(id))
      return ResponseEntity.status(404).body(Map.of("error", "reserva no existe"));
    return comprobantes.comprobante(codigo, null)
      .map(c -> ResponseEntity.ok().body((Object) c))
      .orElse(ResponseEntity.status(404).body(Map.of("error", "reserva no existe")));
  }

  public record CambiarFechasReq(String llegada, String salida) {}

  public record CambiarHuespedesReq(Integer huespedes) {}

  /**
   * El huésped mueve sus fechas sin llamar al hotel: solo las suyas (la ajena es 404,
   * igual que en el comprobante propio), solo vigentes y solo a noches libres y
   * vendibles, con el precio recalculado. 400 con fechas ausentes o invertidas.
   */
  @PostMapping("/api/mis-reservas/{codigo}/fechas")
  public ResponseEntity<?> cambiarFechas(@PathVariable String codigo,
      @RequestBody(required = false) CambiarFechasReq req) {
    Long id = idActual();
    if (id == null)
      return ResponseEntity.status(401).body(Map.of("error", "sin sesión"));
    Long duena = reservas.duenaDe(codigo);
    if (duena == null || !duena.equals(id))
      return ResponseEntity.status(404).body(Map.of("error", "reserva no existe"));
    final java.time.LocalDate llegada;
    final java.time.LocalDate salida;
    try {
      llegada = java.time.LocalDate.parse(req.llegada());
      salida = java.time.LocalDate.parse(req.salida());
    } catch (java.time.format.DateTimeParseException | NullPointerException e) {
      return ResponseEntity.badRequest().body(Map.of("error", "las fechas deben tener formato YYYY-MM-DD"));
    }
    try {
      var movida = servicioReservas.cambiarFechas(codigo, llegada, salida,
        "huésped:" + currentEmail());
      return ResponseEntity.ok(Map.of(
        "codigo", movida.codigo(),
        "llegada", movida.llegada().toString(),
        "salida", movida.salida().toString(),
        "huespedes", movida.huespedes(),
        "estado", movida.estado().name()));
    } catch (co.hotel.reservas.ExcepcionDeEstado | co.hotel.reservas.SinDisponibilidadException e) {
      return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
    } catch (co.hotel.reservas.DatosInvalidosException e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
  }

  /**
   * El huésped avisa de que viene con más gente de la que había-booked. Mismas reglas que el
   * panel: solo suyas (la ajena es 404, no 403, para no confirmar que existe), solo vigentes y
   * solo si la habitación admite al grupo. Sin mover fechas, el hueco bloqueado no cambia.
   */
  @PostMapping("/api/mis-reservas/{codigo}/huespedes")
  public ResponseEntity<?> cambiarHuespedes(@PathVariable String codigo,
      @RequestBody(required = false) CambiarHuespedesReq req) {
    Long id = idActual();
    if (id == null)
      return ResponseEntity.status(401).body(Map.of("error", "sin sesión"));
    Long duena = reservas.duenaDe(codigo);
    if (duena == null || !duena.equals(id))
      return ResponseEntity.status(404).body(Map.of("error", "reserva no existe"));
    if (req == null || req.huespedes() == null)
      return ResponseEntity.badRequest().body(Map.of("error", "huéspedes requerido"));
    try {
      var movida = servicioReservas.cambiarHuespedes(codigo, req.huespedes(),
        "huésped:" + currentEmail());
      return ResponseEntity.ok(Map.of(
        "codigo", movida.codigo(),
        "llegada", movida.llegada().toString(),
        "salida", movida.salida().toString(),
        "huespedes", movida.huespedes(),
        "estado", movida.estado().name()));
    } catch (co.hotel.reservas.ExcepcionDeEstado
        | co.hotel.reservas.SinDisponibilidadException e) {
      return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
    } catch (co.hotel.reservas.DatosInvalidosException e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
  }

  /**
   * Cierre de sesión del huésped. Es POST (no un GET) porque cierra sesión, y lleva CSRF como
   * cualquier otra escritura.
   */
  @PostMapping("/api/huesped/logout")
  public ResponseEntity<?> salir(jakarta.servlet.http.HttpServletRequest peticion) {
    var sesion = peticion.getSession(false);
    if (sesion != null) sesion.invalidate();
    org.springframework.security.core.context.SecurityContextHolder.clearContext();
    return ResponseEntity.ok(Map.of("estado", "sesión cerrada"));
  }

  private String currentEmail() { return ActualCorreo.deSesion(); }
  private Long idActual() {
    String email = currentEmail();
    return email == null ? null : usuarios.idPorEmail(email);
  }
}