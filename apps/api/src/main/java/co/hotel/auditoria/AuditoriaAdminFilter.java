package co.hotel.auditoria;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Un único punto donde queda rastro de todo lo que el panel escribe.
 *
 * Es un filtro y no dieciséis llamadas repartidas por los controladores. Así ningún endpoint
 * nuevo puede olvidarse de auditarse, que es exactamente como el rastro se vuelve falso: la
 * cobertura depende de que alguien se acuerde.
 *
 * Va PRIMERO (antes que la cadena de Spring Security) a propósito: el LogoutFilter responde
 * sin continuar la cadena, así que un filtro después de seguridad jamás ve el logout. Y el
 * actor se lee de la sesión (no del holder, que antes de la cadena está vacío y después el
 * propio Spring lo limpia): se captura antes y después, y se queda el que identifique.
 * El login queda con quien entró y el logout con quien salió.
 *
 * Se registra después de la respuesta, con el estado HTTP real, para que quede también lo que
 * falló. Y la auditoría no puede tumbar la petición: si escribir el log falla, el hotel no puede
 * dejar de confirmar una reserva por eso.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuditoriaAdminFilter extends OncePerRequestFilter {
  private static final Logger LOG = LoggerFactory.getLogger(AuditoriaAdminFilter.class);

  private final AccionesAdminRepository repo;
  private final HttpSessionSecurityContextRepository contextos =
    new HttpSessionSecurityContextRepository();

  public AuditoriaAdminFilter(AccionesAdminRepository repo) { this.repo = repo; }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest req) {
    // Toda escritura del panel, no solo POST: el PUT de servicios y el PUT/DELETE de lugares
    // pasaban sin dejar rastro. Las lecturas (GET) no se registran.
    String metodo = req.getMethod();
    boolean escribe = "POST".equals(metodo) || "PUT".equals(metodo) || "DELETE".equals(metodo)
      || "PATCH".equals(metodo);
    return !escribe || !req.getRequestURI().startsWith("/api/admin/");
  }

  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String actorAntes = actorSesion(req);
    try {
      chain.doFilter(req, res);
    } finally {
      try {
        String actorDespues = actorSesion(req);
        String actor = !ActorActual.ANONIMO.equals(actorDespues) ? actorDespues : actorAntes;
        repo.registrar(actor, req.getMethod(), req.getRequestURI(), res.getStatus());
      } catch (RuntimeException e) {
        LOG.warn("no se pudo registrar {} {}", req.getMethod(), req.getRequestURI(), e);
      }
    }
  }

  /** Actor guardado en la sesión, sin crearla si no existe. Nunca tumba la petición. */
  private String actorSesion(HttpServletRequest req) {
    try {
      var contexto = contextos.loadDeferredContext(req).get();
      return ActorActual.correo(contexto == null ? null : contexto.getAuthentication());
    } catch (RuntimeException e) {
      return ActorActual.ANONIMO;
    }
  }
}
