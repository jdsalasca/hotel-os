package co.hotel.reservas;

/** Error de validación en la entrada: el cliente debe corregir, no reintentar. */
public class DatosInvalidosException extends RuntimeException {
  public DatosInvalidosException(String mensaje) { super(mensaje); }
}