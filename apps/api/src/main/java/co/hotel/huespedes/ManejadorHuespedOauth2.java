package co.hotel.huespedes;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * Entrada de huéspedes con Google, sin allowlist: cualquiera que tenga una cuenta puede reservar,
 * que es el punto de una reserva directa.
 *
 * El primer login crea el usuario y solo eso: no se toca ninguna reserva previa. Quien ya había
 * reservado sin sesión sigue teniendo sus reservas, y las tendrá en "Mis reservas" solo a partir
 * de la siguiente.
 */
@Component
public class ManejadorHuespedOauth2 implements AuthenticationSuccessHandler {
  private static final Logger log = LoggerFactory.getLogger(ManejadorHuespedOauth2.class);

  private final UsuariosHuespedRepository usuarios;

  public ManejadorHuespedOauth2(UsuariosHuespedRepository usuarios) { this.usuarios = usuarios; }

  @Override
  public void onAuthenticationSuccess(HttpServletRequest req, HttpServletResponse res,
                                      Authentication auth) throws IOException, ServletException {
    if (!(auth.getPrincipal() instanceof OAuth2User principal)) {
      SecurityContextHolder.clearContext();
      res.sendRedirect("/?error=oauth2");
      return;
    }
    String sub = principal.getAttribute("sub");
    String email = principal.getAttribute("email");
    if (sub == null || email == null) {
      // Sin correo no hay nada que guardar: Google no lo dio.
      SecurityContextHolder.clearContext();
      log.warn("Google no devolvió sub/email: no se puede crear el huésped");
      res.sendRedirect("/?error=oauth2");
      return;
    }

    long id = usuarios.porSubOCrear(sub, email, principal.getAttribute("name"));
    // La sesión se identifica por el correo, igual que el panel: /api/yo lo lee de ahí.
    var renombrado = new DefaultOAuth2User(principal.getAuthorities(), principal.getAttributes(),
      "email");
    var token = new OAuth2AuthenticationToken(renombrado,
      java.util.List.of(new SimpleGrantedAuthority("ROLE_HUESPED")), "google-huesped");
    SecurityContextHolder.getContext().setAuthentication(token);
    log.info("huésped {} entró con Google (usuario {})", email, id);
    res.sendRedirect(req.getParameter("vuelve") != null ? req.getParameter("vuelve") : "/mis-reservas");
  }
}