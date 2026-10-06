package co.hotel.inventario;

import co.hotel.reservas.SqliteTransactionExecutor;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Inventario del hotel: tipos, habitaciones, estados y bloqueos.
 *
 * También responde qué se ofrece en un periodo. Una habitación entra en la oferta solo si está
 * libre **y** el hotel tiene tarifa para todas las noches. Si falta un precio, no hay oferta:
 * nadie calcula un importe que el hotel no haya fijado.
 */
@Service
public class InventarioService {
  private final InventarioRepository inventario;
  private final TarifaRepository tarifas;
  private final SqliteTransactionExecutor tx;

  public InventarioService(InventarioRepository inventario, TarifaRepository tarifas,
                           SqliteTransactionExecutor tx) {
    this.inventario = inventario;
    this.tarifas = tarifas;
    this.tx = tx;
  }

  public RoomType crearTipo(String codigo, String nombre, int capacidadMax) {
    if (codigo == null || codigo.isBlank()) throw new DatosInvalidosException("código de tipo requerido");
    if (capacidadMax < 1) throw new DatosInvalidosException("la capacidad debe ser al menos 1 huésped");
    return tx.enTransaccion(estado -> {
      long id = inventario.insertarTipo(codigo.trim(), nombre.trim(), capacidadMax);
      return inventario.tipoPorId(id).orElseThrow();
    });
  }

  public Habitacion crearHabitacion(String codigo, long tipoId, String nombre) {
    if (codigo == null || codigo.isBlank()) throw new DatosInvalidosException("código de habitación requerido");
    if (inventario.idPorCodigoHabitacion(codigo.trim()).isPresent())
      throw new DatosInvalidosException("ya existe una habitación con el código " + codigo.trim());
    return tx.enTransaccion(estado -> {
      long id = inventario.insertarHabitacion(codigo.trim(), tipoId, nombre == null ? "" : nombre.trim());
      return inventario.habitacionPorId(id).orElseThrow();
    });
  }

  public Habitacion cambiarEstado(long habitacionId, EstadoHabitacion nuevo) {
    inventario.actualizarEstado(habitacionId, nuevo);
    return inventario.habitacionPorId(habitacionId)
      .orElseThrow(() -> new DatosInvalidosException("habitación no encontrada"));
  }

  /** Bloqueo de una habitación concreta (por ejemplo, mantenimiento). */
  public long bloquear(long habitacionId, LocalDate desde, LocalDate hasta, String motivo) {
    return registrarBloqueo(habitacionId, desde, hasta, motivo);
  }

  /** Bloqueo de todo el hotel. */
  public long bloquearTodo(LocalDate desde, LocalDate hasta, String motivo) {
    return registrarBloqueo(null, desde, hasta, motivo);
  }

  private long registrarBloqueo(Long habitacionId, LocalDate desde, LocalDate hasta, String motivo) {
    if (desde == null || hasta == null || !desde.isBefore(hasta))
      throw new DatosInvalidosException("el bloqueo necesita desde anterior a hasta");
    return tx.enTransaccion(estado ->
      inventario.insertarBloqueo(habitacionId, desde, hasta, motivo == null ? "" : motivo.trim()));
  }

  public List<Habitacion> listarHabitaciones() { return inventario.habitaciones(); }

  public List<RoomType> listarTipos() { return inventario.tipos(); }

  public List<Habitacion> disponibles(LocalDate desde, LocalDate hasta) {
    validarPeriodo(desde, hasta);
    return inventario.disponibles(desde, hasta);
  }

  /**
   * Habitaciones libres cuyo precio se puede totalizar. Quedan fuera las que no alcanzan la
   * capacidad pedida, las que tienen una noche cerrada o sin tarifa, y las que incumplen una
   * restricción de estancia.
   */
  public List<OpcionOferta> disponiblesConPrecio(LocalDate desde, LocalDate hasta, int huespedes) {
    validarPeriodo(desde, hasta);
    if (huespedes < 1) throw new DatosInvalidosException("número de huéspedes inválido");

    List<OpcionOferta> ofertas = new java.util.ArrayList<>();
    for (Habitacion habitacion : inventario.disponibles(desde, hasta)) {
      Optional<RoomType> tipo = inventario.tipoPorId(habitacion.roomTypeId());
      if (tipo.isEmpty() || tipo.get().capacidadMax() < huespedes) continue;

      PrecioTotalizado precio = calcularPrecio(tipo.get(), desde, hasta);
      if (precio != null) {
        ofertas.add(new OpcionOferta(habitacion, tipo.get(), precio.totalCents(), precio.moneda(),
          precio.noches()));
      }
    }
    return ofertas;
  }

  /**
   * Suma las noches del periodo en el primer plan activo con tarifa completa.
   * Devuelve null si ninguna noche está configurada, alguna está cerrada o incumple la estancia:
   * la ausencia de precio es un dato, no un cero.
   */
  private PrecioTotalizado calcularPrecio(RoomType tipo, LocalDate desde, LocalDate hasta) {
    long noches = java.time.temporal.ChronoUnit.DAYS.between(desde, hasta);
    for (PlanTarifario plan : tarifas.planesActivos()) {
      var configuradas = tarifas.nochesDelPeriodo(plan.id(), tipo.id(), desde, hasta);
      if (configuradas.size() != noches) continue;

      long total = 0;
      boolean completa = true;
      for (TarifaRepository.TarifaNoche noche : configuradas) {
        if (noche.cerrado()) { completa = false; break; }
        if (noche.minEstancia() != null && noches < noche.minEstancia()) { completa = false; break; }
        if (noche.maxEstancia() != null && noches > noche.maxEstancia()) { completa = false; break; }
        total += noche.precioCents();
      }
      if (completa) return new PrecioTotalizado(total, plan.moneda(), (int) noches);
    }
    return null;
  }

  private void validarPeriodo(LocalDate desde, LocalDate hasta) {
    if (desde == null || hasta == null || !desde.isBefore(hasta))
      throw new DatosInvalidosException("la salida debe ser posterior a la llegada");
  }

  /** Total del periodo solo si todas las noches tienen tarifa válida. */
  private record PrecioTotalizado(long totalCents, String moneda, int noches) {}
}