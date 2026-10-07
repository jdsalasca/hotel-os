package co.hotel.auditoria;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Un único punto donde queda rastro de todo lo que el panel escribe.
 *
 * Es un filtro y no dieciséis llamadas repartidas por los controladores. Así ningún endpoint
 * nuevo puede olvidarse de auditarse, que es exactamente como el rastro se vuelve falso: la
 * cobertura depende de que alguien se acuerde.
 *
 * Se registra después de la respuesta, con el estado HTTP real, para que quede también lo que
 * falló. Y la auditoría no puede tumbar la petición: si escribir el log falla, el hotel no puede
 * dejar de confirmar una reserva por eso.
 */
@Component
public class AuditoriaAdminFilter extends OncePerRequestFilter {
  private static final Logger LOG = LoggerFactory.getLogger(AuditoriaAdminFilter.class);

  private final AccionesAdminRepository repo;

  public AuditoriaAdminFilter(AccionesAdminRepository repo) { this.repo = repo; }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest req) {
    return !"POST".equals(req.getMethod()) || !req.getRequestURI().startsWith("/api/admin/");
  }

  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    try {
      chain.doFilter(req, res);
    } finally {
      try {
        repo.registrar(ActorActual.correo(), req.getMethod(), req.getRequestURI(), res.getStatus());
      } catch (RuntimeException e) {
        LOG.warn("no se pudo registrar {} {}", req.getMethod(), req.getRequestURI(), e);
      }
    }
  }
}