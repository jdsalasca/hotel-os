package co.hotel.seguridad;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Límite de reservas públicas por dirección IP.
 *
 * Existe porque {@code POST /api/reservas} no tiene sesión: sin tope, un script en bucle puede
 * reservar todo el inventario con correos inventados y los huéspedes reales ven que no hay
 * disponibilidad. El daño no es una caída, es perder la venta.
 *
 * Vive en memoria por la misma razón que {@link LoginThrottle}: el despliegue es de instancia
 * única con SQLite, no hay segundo proceso donde repartir el contador.
 */
@Component
public class LimiteReservas {
  /** Un huésped real reserva una o dos veces; diez deja margen sin abrir la puerta a un bucle. */
  public static final int MAX_POR_IP = 10;
  private static final Duration VENTANA = Duration.ofMinutes(15);
  /** Techo de claves vivas. Ver ponytail: una IP distinta por petición llenaría el mapa. */
  private static final int MAX_CLAVES = 10_000;

  private final Map<String, Ventana> porIp = new ConcurrentHashMap<>();
  private final int maxPorIp;

  /**
   * Configurable porque en un resort todos los huéspedes salen por la misma IP: con diez de tope, la
   * tercera casa del pueblo que reserve se encuentra con un 429 sin haber hecho nada malo. Subirlo
   * es decisión de quien despliega, no un default.
   */
  public LimiteReservas(@Value("${hotel.limites.reservas:10}") int maxPorIp) {
    this.maxPorIp = maxPorIp;
  }

  /** true si la petición puede seguir. Cuenta el intento aunque se permita. */
  public boolean permitir(String ip) {
    if (ip == null) return false;
    Instant ahora = Instant.now();
    if (porIp.size() > MAX_CLAVES) porIp.clear();
    Ventana v = porIp.compute(ip, (k, previa) ->
      previa == null || previa.caducada(ahora) ? new Ventana(ahora, 1) : previa.conUno());
    return v.conteo <= maxPorIp;
  }

  private record Ventana(Instant inicio, int conteo) {
    boolean caducada(Instant ahora) { return Duration.between(inicio, ahora).compareTo(VENTANA) > 0; }
    Ventana conUno() { return new Ventana(inicio, conteo + 1); }
  }
}