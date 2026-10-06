package co.hotel.admin;

import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
public class AdminInitController {
  private final JdbcTemplate jdbc;
  private final PasswordEncoder enc;
  private final String initToken;
  public AdminInitController(JdbcTemplate jdbc, PasswordEncoder enc, @Value("${ADMIN_INIT_TOKEN:}") String initToken) {
    this.jdbc = jdbc; this.enc = enc; this.initToken = initToken;
  }

  public record InitReq(String token, String email, String password) {}

  @PostMapping("/api/admin/init")
  public Map<String, String> init(@RequestBody InitReq req) {
    if (initToken == null || initToken.isBlank() || !initToken.equals(req.token()))
      throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "token inválido");
    if (req.password() == null || req.password().length() < 12)
      throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "contraseña mínima 12 caracteres");
    Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
    if (n != null && n > 0) return Map.of("estado", "ya existe administrador");
    jdbc.update("INSERT INTO users(email,hash,rol,activo,creado_en) VALUES(?,?,'ADMIN',1,?)", req.email(), enc.encode(req.password()), LocalDateTime.now().toString());
    return Map.of("estado", "administrador creado");
  }
}
