package co.hotel.admin;

import co.hotel.config.HotelProperties;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Superusuarios sembrados desde el entorno (HOTEL_SEED_ADMINS="email:clave,email:clave").
 *
 * Solo crea los que faltan: si el correo ya existe en users, su contraseña se respeta y no se
 * toca. Por eso es seguro dejar la variable puesta para siempre: el día que se borre la tabla,
 * los superusuarios vuelven a nacer con la clave del entorno. Toda cuenta sembrada nace con
 * debe_cambiar_clave=1, así que la clave del entorno es de un solo uso: al primer login se exige
 * cambiarla y jamás vuelve a valer.
 */
@Component
public class AdminSeedInitializer implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(AdminSeedInitializer.class);

  /** Mínimo para la semilla. El cambio obligatorio posterior sí exige 12 (ver AdminInitController). */
  static final int LONGITUD_MINIMA_SEMILLA = 8;

  private final JdbcTemplate jdbc;
  private final PasswordEncoder encoder;
  private final HotelProperties props;

  public AdminSeedInitializer(JdbcTemplate jdbc, PasswordEncoder encoder, HotelProperties props) {
    this.jdbc = jdbc;
    this.encoder = encoder;
    this.props = props;
  }

  @Override
  public void run(ApplicationArguments args) {
    List<String[]> pares = parsear(props.seedAdmins());
    if (pares.isEmpty()) return;

    int creados = 0;
    for (String[] par : pares) {
      String email = par[0].trim().toLowerCase();
      String clave = par[1];
      if (!email.contains("@") || clave == null || clave.length() < LONGITUD_MINIMA_SEMILLA) {
        log.warn("semilla de superusuario ignorada por formato (revisa HOTEL_SEED_ADMINS)");
        continue;
      }
      Integer existe = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Integer.class,
        email);
      if (existe != null && existe > 0) continue;
      jdbc.update(
        "INSERT INTO users(email,hash,rol,activo,creado_en,debe_cambiar_clave) VALUES(?,?,'ADMIN',1,?,1)",
        email, encoder.encode(clave), LocalDateTime.now().toString());
      creados++;
    }
    if (creados > 0)
      log.warn("sembrados {} superusuarios: entran una vez con la clave del entorno y deben cambiarla",
        creados);
  }

  static List<String[]> parsear(String crudo) {
    List<String[]> pares = new ArrayList<>();
    if (crudo == null || crudo.isBlank()) return pares;
    for (String parte : crudo.split(",")) {
      int dos = parte.indexOf(':');
      if (dos <= 0) continue;
      pares.add(new String[] {parte.substring(0, dos), parte.substring(dos + 1)});
    }
    return pares;
  }
}
