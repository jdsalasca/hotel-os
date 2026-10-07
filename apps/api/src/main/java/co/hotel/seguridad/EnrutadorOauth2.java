package co.hotel.seguridad;

import co.hotel.huespedes.ManejadorHuespedOauth2;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * Un Client ID, dos registros, dos manejadores. Este enrutador mira con cuál de los dos volvió
 * Google y le pasa la petición al manejador que toca. Un registro desconocido no entra: se limpia
 * el contexto y se vuelve al inicio.
 */
@Component
public class EnrutadorOauth2 implements AuthenticationSuccessHandler {
  public static final String ADMIN = "google-admin";
  public static final String HUESPED = "google-huesped";

  private final ManejadorAdminOauth2 admin;
  private final ManejadorHuespedOauth2 huesped;

  public EnrutadorOauth2(ManejadorAdminOauth2 admin, ManejadorHuespedOauth2 huesped) {
    this.admin = admin;
    this.huesped = huesped;
  }

  @Override
  public void onAuthenticationSuccess(HttpServletRequest req, HttpServletResponse res,
                                      Authentication auth) throws IOException, ServletException {
    String registro = auth instanceof OAuth2AuthenticationToken t
      ? t.getAuthorizedClientRegistrationId() : null;
    if (ADMIN.equals(registro)) {
      admin.onAuthenticationSuccess(req, res, auth);
      return;
    }
    if (HUESPED.equals(registro)) {
      huesped.onAuthenticationSuccess(req, res, auth);
      return;
    }
    SecurityContextHolder.clearContext();
    res.sendRedirect("/?error=oauth2");
  }
}