package co.hotel.auditoria;

import org.springframework.stereotype.Service;

/**
 * Rastro de cambios de estado de una reserva. Se escribe en la misma transacción que el cambio:
 * si la auditoría falla, la operación falla.
 */
@Service
public class AuditoriaService {
  private final AuditoriaRepository repo;

  public AuditoriaService(AuditoriaRepository repo) { this.repo = repo; }

  public void cambioEstado(long reservationId, String estadoAnterior, String estadoNuevo, String actor) {
    repo.registrarEstado(reservationId, estadoAnterior, estadoNuevo, actor);
  }

  /** Movimiento que no cambia el estado (reasignar habitación): el qué va en el detalle. */
  public void movimiento(long reservationId, String estadoActual, String detalle, String actor) {
    repo.registrarMovimiento(reservationId, estadoActual, detalle, actor);
  }
}