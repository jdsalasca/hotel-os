package co.hotel.admin;

import co.hotel.seguridad.LoginThrottle;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inicio de sesión del panel. Acepta correo y contraseña, autentica, y deja sesión
 * HTTP con el contexto persistido: el resto de rutas se protegen con el rol ADMIN. El cierre lo
 * atiende el filtro de logout de Spring en la misma ruta y responde 200 con JSON.
 */
@RestController
public class AdminAuthController {
  private static final Logger log = LoggerFactory.getLogger(AdminAuthController.class);

  private final AuthenticationManager autenticacion;
  private final LoginThrottle throttle;
  private final LoginThrottle throttleIp;
  private final HttpSessionSecurityContextRepository repoSesion = new HttpSessionSecurityContextRepository();

  public AdminAuthController(AuthenticationManager autenticacion,
                             @Qualifier("throttlePorCuenta") LoginThrottle throttle,
                             @Qualifier("throttlePorIp") LoginThrottle throttleIp) {
    this.autenticacion = autenticacion;
    this.throttle = throttle;
    this.throttleIp = throttleIp;
  }

  public record LoginReq(String email, String password) {}

  @PostMapping("/api/admin/login")
  public ResponseEntity<?> login(@RequestBody LoginReq req, HttpServletRequest peticion,
                                 HttpServletResponse respuesta) {
    String email = req.email() == null ? "" : req.email().trim().toLowerCase();
    // Dos topes, no uno. Por `correo|IP` frena el ataque a una cuenta; por IP sola frena el
    // credential stuffing, que cambia de correo en cada intento y no lo nota.
    String clave = email + "|" + peticion.getRemoteAddr();
    if (!throttle.permitir(clave) || !throttleIp.permitir(peticion.getRemoteAddr()))
      return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .body(Map.of("error", "demasiados intentos fallidos. Espera 15 minutos."));

    try {
      Authentication auth = autenticacion.authenticate(
        new UsernamePasswordAuthenticationToken(email, req.password()));
      SecurityContextHolder.getContext().setAuthentication(auth);
      repoSesion.saveContext(SecurityContextHolder.getContext(), peticion, respuesta);
      peticion.getSession(true);
      throttle.exito(clave);
      throttleIp.exito(peticion.getRemoteAddr());
      log.info("inicio de sesión administrativo correcto");
      return ResponseEntity.ok(Map.of("estado", "autenticado", "rol", auth.getAuthorities()));
    } catch (AuthenticationException e) {
      throttle.fallo(clave);
      throttleIp.fallo(peticion.getRemoteAddr());
      log.warn("inicio de sesión fallido para {}", email);
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(Map.of("error", "credenciales inválidas"));
    }
  }
}