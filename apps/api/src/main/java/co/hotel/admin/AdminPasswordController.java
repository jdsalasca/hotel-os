package co.hotel.admin;

import co.hotel.seguridad.LoginThrottle;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cambio de la contraseña inicial sin sesión. Es la segunda mitad del login con
 * debe_cambiar_clave: como aún no hay sesión, la única prueba de identidad es la contraseña
 * actual, así que lleva el mismo doble tope de intentos que el login. La nueva debe tener al
 * menos 12 caracteres y ser distinta de la actual.
 */
@RestController
public class AdminPasswordController {
  private static final Logger log = LoggerFactory.getLogger(AdminPasswordController.class);
  private static final int LONGITUD_MINIMA = 12;

  private final JdbcTemplate jdbc;
  private final PasswordEncoder encoder;
  private final LoginThrottle throttle;
  private final LoginThrottle throttleIp;

  public AdminPasswordController(JdbcTemplate jdbc, PasswordEncoder encoder,
                                 @Qualifier("throttlePorCuenta") LoginThrottle throttle,
                                 @Qualifier("throttlePorIp") LoginThrottle throttleIp) {
    this.jdbc = jdbc;
    this.encoder = encoder;
    this.throttle = throttle;
    this.throttleIp = throttleIp;
  }

  public record CambioReq(String email, String actual, String nueva) {}

  @PostMapping("/api/admin/password")
  public ResponseEntity<?> cambiar(@RequestBody CambioReq req, HttpServletRequest peticion) {
    String email = req.email() == null ? "" : req.email().trim().toLowerCase();
    String clave = email + "|" + peticion.getRemoteAddr();
    if (!throttle.permitir(clave) || !throttleIp.permitir(peticion.getRemoteAddr())) {
      long espera = Math.max(throttle.segundosRestantes(clave),
        throttleIp.segundosRestantes(peticion.getRemoteAddr()));
      long minutos = Math.max(1, (espera + 59) / 60);
      return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .body(Map.of("error", "demasiados intentos fallidos. Espera " + minutos
          + (minutos == 1 ? " minuto." : " minutos."), "reintentar_en_seg", espera));
    }

    String hash;
    try {
      hash = jdbc.queryForObject("SELECT hash FROM users WHERE email = ? AND activo = 1",
        String.class, email);
    } catch (Exception e) {
      hash = null;
    }
    if (hash == null || req.actual() == null || !encoder.matches(req.actual(), hash)) {
      throttle.fallo(clave);
      throttleIp.fallo(peticion.getRemoteAddr());
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(Map.of("error", "credenciales inválidas"));
    }
    if (req.nueva() == null || req.nueva().length() < LONGITUD_MINIMA)
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(Map.of("error", "la nueva contraseña debe tener al menos " + LONGITUD_MINIMA
          + " caracteres"));
    if (encoder.matches(req.nueva(), hash))
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(Map.of("error", "la nueva contraseña debe ser distinta de la actual"));

    jdbc.update("UPDATE users SET hash = ?, debe_cambiar_clave = 0 WHERE email = ?",
      encoder.encode(req.nueva()), email);
    throttle.exito(clave);
    throttleIp.exito(peticion.getRemoteAddr());
    log.info("contraseña inicial cambiada");
    return ResponseEntity.ok(Map.of("estado", "clave actualizada"));
  }
}
