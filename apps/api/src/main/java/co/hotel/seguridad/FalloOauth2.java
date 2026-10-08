package co.hotel.seguridad;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/**
 * La vuelta de Google que no se completa. Antes era un lambda mudo: el dueño rebotaba
 * a ?error=oauth2 sin explicación y el log quedaba limpio, así que nadie podía saber
 * si falló el secreto, la sesión o la allowlist. Ahora el motivo queda registrado
 * (clase y mensaje, nunca el secreto) y cada entrada vuelve a su puerta.
 */
@Component
public class FalloOauth2 implements AuthenticationFailureHandler {
  private static final Logger log = LoggerFactory.getLogger(FalloOauth2.class);

  @Override
  public void onAuthenticationFailure(HttpServletRequest req, HttpServletResponse res,
                                      AuthenticationException ex) throws IOException, ServletException {
    log.warn("vuelta de Google fallida en {}: {}: {}",
      req.getRequestURI(), ex.getClass().getSimpleName(), ex.getMessage());
    String destino = req.getRequestURI().endsWith(EnrutadorOauth2.HUESPED)
      ? "/?error=oauth2" : "/admin/entrar?error=oauth2";
    res.sendRedirect(destino);
  }
}
