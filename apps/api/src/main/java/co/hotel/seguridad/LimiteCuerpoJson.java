package co.hotel.seguridad;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Tope de tamaño para cuerpos JSON. `server.tomcat.max-http-form-post-size` solo limita
 * formularios: un JSON gigante se leería entero en memoria sin que nada lo impida. El cuerpo
 * legítimo más grande (un lote de 366 noches) no llega a 100 KB, así que 1 MB sobra y corta
 * el abuso con 413 antes de leerlo.
 *
 * Va justo después de la auditoría (que así registra también el 413) y antes que seguridad:
 * frenar un abuso no exige sesión.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class LimiteCuerpoJson extends OncePerRequestFilter {
  public static final long MAX_CUERPO_BYTES = 1024L * 1024L;

  /** Marca interna: el cuerpo pasó el tope mientras se leía (p. ej. troceado sin longitud). */
  static final class CuerpoMuyGrandeException extends IOException {
    CuerpoMuyGrandeException() {
      super("el cuerpo supera el máximo de " + MAX_CUERPO_BYTES + " bytes");
    }
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest req) {
    return !Set.of("POST", "PUT", "PATCH", "DELETE").contains(req.getMethod());
  }

  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    if (req.getContentLengthLong() > MAX_CUERPO_BYTES) {
      rechazar(res);
      return;
    }
    try {
      chain.doFilter(new PeticionAcotada(req), res);
    } catch (CuerpoMuyGrandeException e) {
      rechazar(res);
    } catch (ServletException | IOException e) {
      for (Throwable t = e; t != null; t = t.getCause()) {
        if (t instanceof CuerpoMuyGrandeException) {
          rechazar(res);
          return;
        }
      }
      throw e;
    }
  }

  private static void rechazar(HttpServletResponse res) throws IOException {
    res.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
    res.setContentType("application/json;charset=UTF-8");
    res.getWriter().write("{\"error\":\"el cuerpo supera el máximo de 1 MB\"}");
  }

  /** El cuerpo se cuenta mientras se lee: al pasar el tope se corta en seco. */
  static final class PeticionAcotada extends HttpServletRequestWrapper {
    PeticionAcotada(HttpServletRequest req) {
      super(req);
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
      ServletInputStream original = super.getInputStream();
      return new ServletInputStream() {
        private long leidos = 0;

        private int contar(int n) throws CuerpoMuyGrandeException {
          if (n == -1) return -1;
          leidos += n;
          if (leidos > MAX_CUERPO_BYTES) throw new CuerpoMuyGrandeException();
          return n;
        }

        @Override
        public int read() throws IOException {
          return contar(original.read());
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
          return contar(original.read(b, off, len));
        }

        @Override
        public boolean isFinished() {
          return original.isFinished();
        }

        @Override
        public boolean isReady() {
          return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
          // Solo se usa lectura bloqueante en esta app.
        }
      };
    }

    @Override
    public BufferedReader getReader() throws IOException {
      return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }
  }
}
