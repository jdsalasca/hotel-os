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
 * Reglas: el abono va en la moneda de la reserva (mezclar monedas en un libro manual es como se
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
    return new Saldo(total, reserva.moneda(), abonado, total - abonado, pagos.movimientos(id));
  }
}
