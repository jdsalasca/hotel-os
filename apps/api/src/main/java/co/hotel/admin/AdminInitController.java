package co.hotel.admin;

import co.hotel.config.HotelProperties;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Arranque del primer administrador con un secreto del entorno. Un solo uso: en cuanto existe un
 * usuario, la ruta deja de crear cuentas. No hay contraseña por defecto ni usuario en memoria.
 */
@RestController
public class AdminInitController {
  private static final Logger log = LoggerFactory.getLogger(AdminInitController.class);
  private static final int LONGITUD_MINIMA = 12;

  private final JdbcTemplate jdbc;
  private final PasswordEncoder encoder;
  private final HotelProperties props;

  public AdminInitController(JdbcTemplate jdbc, PasswordEncoder encoder, HotelProperties props) {
    this.jdbc = jdbc;
    this.encoder = encoder;
    this.props = props;
  }

  public record InitReq(String token, String email, String password) {}

  @PostMapping("/api/admin/init")
  public java.util.Map<String, String> init(@RequestBody InitReq req) {
    if (!props.hayTokenInicial())
      throw new ResponseStatusException(HttpStatus.FORBIDDEN,
        "arranque deshabilitado: define ADMIN_INIT_TOKEN en el entorno");
    if (!props.adminInitToken().equals(req.token()))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "token inválido");
    if (req.password() == null || req.password().length() < LONGITUD_MINIMA)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
        "la contraseña debe tener al menos " + LONGITUD_MINIMA + " caracteres");

    Integer usuarios = jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
    if (usuarios != null && usuarios > 0) return java.util.Map.of("estado", "ya existe administrador");

    jdbc.update("INSERT INTO users(email,hash,rol,activo,creado_en) VALUES(?,?,'ADMIN',1,?)",
      normalizar(req.email()), encoder.encode(req.password()), LocalDateTime.now().toString());
    log.info("primer administrador creado");
    return java.util.Map.of("estado", "administrador creado");
  }

  private String normalizar(String email) {
    return email == null ? "" : email.trim().toLowerCase();
  }
}