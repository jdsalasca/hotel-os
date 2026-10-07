package co.hotel.admin;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Quién es el administrador de esta sesión. Sin sesión responde 401 desde el entry point,
 * igual que el resto del panel: el frontend lo usa para el "Hola, {correo}" sin adivinar.
 * Vale para contraseña y para Google, porque en ambos el nombre de la sesión es el correo.
 */
@RestController
public class AdminSesionController {

  @GetMapping("/api/admin/sesion")
  public ResponseEntity<?> sesion(Authentication auth) {
    if (auth == null || !auth.isAuthenticated()) {
      return ResponseEntity.status(401).body(Map.of("error", "sin sesión"));
    }
    return ResponseEntity.ok(Map.of("email", auth.getName(), "rol", "ADMIN"));
  }
}
