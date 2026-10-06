package co.hotel.reservas;

/** Error de negocio: la reserva no puede crearse con los datos dados. */
public class SinDisponibilidadException extends RuntimeException {
  public SinDisponibilidadException(String mensaje) { super(mensaje); }
}