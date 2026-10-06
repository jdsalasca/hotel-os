package co.hotel.config;

import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import javax.sql.DataSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
  // API stateless con Basic para admin; CSRF deshabilitado solo porque no hay cookies/sesión.
  // Cuando el panel React use sesión con cookies (R5), se activará CookieCsrfTokenRepository. No se desactiva para facilitar, sino por stateless.
  @Bean
  SecurityFilterChain chain(HttpSecurity http) throws Exception {
    http.csrf(c -> c.disable());
    http.authorizeHttpRequests(a -> a
      .requestMatchers("/api/health", "/api/reservas", "/api/reservas/**", "/api/admin/init").permitAll()
      .requestMatchers("/api/admin/**").hasRole("ADMIN")
      .anyRequest().denyAll());
    http.httpBasic(b -> {});
    return http.build();
  }

  @Bean
  UserDetailsService users(DataSource ds, PasswordEncoder enc) {
    JdbcUserDetailsManager m = new JdbcUserDetailsManager(ds);
    m.setUsersByUsernameQuery("SELECT email, hash, activo FROM users WHERE email=?");
    m.setAuthoritiesByUsernameQuery("SELECT email, 'ROLE_' || rol FROM users WHERE email=?");
    return m;
  }
}
