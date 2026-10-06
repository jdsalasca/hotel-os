package co.hotel.reservas;

/** Estados de una reserva. CONFIRMADA solo la alcanza una acción explícita del hotel. */
public enum EstadoReserva {
  PENDIENTE, CONFIRMADA, CANCELADA, RECHAZADA;

  public boolean vigente() { return this == PENDIENTE || this == CONFIRMADA; }
}