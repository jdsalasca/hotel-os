package co.hotel.config;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Cadena de seguridad del panel.
 *
 * CSRF con cookie doble: React lee XSRF-TOKEN y lo reenvía en X-XSRF-TOKEN, así que la protección
 * queda activa y el navegador puede operar. Desactivar CSRF "para que React funcione" abriría la
 * escritura pública a cualquier sitio.
 *
 * El atributo de solicitud se pone a null a propósito: sin eso, Spring difiere la creación del
 * token y, como ninguna vista del servidor lo lee, la cookie nunca se emite y todo POST del SPA
 * responde 403. Es la configuración recomendada para clientes sin JSP.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    var csrfCookie = CookieCsrfTokenRepository.withHttpOnlyFalse();
    csrfCookie.setCookiePath("/");

    var atributoCsrf = new CsrfTokenRequestAttributeHandler();
    atributoCsrf.setCsrfRequestAttributeName(null);

    http.csrf(c -> c.csrfTokenRepository(csrfCookie).csrfTokenRequestHandler(atributoCsrf))
      .sessionManagement(s -> s.sessionFixation().changeSessionId())
      .authorizeHttpRequests(a -> a
        // Spring reenvía los errores de validación a /error. Si ese reenvío queda denegado, un
        // mal parámetro responde 403 y parece un fallo de sesión en lugar de un 400 con mensaje.
        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
        .requestMatchers("/api/health", "/api/hotel", "/api/reservas", "/api/reservas/**", "/api/disponibilidad").permitAll()
        .requestMatchers("/api/admin/init", "/api/admin/login").permitAll()
        .requestMatchers("/api/admin/**").hasRole("ADMIN")
        .anyRequest().denyAll())
      .logout(l -> l.logoutUrl("/api/admin/logout").deleteCookies("JSESSIONID"))
      .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) ->
        res.sendError(401, "se requiere sesión administrativa")));
    return http.build();
  }
}