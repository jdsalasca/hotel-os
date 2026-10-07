package co.hotel.seguridad;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Límite de intentos de autenticación. Vive en memoria porque el despliegue es de instancia única
 * con SQLite: no hay segundo proceso donde repartir el contador. Si algún día se escala, este
 * contador debe pasar a la base de datos; está anotado para que no se pierda.
 *
 * Se usan dos instancias, una por criterio: `correo|IP` y `IP` sola. El primero frena el ataque a
 * una cuenta; el segundo frena el credential stuffing, que cambia de correo en cada intento.
 *
 * La clase ya no es un `@Component`: se registran a mano en AppConfig, porque un solo bean no
 * puede ser las dos cosas.
 */
public class LoginThrottle {
  /** Público para que las pruebas fijen el número de intentos sin duplicarlo. */
  public static final int MAX_INTENTOS = 5;
  private static final Duration VENTANA = Duration.ofMinutes(15);

  private final Map<String, Intentos> porClave = new ConcurrentHashMap<>();

  /** true si el intento debe permitirse. Un acierto limpia el contador. */
  public boolean permitir(String clave) {
    if (clave == null) return false;
    Instant ahora = Instant.now();
    Intentos intentos = porClave.compute(clave, (k, v) -> (v == null || v.caducada(ahora)) ? new Intentos(ahora) : v);
    return !intentos.caducada(ahora) && intentos.fallos < MAX_INTENTOS;
  }

  public void fallo(String clave) {
    if (clave == null) return;
    Instant ahora = Instant.now();
    porClave.compute(clave, (k, v) -> {
      if (v == null || v.caducada(ahora)) return new Intentos(ahora).conFallo();
      return v.conFallo();
    });
  }

  public void exito(String clave) { if (clave != null) porClave.remove(clave); }

  /** Solo para diagnóstico del panel; no expone credenciales. */
  public int fallosRegistrados(String clave) {
    Intentos intentos = porClave.get(clave);
    return intentos == null ? 0 : intentos.fallos;
  }

  private static final class Intentos {
    private final Instant primerFallo;
    private final int fallos;

    private Intentos(Instant primerFallo) { this(primerFallo, 0); }

    private Intentos(Instant primerFallo, int fallos) {
      this.primerFallo = primerFallo;
      this.fallos = fallos;
    }

    private Intentos conFallo() { return new Intentos(primerFallo, fallos + 1); }

    private boolean caducada(Instant ahora) { return Duration.between(primerFallo, ahora).compareTo(VENTANA) > 0; }
  }
}