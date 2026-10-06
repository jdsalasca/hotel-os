package co.hotel.reservas;

import java.time.LocalDate;

/** Datos mínimos de una reserva solicitada. Objeto de valor: inmutable y validado en el servicio. */
public record CrearReserva(String email, String nombre, LocalDate llegada, LocalDate salida,
                           int huespedes, Origen origen, String claveIdempotencia, long roomId) {

  public CrearReserva {
    nombre = nombre == null ? "" : nombre.trim();
  }

  /** El intervalo es semiabierto [llegada, salida): la salida nunca ocupa noche. */
  public long noches() { return java.time.temporal.ChronoUnit.DAYS.between(llegada, salida); }
}