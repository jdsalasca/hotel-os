package co.hotel.reservas;

import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Selección de habitación para el flujo público. Yet to come: capacidad y tarifas.
 * Hoy devuelve la primera habitación libre; la búsqueda por fechas y huéspedes llega con tarifas.
 */
@Service
public class RoomSelector {
  private final ReservaRepository repo;

  public RoomSelector(ReservaRepository repo) { this.repo = repo; }

  /** Primera habitación activa y libre en el intervalo, o null si no hay ninguna. */
  public Long primeraDisponible(LocalDate llegada, LocalDate salida) {
    List<Long> libres = repo.habitacionesLibres(llegada, salida);
    return libres.isEmpty() ? null : libres.get(0);
  }
}