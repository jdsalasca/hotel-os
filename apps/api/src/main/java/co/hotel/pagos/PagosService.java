package co.hotel.pagos;

import co.hotel.inventario.DatosInvalidosException;
import co.hotel.reservas.ReservaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Abonos manuales de una reserva y su saldo.
 *
 * Reglas: la reserva sigue vigente (una CANCELADA/RECHAZADA no admite dinero, devuelve 400); el
 * abono va en la moneda de la reserva (mezclar monedas en un libro manual es como se
 * pierden centavos); el importe es positivo; anular marca sin borrar. El saldo es total menos
 * abonos vigentes, calculado al leer: nunca se guarda.
 */
@Service
public class PagosService {
  private final PagosRepository pagos;
  private final ReservaRepository reservas;

  public PagosService(PagosRepository pagos, ReservaRepository reservas) {
    this.pagos = pagos;
    this.reservas = reservas;
  }

  /** Importe acordado, cobrado y pendiente de una reserva. */
  public record Saldo(long totalCents, String moneda, long abonadoCents, long pendienteCents,
                      List<Map<String, Object>> movimientos) {}

  public long abonar(String codigo, long montoCents, String moneda, String concepto, String actor) {
    var reserva = reservas.porCodigo(codigo)
      .orElseThrow(() -> new DatosInvalidosException("reserva no encontrada"));
    if (!reserva.estado().vigente()) {
      throw new DatosInvalidosException("la reserva está " + reserva.estado().name()
        + ": no admite abonos (si hubo cobro, es una devolución, no un abono)");
    }
    if (montoCents <= 0) throw new DatosInvalidosException("el abono debe ser mayor que cero");
    if (moneda == null || !moneda.equalsIgnoreCase(reserva.moneda())) {
      throw new DatosInvalidosException(
        "el abono va en la moneda de la reserva: " + reserva.moneda());
    }
    long id = reservas.idPorCodigo(codigo)
      .orElseThrow(() -> new DatosInvalidosException("reserva no encontrada"));
    return pagos.insertar(id, montoCents, reserva.moneda(), concepto == null ? "" : concepto.trim(),
      actor, LocalDateTime.now().toString());
  }

  public void anular(long abonoId, String actor) {
    pagos.reservaDe(abonoId)
      .orElseThrow(() -> new DatosInvalidosException("abono no encontrado"));
    if (pagos.anulado(abonoId)) throw new DatosInvalidosException("el abono ya está anulado");
    pagos.anular(abonoId, actor, LocalDateTime.now().toString());
  }

  public Saldo saldo(String codigo) {
    var reserva = reservas.porCodigo(codigo)
      .orElseThrow(() -> new DatosInvalidosException("reserva no encontrada"));
    long id = reservas.idPorCodigo(codigo)
      .orElseThrow(() -> new DatosInvalidosException("reserva no encontrada"));
    long abonado = pagos.abonadoVigente(id);
    long total = reserva.totalCents() == null ? 0L : reserva.totalCents();
    // Una reserva CANCELADA o RECHAZADA ya solto la fecha: no deja nada pendiente. Antes el
    // saldo salia siempre de `total - abonado` y una reserva cancelada pedia su importe
    // completo, como si el huesped tuviera que pagar por algo que ya no reservo (COP 3.000 en
    // la captura que origino esta ronda). El total acordado se sigue enseñando porque es
    // parte del historico; lo que no se inventa es una deuda nueva. Lo abonado se conserva:
    // es dinero real, y si se cobro antes de cancelar, lo que toca es devolverlo.
    long pendiente = reserva.estado().vigente() ? total - abonado : 0L;
    return new Saldo(total, reserva.moneda(), abonado, pendiente, pagos.movimientos(id));
  }
}
