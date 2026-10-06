package co.hotel.reservas;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Reserva persistida. `estado` e `origen` ya llegan resueltos desde el dominio. */
public record Reserva(String codigo, String email, String nombre, LocalDate llegada, LocalDate salida,
                      int huespedes, EstadoReserva estado, Origen origen, String creadoEn) {

  public long noches() { return java.time.temporal.ChronoUnit.DAYS.between(llegada, salida); }

  public Reserva cambiarEstado(EstadoReserva nuevo) {
    return new Reserva(codigo, email, nombre, llegada, salida, huespedes, nuevo, origen, creadoEn);
  }
}