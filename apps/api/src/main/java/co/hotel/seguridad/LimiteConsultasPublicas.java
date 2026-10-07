package co.hotel.seguridad;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Tope de lecturas públicas por dirección IP.
 *
 * Complementa a {@link LimiteReservas}, que protege las escrituras. Aquí está la razón: consultar
 * una reserva exige código y correo, así que el endpoint es seguro para quien tiene sus datos, pero
 * un atacante con tiempo puede recorrer los códigos probando correos. El código son 8 caracteres
 * hexadecimales (32 bits) y el correo se compara sin distinguir mayúsculas, así que ese endpoint se
 * convierte en un oráculo de "qué correos están registrados" y de carga contra la base.
 *
 * 30 lecturas por minuto: un huésped que revisa su reserva, su comprobante o la disponibilidad
 * repite unas pocas veces, muy por debajo del tope.
 *
 * Vive en memoria por la misma razón que los otros dos limitadores: instancia única con SQLite.
 */
@Component
public class LimiteConsultasPublicas {
  public static final int MAX_POR_MINUTO = 30;
  private static final Duration VENTANA = Duration.ofMinutes(1);
  /** Techo de claves vivas; ver ponytail en {@link LimiteReservas}. */
  private static final int MAX_CLAVES = 10_000;

  private final Map<String, Ventana> porIp = new ConcurrentHashMap<>();

  /** true si la lectura puede seguir. */
  public boolean permitir(String ip) {
    if (ip == null) return false;
    Instant ahora = Instant.now();
    if (porIp.size() > MAX_CLAVES) porIp.clear();
    Ventana v = porIp.compute(ip, (k, previa) ->
      previa == null || previa.caducada(ahora) ? new Ventana(ahora, 1) : previa.conUno());
    return v.conteo <= MAX_POR_MINUTO;
  }

  private record Ventana(Instant inicio, int conteo) {
    boolean caducada(Instant ahora) { return Duration.between(inicio, ahora).compareTo(VENTANA) > 0; }
    Ventana conUno() { return new Ventana(inicio, conteo + 1); }
  }
}