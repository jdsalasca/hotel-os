package co.hotel.auditoria;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * El usuario con sesión, para atribuir una acción.
 *
 * Se lee del contexto de seguridad y no del cuerpo de la petición a propósito: si el actor fuera un
 * campo del JSON, cualquiera con sesión podría atribuir su cambio a otro. El contexto lo fija el
 * servidor al validar la contraseña.
 */
public final class ActorActual {
  public static final String ANONIMO = "ANONIMO";

  private ActorActual() {}

  public static String correo() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    // AnonymousAuthenticationToken viene "autenticado" pero no es una persona.
    if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
      return ANONIMO;
    }
    return auth.getName();
  }
}