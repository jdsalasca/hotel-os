package co.hotel.admin;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Quién es el administrador de esta sesión. Sin sesión responde 401 desde el entry point,
 * igual que el resto del panel: el frontend lo usa para el "bienvenido de vuelta" sin
 * adivinar. Vale para contraseña y para Google, porque en ambos el nombre de la sesión es
 * el correo; el nombre visible sale de la ficha y cae al correo si está vacía.
 */
@RestController
public class AdminSesionController {

  private final JdbcTemplate jdbc;

  public AdminSesionController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @GetMapping("/api/admin/sesion")
  public ResponseEntity<?> sesion(Authentication auth) {
    if (auth == null || !auth.isAuthenticated()) {
      return ResponseEntity.status(401).body(Map.of("error", "sin sesión"));
    }
    String nombre = "";
    try {
      nombre = jdbc.queryForObject("SELECT nombre FROM users WHERE email=?", String.class,
        auth.getName());
      if (nombre == null) nombre = "";
    } catch (Exception e) {
      nombre = "";
    }
    return ResponseEntity.ok(Map.of("email", auth.getName(), "nombre", nombre, "rol", "ADMIN"));
  }

  public record NombreReq(String nombre) {}

  /**
   * Nombre visible ajustable por el dueño de la sesión. Con Google lo pone el login;
   * con clave no hay quién, y nadie merece verse como un correo cortado para siempre.
   */
  @PostMapping("/api/admin/perfil")
  public ResponseEntity<?> perfil(@RequestBody NombreReq req, Authentication auth) {
    if (auth == null || !auth.isAuthenticated()) {
      return ResponseEntity.status(401).body(Map.of("error", "sin sesión"));
    }
    String nombre = req == null || req.nombre() == null ? "" : req.nombre().trim();
    if (nombre.length() < 2 || nombre.length() > 80) {
      return ResponseEntity.badRequest()
        .body(Map.of("error", "el nombre necesita entre 2 y 80 caracteres"));
    }
    jdbc.update("UPDATE users SET nombre=? WHERE email=?", nombre, auth.getName());
    return ResponseEntity.ok(Map.of("nombre", nombre));
  }
}
