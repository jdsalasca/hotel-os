package co.hotel.reservas;

/**
 * El precio cambió entre la búsqueda y la confirmación.
 *
 * Es un 409 y no un 400: la petición está bien formada, pero el importe que el huésped aceptó ya
 * no es el vigente. Lleva el nuevo total para que el navegador lo muestre y pida confirmación
 * explícita, en vez de colar el precio nuevo en silencio.
 */
public class PrecioCambiadoException extends RuntimeException {

  private final long nuevoTotalCents;
  private final String nuevaMoneda;

  public PrecioCambiadoException(long nuevoTotalCents, String nuevaMoneda) {
    super("el precio cambió desde tu búsqueda: confirma de nuevo con el importe actualizado");
    this.nuevoTotalCents = nuevoTotalCents;
    this.nuevaMoneda = nuevaMoneda;
  }

  public long nuevoTotalCents() { return nuevoTotalCents; }

  public String nuevaMoneda() { return nuevaMoneda; }
}