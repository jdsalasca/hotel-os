package co.hotel.reservas;

import co.hotel.inventario.InventarioService;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

/**
 * Selección de habitación para el flujo público.
 *
 * Solo elige entre lo que está a la venta: activa, libre, con capacidad para los huéspedes y con
 * tarifa completa para todas las noches. Antes devolvía la primera libre sin mirar capacidad ni
 * precio, y la reserva se creaba igual con el total en NULL: vender sin importe.
 */
@Service
public class RoomSelector {
  private final InventarioService inventario;

  public RoomSelector(InventarioService inventario) { this.inventario = inventario; }

  /** Primera habitación vendible para los huéspedes, o null si no hay ninguna a la venta. */
  public Long primeraDisponible(LocalDate llegada, LocalDate salida, int huespedes) {
    if (huespedes < 1) return null;
    return inventario.disponiblesConPrecio(llegada, salida, huespedes).stream()
      .map(o -> o.habitacion().id())
      .findFirst().orElse(null);
  }
}