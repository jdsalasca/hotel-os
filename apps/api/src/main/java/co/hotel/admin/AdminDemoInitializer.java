package co.hotel.admin;

import co.hotel.config.HotelProperties;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Usuario de demostración para desarrollo: admin / admin.
 *
 * Existe para poder recorrer el panel sin montar el arranque con token. Se activa solo si el hotel
 * lo pide explícitamente (hotel.demo.admin=true) y, además, se niega a arrancar en producción.
 *
 * Es un compromiso deliberado: preferimos un arranque ruidoso a un despliegue con una credencial
 * conocida. En producción el administrador real se crea con ADMIN_INIT_TOKEN, con una contraseña
 * que elige el hotel y que no vive en este repositorio.
 */
@Component
@ConditionalOnProperty(name = "hotel.demo.admin", havingValue = "true")
public class AdminDemoInitializer implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(AdminDemoInitializer.class);

  static final String EMAIL = "admin";
  static final String CLAVE = "admin";

  private final JdbcTemplate jdbc;
  private final PasswordEncoder encoder;
  private final HotelProperties props;

  public AdminDemoInitializer(JdbcTemplate jdbc, PasswordEncoder encoder, HotelProperties props) {
    this.jdbc = jdbc;
    this.encoder = encoder;
    this.props = props;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!props.permiteAtajosDeDesarrollo())
      throw new IllegalStateException(
        "hotel.demo.admin=true no puede combinarse con hotel.ambiente=produccion: "
          + "el usuario admin/admin es solo para desarrollo. Usa ADMIN_INIT_TOKEN para crear "
          + "el administrador real.");

    Integer usuarios = jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
    if (usuarios != null && usuarios > 0) return;

    jdbc.update("INSERT INTO users(email,hash,rol,activo,creado_en) VALUES(?,?,'ADMIN',1,?)",
      EMAIL, encoder.encode(CLAVE), LocalDateTime.now().toString());
    log.warn("creado usuario de DEMOSTRACION {} / {} — solo en desarrollo, cámbialo antes de operar",
      EMAIL, CLAVE);
  }
}