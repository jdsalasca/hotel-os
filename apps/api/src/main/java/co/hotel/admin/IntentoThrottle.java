package co.hotel.admin;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Tope de intentos para rutas que aceptan un secreto sin sesión. El caso es `POST /api/admin/init`:
 * su única barrera es ADMIN_INIT_TOKEN y quien lo adivine crea la cuenta de administrador.
 *
 * Vive en memoria por la misma razón que los otros limitadores: instancia única con SQLite.
 */
@Component
public class IntentoThrottle {
  public static final int MAX_INTENTOS = 5;
  private static final Duration VENTANA = Duration.ofMinutes(15);
  private static final int MAX_CLAVES = 10_000;

  private final Map<String, Intentos> porClave = new ConcurrentHashMap<>();

  /** true si el intento puede seguir. No cuenta: eso lo hace fallo(). */
  public boolean permitir(String clave) {
    if (clave == null) return false;
    Instant ahora = Instant.now();
    if (porClave.size() > MAX_CLAVES) porClave.clear();
    Intentos i = porClave.compute(clave, (k, v) -> (v == null || v.caducada(ahora)) ? new Intentos(ahora) : v);
    return !i.caducada(ahora) && i.fallos < MAX_INTENTOS;
  }

  public void fallo(String clave) {
    if (clave == null) return;
    Instant ahora = Instant.now();
    porClave.compute(clave, (k, v) ->
      (v == null || v.caducada(ahora)) ? new Intentos(ahora).conFallo() : v.conFallo());
  }

  /** Un acierto limpia el contador: quien entra con el token correcto no queda penalizado. */
  public void exito(String clave) { if (clave != null) porClave.remove(clave); }

  private record Intentos(Instant primerFallo, int fallos) {
    Intentos(Instant primerFallo) { this(primerFallo, 0); }
    Intentos conFallo() { return new Intentos(primerFallo, fallos + 1); }
    boolean caducada(Instant ahora) { return Duration.between(primerFallo, ahora).compareTo(VENTANA) > 0; }
  }
}