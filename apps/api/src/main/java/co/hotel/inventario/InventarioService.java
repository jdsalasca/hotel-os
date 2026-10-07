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

  /** Estado de una noche: libre, ocupada por una reserva, bloqueada o habitación no vendible. */
  public record Noche(LocalDate fecha, String estado, String codigoReserva) {}

  /** Una habitación con sus noches en el periodo pedido. */
  public record OcupacionHabitacion(long id, String codigo, List<Noche> noches) {}

  /**
   * Calendario de ocupación: una fila por habitación y una columna por noche. Es el dato que el
   * hotel necesita ver para saber qué noche vende y cuál no; hasta ahora se respondía con una
   * consulta por habitación contra la API pública, que es un atajo caro y que no distingue entre
   * una reserva cancelada y una habitación libre.
   *
   * Intervalo semiabierto: la noche `d` está ocupada si `desde <= d < hasta`. El día de salida no
   * cuenta, como en el resto del sistema.
   */
  public List<OcupacionHabitacion> ocupacion(LocalDate desde, LocalDate hasta) {
    validarPeriodo(desde, hasta);
    if (java.time.temporal.ChronoUnit.DAYS.between(desde, hasta) > 366)
      throw new DatosInvalidosException("el periodo no puede superar un año");

    var reservas = inventario.ocupacionesDeReservas(desde, hasta);
    var bloqueos = inventario.bloqueosDe(desde, hasta);
    List<OcupacionHabitacion> calendario = new java.util.ArrayList<>();

    for (Habitacion habitacion : inventario.habitaciones()) {
      List<Noche> noches = new java.util.ArrayList<>();
      for (LocalDate dia = desde; dia.isBefore(hasta); dia = dia.plusDays(1)) {
        noches.add(new Noche(dia, estadoDe(habitacion, dia, reservas, bloqueos), codigoDe(habitacion, dia, reservas)));
      }
      calendario.add(new OcupacionHabitacion(habitacion.id(), habitacion.codigo(), noches));
    }
    return calendario;
  }

  /**
   * Prioridad: primero si la habitación no está activa, después el bloqueo y por último la
   * reserva. Una habitación retirada no vende; un bloqueo vigente tapa lo que el hotel marcó; y
   * si hay reserva, hay huésped y eso es lo que hay que mostrar.
   */
  private String estadoDe(Habitacion habitacion, LocalDate dia,
      List<InventarioRepository.OcupacionReserva> reservas,
      List<InventarioRepository.OcupacionBloqueo> bloqueos) {
    if (habitacion.estado() != EstadoHabitacion.ACTIVA) return habitacion.estado().name();
    for (InventarioRepository.OcupacionBloqueo bloqueo : bloqueos)
      if (cubreBloqueo(bloqueo, habitacion, dia)) return "BLOQUEADA";
    for (InventarioRepository.OcupacionReserva reserva : reservas)
      if (cubreReserva(reserva, habitacion, dia)) return "OCUPADA";
    return "LIBRE";
  }

  private String codigoDe(Habitacion habitacion, LocalDate dia,
      List<InventarioRepository.OcupacionReserva> reservas) {
    for (InventarioRepository.OcupacionReserva reserva : reservas)
      if (cubreReserva(reserva, habitacion, dia)) return reserva.codigo();
    return null;
  }

  /** Intervalo semiabierto [desde, hasta): el día de salida no se cuenta. */
  private static boolean dentro(LocalDate desde, LocalDate hasta, LocalDate dia) {
    return !dia.isBefore(desde) && dia.isBefore(hasta);
  }

  private static boolean cubreReserva(InventarioRepository.OcupacionReserva reserva, Habitacion habitacion, LocalDate dia) {
    return reserva.roomId() == habitacion.id() && dentro(reserva.desde(), reserva.hasta(), dia);
  }

  /** Bloqueo con `roomId` nulo = hotel entero, así que se aplica a todas las habitaciones. */
  private static boolean cubreBloqueo(InventarioRepository.OcupacionBloqueo bloqueo, Habitacion habitacion, LocalDate dia) {
    return (bloqueo.roomId() == null || bloqueo.roomId() == habitacion.id())
      && dentro(bloqueo.desde(), bloqueo.hasta(), dia);
  }

  /**
   * Precio acordado para una habitación y un periodo: el total, la moneda y el plan que lo
   * respalda. Es lo que se congela en la reserva para que el comprobante no dependa de lo que el
   * hotel tarifó después.
   */
  public record PrecioAcordado(long totalCents, String moneda, long ratePlanId) {}

  /**
   * Precio de una habitación concreta con las mismas reglas de la oferta pública: activa,
   * capacidad suficiente y tarifa completa en el primer plan que la tenga. Vacío si no hay tarifa:
   * quien llama guarda NULL en vez de inventar un importe.
   */
  public java.util.Optional<PrecioAcordado> precioDe(long roomId, LocalDate desde, LocalDate hasta,
      int huespedes) {
    return inventario.habitacionPorId(roomId)
      .filter(h -> h.estado() == EstadoHabitacion.ACTIVA)
      .flatMap(h -> inventario.tipoPorId(h.roomTypeId())
        .filter(t -> t.capacidadMax() >= huespedes)
        .flatMap(t -> java.util.Optional.ofNullable(calcularPrecio(t, desde, hasta))
          .map(p -> new PrecioAcordado(p.totalCents(), p.moneda(), p.planId()))));
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
      if (completa) return new PrecioTotalizado(total, plan.moneda(), (int) noches, plan.id());
    }
    return null;
  }

  private void validarPeriodo(LocalDate desde, LocalDate hasta) {
    if (desde == null || hasta == null || !desde.isBefore(hasta))
      throw new DatosInvalidosException("la salida debe ser posterior a la llegada");
  }

  /** Total del periodo solo si todas las noches tienen tarifa válida. */
  private record PrecioTotalizado(long totalCents, String moneda, int noches, long planId) {}
}