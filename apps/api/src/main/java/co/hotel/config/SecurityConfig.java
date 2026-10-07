package co.hotel.config;

import co.hotel.seguridad.EnrutadorOauth2;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

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

  private final HttpSessionSecurityContextRepository repoSesion =
    new HttpSessionSecurityContextRepository();

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http,
                                           ObjectProvider<ClientRegistrationRepository> registros,
                                           EnrutadorOauth2 enrutador) throws Exception {
    var csrfCookie = CookieCsrfTokenRepository.withHttpOnlyFalse();
    csrfCookie.setCookiePath("/");

    var atributoCsrf = new CsrfTokenRequestAttributeHandler();
    atributoCsrf.setCsrfRequestAttributeName(null);

    http.csrf(c -> c.csrfTokenRepository(csrfCookie).csrfTokenRequestHandler(atributoCsrf))
      .sessionManagement(s -> s.sessionFixation().changeSessionId())
      // Por defecto Spring Security 6 guarda el contexto en un atributo de la petición, no en la
      // sesión. Con login de Google eso rompe el flujo entero: se vuelve de Google a otra petición,
      // no hay contexto y /api/yo responde 401. Se fija el repositorio de sesión a propósito.
      .securityContext(s -> s.securityContextRepository(repoSesion))
      .authorizeHttpRequests(a -> a
        // Spring reenvía los errores de validación a /error. Si ese reenvío queda denegado, un
        // mal parámetro responde 403 y parece un fallo de sesión en lugar de un 400 con mensaje.
        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
        .requestMatchers("/api/health", "/api/hotel", "/api/reservas", "/api/reservas/**",
          "/api/disponibilidad", "/api/disponibilidad/calendario",
          "/api/disponibilidad/detalle").permitAll()
        .requestMatchers("/api/admin/init", "/api/admin/login").permitAll()
        // Sin estas dos, el flujo de Google caería en el denyAll de abajo: la ida a Google y la
        // vuelta con el código son peticiones sin sesión por definición.
        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
        // Lo del huésped con sesión: cada uno necesita su propia sesión para saber de quién es.
        // ROLE_HUESPED no llega a /api/admin/**, que exige ROLE_ADMIN.
        .requestMatchers("/api/yo", "/api/mis-reservas")
        .hasAnyRole("HUESPED", "ADMIN")
        .requestMatchers("/api/huesped/**").hasAnyRole("HUESPED", "ADMIN")
        .requestMatchers("/api/admin/**").hasRole("ADMIN")
        .anyRequest().denyAll())
      // El cierre es una API, no un formulario: responde 200 con JSON en vez de redirigir a
      // /login. El navegador ya ignoraba la redirección, pero un 302 en un POST de API es mentir.
      .logout(l -> l.logoutUrl("/api/admin/logout").deleteCookies("JSESSIONID")
        .logoutSuccessHandler((peticion, respuesta, auth) -> {
          respuesta.setStatus(HttpServletResponse.SC_OK);
          respuesta.setContentType("application/json;charset=UTF-8");
          respuesta.getWriter().write("{\"estado\":\"sesion cerrada\"}");
        }))
      .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) ->
        res.sendError(401, "se requiere sesión administrativa")));
    // El login con Google solo existe con Client ID: sin él no hay repositorio y la contraseña
    // queda como único método. ObjectProvider en vez de inyección directa para no tumbar el
    // arranque cuando Google no está configurado.
    var repo = registros.getIfAvailable();
    if (repo != null) {
      http.oauth2Login(o -> o
        .clientRegistrationRepository(repo)
        .successHandler(enrutador)
        .failureHandler((peticion, respuesta, ex) -> {
          // La vuelta fallida trae el registro en la ruta (/login/oauth2/code/{registro}):
          // cada entrada vuelve a su puerta, no a la del otro.
          String destino = peticion.getRequestURI().endsWith(EnrutadorOauth2.HUESPED)
            ? "/?error=oauth2" : "/admin/entrar?error=oauth2";
          respuesta.sendRedirect(destino);
        }));
    }
    return http.build();
  }
}