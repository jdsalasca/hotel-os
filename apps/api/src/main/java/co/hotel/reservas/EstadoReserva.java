package co.hotel.reservas;

import java.util.List;

/**
 * Estados de una reserva y las transiciones que se permiten entre ellos.
 *
 * CONFIRMADA solo la alcanza una acción explícita del hotel, y CANCELADA/RECHAZADA/
 * NO_PRESENTADA son finales:
 * el hotel ya liberó la fecha, así que volver a CONFIRMADA le volvería a bloquear la habitación
 * (`vigente()`) sobre una fecha que se creía libre. Si el hotel se equivocó al cancelar, lo
 * correcto es una reserva nueva, que además queda con su propio código e historial.
 *
 * Antes estas reglas no existían: el servicio aceptaba cualquier estado nuevo sin mirar el anterior.
 */
public enum EstadoReserva {
  PENDIENTE, CONFIRMADA, CANCELADA, RECHAZADA, NO_PRESENTADA;

  public boolean vigente() { return this == PENDIENTE || this == CONFIRMADA; }

  /** No tiene salida: la fecha se liberó y no se vuelve a bloquear desde aquí. */
  public boolean esTerminal() {
    return this == CANCELADA || this == RECHAZADA || this == NO_PRESENTADA;
  }

  /** Estados alcanzables desde este. El propio estado no cuenta: no hay "cambiar a lo mismo". */
  public List<EstadoReserva> desde() {
    return switch (this) {
      case PENDIENTE -> List.of(CONFIRMADA, CANCELADA, RECHAZADA);
      case CONFIRMADA -> List.of(CANCELADA, NO_PRESENTADA);
      case CANCELADA, RECHAZADA, NO_PRESENTADA -> List.of();
    };
  }

  public boolean puede(EstadoReserva destino) { return desde().contains(destino); }

  /** Falla con un mensaje que dice de dónde se venía y adónde se quería ir. */
  public static void validar(EstadoReserva origen, EstadoReserva destino) {
    if (!origen.puede(destino)) {
      throw new ExcepcionDeEstado(origen.esTerminal()
        ? "no se puede pasar de " + origen + " a " + destino + ": ya está cerrada, crea una reserva nueva"
        : "no se puede pasar de " + origen + " a " + destino);
    }
  }
}
