package co.hotel.inventario;

/** Tipo de habitación (categoría que agrupa habitaciones). La capacidad la define el hotel. */
public record RoomType(long id, String codigo, String nombre, int capacidadMax) {}