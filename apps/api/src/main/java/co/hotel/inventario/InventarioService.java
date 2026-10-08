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
    if (nombre == null || nombre.isBlank()) throw new DatosInvalidosException("nombre de tipo requerido");
    if (capacidadMax < 1) throw new DatosInvalidosException("la capacidad debe ser al menos 1 huésped");
    String cod = codigo.trim();
    String nom = nombre.trim();
    try {
      return tx.enTransaccion(estado -> {
        long id = inventario.insertarTipo(cod, nom, capacidadMax);
        return inventario.tipoPorId(id).orElseThrow();
      });
    } catch (org.springframework.dao.DataAccessException e) {
      if (mensajeContiene(e, "UNIQUE"))
        throw new DatosInvalidosException("ya existe un tipo con el código " + cod);
      throw e;
    }
  }

  public Habitacion crearHabitacion(String codigo, long tipoId, String nombre) {
    if (codigo == null || codigo.isBlank()) throw new DatosInvalidosException("código de habitación requerido");
    if (inventario.tipoPorId(tipoId).isEmpty())
      throw new DatosInvalidosException("tipo de habitación no encontrado");
    if (inventario.idPorCodigoHabitacion(codigo.trim()).isPresent())
      throw new DatosInvalidosException("ya existe una habitación con el código " + codigo.trim());
    try {
      return tx.enTransaccion(estado -> {
        long id = inventario.insertarHabitacion(codigo.trim(), tipoId, nombre == null ? "" : nombre.trim());
        return inventario.habitacionPorId(id).orElseThrow();
      });
    } catch (org.springframework.dao.DataAccessException e) {
      if (mensajeContiene(e, "UNIQUE"))
        throw new DatosInvalidosException("ya existe una habitación con el código " + codigo.trim());
      if (mensajeContiene(e, "FOREIGN KEY"))
        throw new DatosInvalidosException("tipo de habitación no encontrado");
      throw e;
    }
  }

  private static boolean mensajeContiene(Exception e, String fragmento) {
    for (Throwable t = e; t != null; t = t.getCause())
      if (t.getMessage() != null && t.getMessage().contains(fragmento)) return true;
    return false;
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

  /** Bloqueos vigentes para gestionarlos desde el panel. */
  public List<InventarioRepository.BloqueoVista> bloqueosVigentes() {
    return inventario.bloqueosVigentes();
  }

  /**
   * Retira un bloqueo: la habitación vuelve a la venta. Se borra la fila y el rastro queda en la
   * auditoría de acciones del panel (método, ruta y actor), que es donde el hotel mira qué se tocó.
   */
  public void retirarBloqueo(long bloqueoId) {
    if (!inventario.existeBloqueo(bloqueoId))
      throw new DatosInvalidosException("bloqueo no encontrado");
    inventario.eliminarBloqueo(bloqueoId);
  }

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
          .map(p -> new PrecioAcordado(p.totalCents(), p.moneda(), p.plan().id()))));
  }

  /**
   * Habitaciones libres cuyo precio se puede totalizar. Cada habitación sale una vez por plan con
   * tarifa completa: el huésped elige entre planes reales (flexible, promo…), no recibe solo el
   * primero. Quedan fuera las que no alcanzan la capacidad, las noches cerradas o sin tarifa y
   * las que incumplen una restricción.
   */
  public List<OpcionOferta> disponiblesConPrecio(LocalDate desde, LocalDate hasta, int huespedes) {
    validarPeriodo(desde, hasta);
    if (huespedes < 1) throw new DatosInvalidosException("número de huéspedes inválido");

    // Los planes y los tipos se leen una vez, y el precio se calcula por (tipo, plan):
    // depende del tipo, no de la habitación, así que una lectura sirve para todas las suyas.
    // Antes se pedía por cada habitación (N+1): con 4 habitaciones y 2 planes eran 17 consultas.
    java.util.Map<Long, RoomType> tipos = new java.util.HashMap<>();
    for (RoomType tipo : inventario.tipos()) tipos.put(tipo.id(), tipo);
    List<PlanTarifario> planes = tarifas.planesActivos();
    return ofertasDe(inventario.disponibles(desde, hasta), tipos, planes, desde, hasta, huespedes, null);
  }

  /**
   * Núcleo de la oferta sobre habitaciones ya filtradas. Con caché trae las noches del periodo
   * amplio una sola vez (el calendario) en vez de pedirlas por cada día; sin caché las pide por
   * rango (la búsqueda). Misma regla en ambos: solo cambia de dónde salen las filas.
   */
  private List<OpcionOferta> ofertasDe(List<Habitacion> libres, java.util.Map<Long, RoomType> tipos,
      List<PlanTarifario> planes, LocalDate desde, LocalDate hasta, int huespedes,
      java.util.Map<String, List<TarifaRepository.TarifaNoche>> cache) {
    java.util.Map<Long, List<Habitacion>> porTipo = new java.util.HashMap<>();
    for (Habitacion habitacion : libres) {
      porTipo.computeIfAbsent(habitacion.roomTypeId(), k -> new java.util.ArrayList<>()).add(habitacion);
    }
    int noches = (int) java.time.temporal.ChronoUnit.DAYS.between(desde, hasta);
    List<OpcionOferta> ofertas = new java.util.ArrayList<>();
    for (var grupo : porTipo.entrySet()) {
      RoomType tipo = tipos.get(grupo.getKey());
      if (tipo == null || tipo.capacidadMax() < huespedes) continue;
      for (PlanTarifario plan : planes) {
        List<TarifaRepository.TarifaNoche> configuradas = cache == null
          ? tarifas.nochesDelPeriodo(plan.id(), tipo.id(), desde, hasta)
          : recorte(cache.get(tipo.id() + ":" + plan.id()), desde, hasta);
        var precio = detalleParaPlan(tipo, plan, desde, hasta, configuradas);
        if (precio.isEmpty()) continue;
        var p = precio.orElseThrow();
        for (Habitacion habitacion : grupo.getValue()) {
          ofertas.add(new OpcionOferta(habitacion, tipo, p.total(), p.plan().moneda(), noches,
            p.plan(), p.totalSinDescuento(), p.plan().descuentoPct()));
        }
      }
    }
    return ofertas;
  }

  private static List<TarifaRepository.TarifaNoche> recorte(
      List<TarifaRepository.TarifaNoche> noches, LocalDate desde, LocalDate hasta) {
    if (noches == null) return List.of();
    return noches.stream()
      .filter(n -> !n.fecha().isBefore(desde) && n.fecha().isBefore(hasta)).toList();
  }

  /**
   * Un día del calendario público: cuántas habitaciones distintas se pueden vender esa noche
   * y desde qué precio en cada moneda. Las habitaciones se cuentan una vez aunque tengan
   * varios planes, y el mínimo nunca mezcla monedas: cada moneda trae el suyo.
   */
  public record PrecioDesde(String moneda, long desdeCents) {}
  public record DiaCalendario(LocalDate fecha, int disponibles, List<PrecioDesde> precios) {}

  /**
   * Calendario de disponibilidad de un mes para unos huéspedes: una entrada por día con su
   * oferta de una noche. Es una sola petición en vez de treinta búsquedas, y usa las mismas
   * reglas que la búsqueda para no mostrar dos verdades distintas.
   */
  public List<DiaCalendario> calendarioMensual(java.time.YearMonth mes, int huespedes) {
    if (huespedes < 1) throw new DatosInvalidosException("número de huéspedes inválido");
    java.util.Map<Long, RoomType> tipos = new java.util.HashMap<>();
    for (RoomType tipo : inventario.tipos()) tipos.put(tipo.id(), tipo);
    List<PlanTarifario> planes = tarifas.planesActivos();
    // Todo el mes de una vez: las habitaciones, sus reservas y bloqueos, y las tarifas por
    // (tipo, plan). Por día solo se filtra en memoria; antes cada día repetía las 6 lecturas.
    LocalDate inicio = mes.atDay(1);
    LocalDate fin = mes.atEndOfMonth().plusDays(1);
    List<Habitacion> todas = inventario.habitaciones();
    var reservas = inventario.ocupacionesDeReservas(inicio, fin);
    var bloqueos = inventario.bloqueosDe(inicio, fin);
    java.util.Map<String, List<TarifaRepository.TarifaNoche>> cache = new java.util.HashMap<>();
    for (RoomType tipo : tipos.values()) {
      for (PlanTarifario plan : planes) {
        cache.put(tipo.id() + ":" + plan.id(),
          tarifas.nochesDelPeriodo(plan.id(), tipo.id(), inicio, fin));
      }
    }
    List<DiaCalendario> dias = new java.util.ArrayList<>();
    for (LocalDate dia = inicio; !dia.isAfter(mes.atEndOfMonth()); dia = dia.plusDays(1)) {
      final LocalDate noche = dia;
      var libres = todas.stream()
        .filter(h -> h.estado() == EstadoHabitacion.ACTIVA)
        .filter(h -> bloqueos.stream().noneMatch(b -> cubreBloqueo(b, h, noche)))
        .filter(h -> reservas.stream().noneMatch(r -> cubreReserva(r, h, noche)))
        .toList();
      var ofertas = ofertasDe(libres, tipos, planes,
        dia, dia.plusDays(1), huespedes, cache);
      long habitaciones = ofertas.stream().map(o -> o.habitacion().id()).distinct().count();
      var precios = ofertas.stream()
        .collect(java.util.stream.Collectors.groupingBy(OpcionOferta::moneda,
          java.util.stream.Collectors.mapping(OpcionOferta::totalCents,
            java.util.stream.Collectors.minBy(Long::compare))))
        .entrySet().stream()
        .sorted(java.util.Map.Entry.comparingByKey())
        .map(e -> new PrecioDesde(e.getKey(), e.getValue().orElseThrow()))
        .toList();
      dias.add(new DiaCalendario(dia, (int) habitaciones, precios));
    }
    return dias;
  }

  /**
   * Detalle de una oferta: habitación, tipo, plan que la respalda y precio noche por noche.
   *
   * Es oferta vendible, no precio informativo: valen las mismas reglas que la búsqueda. Una
   * habitación ocupada o bloqueada no tiene detalle aunque tenga tarifa, igual que no sale
   * en las ofertas.
   */
  public record NochePrecio(LocalDate fecha, long precioCents) {}
  public record DetalleOferta(Habitacion habitacion, RoomType tipo, PlanTarifario plan,
                              List<NochePrecio> noches, long totalCents, String moneda,
                              long totalSinDescuentoCents, int descuentoPct) {}

  public java.util.Optional<DetalleOferta> detalleOferta(long roomId, LocalDate desde, LocalDate hasta,
      int huespedes) {
    if (huespedes < 1) throw new DatosInvalidosException("número de huéspedes inválido");
    var habitacion = inventario.habitacionPorId(roomId)
      .filter(h -> h.estado() == EstadoHabitacion.ACTIVA);
    var tipo = habitacion.flatMap(h -> inventario.tipoPorId(h.roomTypeId()))
      .filter(t -> t.capacidadMax() >= huespedes);
    if (tipo.isEmpty()) return java.util.Optional.empty();
    // Una fila en vez de la lista completa: la regla es la misma de la búsqueda.
    if (!inventario.estaLibre(roomId, desde, hasta)) return java.util.Optional.empty();
    return precioDetallado(tipo.get(), desde, hasta).map(p -> new DetalleOferta(
      habitacion.orElseThrow(), tipo.get(), p.plan(), p.noches(), p.total(), p.plan().moneda(),
      p.totalSinDescuento(), p.plan().descuentoPct()));
  }

  /** Plan con el que un tipo cubre el periodo, noche por noche con su precio. */
  private record PrecioDetallado(PlanTarifario plan, List<NochePrecio> noches, long total,
                                 long totalSinDescuento) {}

  /**
   * Núcleo común de la oferta: precio de un tipo con un plan, noche por noche. Lo usan la
   * búsqueda (todos los planes), el alta, el calendario y el detalle (el primero válido), para
   * no tener varias versiones de la misma regla.
   */
  private java.util.Optional<PrecioDetallado> detalleParaPlan(RoomType tipo, PlanTarifario plan,
      LocalDate desde, LocalDate hasta) {
    return detalleParaPlan(tipo, plan, desde, hasta,
      tarifas.nochesDelPeriodo(plan.id(), tipo.id(), desde, hasta));
  }

  private java.util.Optional<PrecioDetallado> detalleParaPlan(RoomType tipo, PlanTarifario plan,
      LocalDate desde, LocalDate hasta, List<TarifaRepository.TarifaNoche> configuradas) {
    long noches = java.time.temporal.ChronoUnit.DAYS.between(desde, hasta);
    if (configuradas.size() != noches) return java.util.Optional.empty();
    List<NochePrecio> detalle = new java.util.ArrayList<>();
    long total = 0;
    for (TarifaRepository.TarifaNoche noche : configuradas) {
      if (noche.cerrado()) return java.util.Optional.empty();
      if (noche.minEstancia() != null && noches < noche.minEstancia()) return java.util.Optional.empty();
      if (noche.maxEstancia() != null && noches > noche.maxEstancia()) return java.util.Optional.empty();
      detalle.add(new NochePrecio(noche.fecha(), noche.precioCents()));
      total += noche.precioCents();
    }
    long conDescuento = aplicarDescuento(total, plan.descuentoPct());
    return java.util.Optional.of(new PrecioDetallado(plan, detalle, conDescuento, total));
  }

  private java.util.Optional<PrecioDetallado> precioDetallado(RoomType tipo, LocalDate desde,
      LocalDate hasta) {
    for (PlanTarifario plan : tarifas.planesActivos()) {
      var precio = detalleParaPlan(tipo, plan, desde, hasta);
      if (precio.isPresent()) return precio;
    }
    return java.util.Optional.empty();
  }

  /**
   * El descuento del plan se aplica al total y se redondea al céntimo: las noches no se
   * fraccionan y el desglose sigue sumando el precio de lista. Sin descuento no se toca nada.
   */
  static long aplicarDescuento(long totalCents, int descuentoPct) {
    if (descuentoPct <= 0) return totalCents;
    return Math.round(totalCents * (100L - descuentoPct) / 100.0);
  }

  /**
   * Suma las noches del periodo en el primer plan activo con tarifa completa.
   * Devuelve null si ninguna noche está configurada, alguna está cerrada o incumple la estancia:
   * la ausencia de precio es un dato, no un cero.
   */
  private PrecioTotalizado calcularPrecio(RoomType tipo, LocalDate desde, LocalDate hasta) {
    long noches = java.time.temporal.ChronoUnit.DAYS.between(desde, hasta);
    return precioDetallado(tipo, desde, hasta)
      .map(p -> new PrecioTotalizado(p.total(), p.plan().moneda(), (int) noches, p.plan(),
        p.totalSinDescuento()))
      .orElse(null);
  }

  private void validarPeriodo(LocalDate desde, LocalDate hasta) {
    if (desde == null || hasta == null || !desde.isBefore(hasta))
      throw new DatosInvalidosException("la salida debe ser posterior a la llegada");
  }

  /** Total del periodo solo si todas las noches tienen tarifa válida. */
  private record PrecioTotalizado(long totalCents, String moneda, int noches, PlanTarifario plan,
                                   long totalSinDescuento) {}
}