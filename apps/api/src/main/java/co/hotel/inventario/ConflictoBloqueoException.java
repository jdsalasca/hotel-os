package co.hotel.inventario;

/** El periodo solicitado ya tiene una reserva vigente o un bloqueo. */
public class ConflictoBloqueoException extends RuntimeException {
  public ConflictoBloqueoException(String mensaje) { super(mensaje); }
}
