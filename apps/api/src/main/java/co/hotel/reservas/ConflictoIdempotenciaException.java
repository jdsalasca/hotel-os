package co.hotel.reservas;

/**
 * La clave de idempotencia se reutilizó con otro contenido.
 *
 * Es un 409 y no un 400: la petición está bien formada, pero choca con la reserva que esa clave
 * ya creó. Devolver la reserva vieja en silencio haría creer al huésped que reservó las fechas
 * nuevas; crear otra duplicaría. La salida es repetir la petición original o usar una clave nueva.
 */
public class ConflictoIdempotenciaException extends RuntimeException {

  public ConflictoIdempotenciaException(String mensaje) {
    super(mensaje);
  }
}