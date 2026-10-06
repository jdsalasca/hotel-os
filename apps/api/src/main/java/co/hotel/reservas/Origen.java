package co.hotel.reservas;

/** Origen de la reserva. Se registra siempre; una reserva sin origen conocido es un error de datos. */
public enum Origen {
  WEB, BOOKING, DESPEGAR, AIRBNB, OTRO;

  public static Origen de(String texto) {
    if (texto == null || texto.isBlank()) throw new DatosInvalidosException("origen requerido");
    for (Origen o : values()) if (o.name().equalsIgnoreCase(texto.trim())) return o;
    throw new DatosInvalidosException("origen desconocido: " + texto);
  }
}