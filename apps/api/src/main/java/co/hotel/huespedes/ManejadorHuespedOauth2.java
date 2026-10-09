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
  private final org.springframework.security.web.context.HttpSessionSecurityContextRepository repoSesion =
    new org.springframework.security.web.context.HttpSessionSecurityContextRepository();

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

    long id;
    try {
      id = usuarios.porSubOCrear(sub, email, principal.getAttribute("name"));
    } catch (Exception e) {
      // Igual que en el panel: caerse a mitad no puede ser un 500 mudo.
      SecurityContextHolder.clearContext();
      log.warn("entrada de huésped con Google falló para {}: {}: {}", email,
        e.getClass().getSimpleName(), e.getMessage());
      res.sendRedirect("/?error=oauth2");
      return;
    }
    // La sesión se identifica por el correo, igual que el panel: /api/yo lo lee de ahí.
    var renombrado = new DefaultOAuth2User(principal.getAuthorities(), principal.getAttributes(),
      "email");
    var token = new OAuth2AuthenticationToken(renombrado,
      java.util.List.of(new SimpleGrantedAuthority("ROLE_HUESPED")), "google-huesped");
    SecurityContextHolder.getContext().setAuthentication(token);
    // Guardar el contexto a mano es OBLIGATORIO, no opcional: el redirect no lo hace. Desde Spring
    // Security 6 el contexto solo se persiste si alguien lo guarda, y sin esto el huésped vuelve de
    // Google a una página que responde 401 en /api/yo. El panel no suffers eso porque su manejador
    // delega en SavedRequestAwareAuthenticationSuccessHandler, que sí lo guarda.
    repoSesion.saveContext(SecurityContextHolder.getContext(), req, res);
    log.info("huésped {} entró con Google (usuario {})", email, id);
    res.sendRedirect(destinoSeguro(req));
  }

  /**
   * A dónde vuelve el huésped después de entrar. Solo rutas internas: aceptar un `vuelve` tal cual
   * sería un redirect abierto, con el que un atacante lleva al usuario a su sitio justo después de
   * autenticarse de verdad en el hotel.
   */
  private String destinoSeguro(HttpServletRequest req) {
    String vuelve = req.getParameter("vuelve");
    if (vuelve == null || !vuelve.startsWith("/") || vuelve.startsWith("//")) {
      return "/mis-reservas";
    }
    return vuelve;
  }
}