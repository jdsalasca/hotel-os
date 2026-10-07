package co.hotel.config;

import co.hotel.ota.HttpClienteOta;
import co.hotel.reservas.SqliteDataSources;
import co.hotel.seguridad.LoginThrottle;
import java.time.Duration;
import javax.sql.DataSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.JdbcUserDetailsManager;

@Configuration
@EnableConfigurationProperties(HotelProperties.class)
public class AppConfig {

  /**
   * El DataSource decide el modo de concurrencia de SQLite (WAL + BEGIN IMMEDIATE).
   * Ver {@link SqliteDataSources} para por qué esto no es negociable con una sola instancia.
   */
  @Bean
  public DataSource dataSource(HotelProperties props) {
    return SqliteDataSources.paraRuta(props.jdbcPath());
  }

  @Bean
  public JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }

  /** El cliente HTTP lo comparten los conectores OTA y el envío de correo. */
  @Bean
  public HttpClienteOta httpClienteOta() {
    return new HttpClienteOta(Duration.ofSeconds(20), 3, Duration.ofMillis(500));
  }

  @Bean
  public CorreoProperties correoProperties(HotelProperties props) { return props.correo(); }

  /**
   * DelegatingPasswordEncoder: el hash se guarda con prefijo de algoritmo ({bcrypt}), que es lo que
   * espera Spring al autenticar. Un BCryptPasswordEncoder pelado devuelve matches()=false para
   * toda contraseña y el login nunca valida.
   */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

@Bean
  public AuthenticationManager authenticationManager(JdbcTemplate jdbc, PasswordEncoder encoder) {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider(usuarios(jdbc.getDataSource()));
    provider.setPasswordEncoder(encoder);
    return new ProviderManager(provider);
  }

  /**
   * Dos contadores de intentos, porque protegen cosas distintas: por `correo|IP` frena el ataque a
   * una cuenta y por IP sola frena el credential stuffing, que cambia de correo en cada intento y
   * no toca ninguno de los anteriores. Con un solo bean no se pueden tener las dos cosas.
   */
  @Bean
  public LoginThrottle throttlePorCuenta() { return new LoginThrottle(); }

  @Bean
  public LoginThrottle throttlePorIp() { return new LoginThrottle(); }

  private JdbcUserDetailsManager usuarios(DataSource ds) {
    JdbcUserDetailsManager manager = new JdbcUserDetailsManager(ds);
    manager.setUsersByUsernameQuery("SELECT email, hash, activo FROM users WHERE email = ? AND activo = 1");
    manager.setAuthoritiesByUsernameQuery("SELECT email, 'ROLE_' || rol FROM users WHERE email = ?");
    return manager;
  }
}