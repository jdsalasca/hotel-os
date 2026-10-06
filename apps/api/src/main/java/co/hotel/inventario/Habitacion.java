package co.hotel.inventario;

/** Habitación física del hotel. */
public record Habitacion(long id, String codigo, long roomTypeId, String nombre, EstadoHabitacion estado) {}