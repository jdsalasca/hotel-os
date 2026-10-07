package co.hotel.huespedes;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** El correo con el que entró el huésped, o null si no hay sesión. */
public final class ActualCorreo {
  private ActualCorreo() {}

  public static String deSesion() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
      return null;
    }
    return auth.getName();
  }
}