package co.hotel.reservas;

import co.hotel.auditoria.AuditoriaRepository;
import co.hotel.hotel.HotelConfigService;
import co.hotel.inventario.TarifaRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Comprobante de reserva: lo que se acordó al reservar, la habitación asignada, la identidad del
 * hotel y el historial. No es una factura: no hay pagos ni impuestos aquí, solo el resumen
 * verificable de la solicitud. El total sale de la foto guardada al crear la reserva, nunca de las
 * tarifas vigentes.
 */
@Service
public class ComprobanteService {
  private final ReservaService reservas;
  private final ReservaRepository repo;
  private final TarifaRepository tarifas;
  private final HotelConfigService hotel;
  private final AuditoriaRepository auditoria;

  public ComprobanteService(ReservaService reservas, ReservaRepository repo, TarifaRepository tarifas,
                            HotelConfigService hotel, AuditoriaRepository auditoria) {
    this.reservas = reservas;
    this.repo = repo;
    this.tarifas = tarifas;
    this.hotel = hotel;
    this.auditoria = auditoria;
  }

  /** Con correo: compuerta pública (código + correo, como la consulta). Sin correo: panel. */
  public Optional<Map<String, Object>> comprobante(String codigo, String email) {
    var reserva = email == null ? reservas.buscar(codigo) : reservas.consultar(codigo, email);
    if (reserva.isEmpty()) return Optional.empty();
    return Optional.of(armar(reserva.get()));
  }

  private Map<String, Object> armar(Reserva r) {
    long id = repo.idPorCodigo(r.codigo()).orElseThrow();
    var habitacion = repo.habitacionDe(id).orElse(null);
    // Si el plan se borró después de la reserva, el nombre queda en null pero el total
    // congelado sigue valiendo: el comprobante no depende de la configuración vigente.
    String planNombre = r.ratePlanId() == null ? null
      : tarifas.planPorId(r.ratePlanId()).map(p -> p.nombre()).orElse(null);

    // LinkedHashMap y no Map.of: el total y la moneda pueden ser NULL cuando se reservó sin tarifa.
    Map<String, Object> reserva = new LinkedHashMap<>();
    reserva.put("codigo", r.codigo());
    reserva.put("email", r.email());
    reserva.put("nombre", r.nombre());
    reserva.put("llegada", r.llegada().toString());
    reserva.put("salida", r.salida().toString());
    reserva.put("noches", r.noches());
    reserva.put("huespedes", r.huespedes());
    reserva.put("estado", r.estado().name());
    reserva.put("origen", r.origen().name());
    reserva.put("totalCents", r.totalCents());
    reserva.put("moneda", r.moneda());
    reserva.put("plan", planNombre);

    Map<String, Object> comprobante = new LinkedHashMap<>();
    comprobante.put("reserva", reserva);
    comprobante.put("habitacion", habitacion == null ? null : Map.of(
      "codigo", habitacion.codigo(),
      "nombre", habitacion.nombre() == null ? "" : habitacion.nombre(),
      "tipo", habitacion.tipoNombre() == null ? "" : habitacion.tipoNombre()));
    comprobante.put("hotel", hotel.publicos());
    comprobante.put("historial", auditoria.historialDe(id));
    return comprobante;
  }
}
