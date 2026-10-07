package co.hotel.huespedes;

import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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

  public HuespedController(UsuariosHuespedRepository usuarios, ReservaServiceHuesped reservas) {
    this.usuarios = usuarios;
    this.reservas = reservas;
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