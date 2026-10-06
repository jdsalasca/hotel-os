package co.hotel.inventario;

/** Estado de una habitación. FUERA_DE_SERVICIO la retira de la oferta sin borrarla del inventario. */
public enum EstadoHabitacion {
  ACTIVA, MANTENIMIENTO, FUERA_DE_SERVICIO;

  public boolean ofrece() { return this == ACTIVA; }
}