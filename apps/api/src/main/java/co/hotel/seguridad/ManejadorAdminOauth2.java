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
  private final AuthenticationSuccessHandler continuacion =
    new SavedRequestAwareAuthenticationSuccessHandler();
  private final HttpSessionSecurityContextRepository repoSesion =
    new HttpSessionSecurityContextRepository();

  @Autowired
  public ManejadorAdminOauth2(HotelProperties props) {
    this(props.oauth2().correosAdministradores());
  }

  /** Visible para pruebas: la allowlist sin pasar por propiedades. */
  ManejadorAdminOauth2(Set<String> permitidos) { this.permitidos = permitidos; }

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
    // El nombre de la sesión es el correo: DefaultOAuth2User lo toma del atributo nombrado.
    var conNombre = new DefaultOAuth2User(principal.getAuthorities(), principal.getAttributes(), "email");
    var conRol = new OAuth2AuthenticationToken(conNombre,
      List.of(new SimpleGrantedAuthority("ROLE_ADMIN")), token.getAuthorizedClientRegistrationId());
    SecurityContextHolder.getContext().setAuthentication(conRol);
    // El redirect no guarda el contexto: hay que hacerlo a mano, igual que en el huésped.
    repoSesion.saveContext(SecurityContextHolder.getContext(), req, res);
    log.info("entrada al panel con Google: {}", email);
    continuacion.onAuthenticationSuccess(req, res, conRol);
  }
}