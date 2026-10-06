package co.hotel.config;

import co.hotel.reservas.SqliteDataSources;
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
  public AuthenticationManager authenticationManager(DataSource ds, PasswordEncoder encoder) {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider(usuarios(ds));
    provider.setPasswordEncoder(encoder);
    return new ProviderManager(provider);
  }

  private JdbcUserDetailsManager usuarios(DataSource ds) {
    JdbcUserDetailsManager manager = new JdbcUserDetailsManager(ds);
    manager.setUsersByUsernameQuery("SELECT email, hash, activo FROM users WHERE email = ? AND activo = 1");
    manager.setAuthoritiesByUsernameQuery("SELECT email, 'ROLE_' || rol FROM users WHERE email = ?");
    return manager;
  }
}