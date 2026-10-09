package co.hotel.seguridad;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import co.hotel.config.HotelProperties;

/**
 * Entrada al panel con Google. Estar autenticado en Google no basta: el correo tiene que estar en
 * la allowlist, o cualquier cuenta de Google entraría a administrar el hotel.
 *
 * Quien entra sale con ROLE_ADMIN, igual que con contraseña: el resto de rutas no distingue cómo
 * se autenticó cada sesión. Y el nombre de la sesión es el correo, también igual que con
 * contraseña, para que la auditoría muestre quién fue y no un sub opaco.
 */
@Component
public class ManejadorAdminOauth2 implements AuthenticationSuccessHandler {
  private static final Logger log = LoggerFactory.getLogger(ManejadorAdminOauth2.class);

  private final Set<String> permitidos;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;
  private final org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler continuacion =
    new org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler();
  private final HttpSessionSecurityContextRepository repoSesion =
    new HttpSessionSecurityContextRepository();

  @Autowired
  public ManejadorAdminOauth2(HotelProperties props,
      org.springframework.jdbc.core.JdbcTemplate jdbc) {
    this(props.oauth2().correosAdministradores(), jdbc);
    // Sin petición guardada (entrar directo por el botón), al panel y no a la home: con Google
    // no hay pantalla de login que redirija, así que este default es el que orienta.
    continuacion.setDefaultTargetUrl("/admin");
  }

  /** Visible para pruebas: la allowlist sin pasar por propiedades. */
  ManejadorAdminOauth2(Set<String> permitidos,
      org.springframework.jdbc.core.JdbcTemplate jdbc) {
    this.permitidos = permitidos;
    this.jdbc = jdbc;
  }

  @Override
  public void onAuthenticationSuccess(HttpServletRequest req, HttpServletResponse res,
                                      Authentication auth) throws IOException, ServletException {
    String email = auth.getPrincipal() instanceof OAuth2User p ? p.getAttribute("email") : null;
    if (email == null || !permitidos.contains(email.trim().toLowerCase())) {
      // Sin sesión: un redirect no sirve si el contexto queda autenticado.
      SecurityContextHolder.clearContext();
      log.warn("Google autenticó a {}, pero no está en la allowlist del panel", email);
      res.sendRedirect("/admin/entrar?error=denegado");
      return;
    }

    OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) auth;
    OAuth2User principal = token.getPrincipal();
    // El nombre visible sale de Google y se guarda: es el "bienvenido de vuelta" sin pedir
    // nada más. Solo se escribe si Google trae algo no vacío, para no borrar un nombre puesto
    // a mano con un login sin atributos.
    try {
      String visible = texto(principal.getAttribute("given_name"));
      if (visible.isEmpty()) visible = texto(principal.getAttribute("name"));
      if (!visible.isEmpty()) {
        jdbc.update("UPDATE users SET nombre=? WHERE email=?", visible, email);
      }
      // El nombre de la sesión es el correo: DefaultOAuth2User lo toma del atributo nombrado.
      var conNombre = new DefaultOAuth2User(principal.getAuthorities(), principal.getAttributes(), "email");
      var conRol = new OAuth2AuthenticationToken(conNombre,
        List.of(new SimpleGrantedAuthority("ROLE_ADMIN")), token.getAuthorizedClientRegistrationId());
      SecurityContextHolder.getContext().setAuthentication(conRol);
      // El redirect no guarda el contexto: hay que hacerlo a mano, igual que en el huésped.
      repoSesion.saveContext(SecurityContextHolder.getContext(), req, res);
      log.info("entrada al panel con Google: {}", email);
      continuacion.onAuthenticationSuccess(req, res, conRol);
    } catch (Exception e) {
      // Caerse a mitad (base caída, atributo raro) no puede ser un 500 mudo: se limpia,
      // se registra con la causa y se vuelve a la puerta con motivo, como siempre.
      SecurityContextHolder.clearContext();
      log.warn("entrada al panel con Google falló para {}: {}: {}", email,
        e.getClass().getSimpleName(), e.getMessage());
      res.sendRedirect("/admin/entrar?error=oauth2");
    }
  }

  private static String texto(Object valor) {
    return valor == null ? "" : valor.toString().trim();
  }
}