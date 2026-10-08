package co.hotel.inventario;

import co.hotel.reservas.SqliteTransactionExecutor;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Tarifas del hotel. No hay precios por defecto ni moneda por defecto: sin configuración del
 * hotel, el flujo público no puede mostrar un precio.
 */
@Service
public class TarifaService {
  /**
   * Techo técnico por noche en céntimos (100 millones en la moneda del plan): ninguna tarifa
   * real se acerca, y con él ni un año de noches al tope desborda las sumas en long. No es
   * una regla comercial, es el límite a partir del cual los totales dejarían de ser exactos.
   */
  public static final long PRECIO_MAXIMO_CENTS = 10_000_000_000L;

  private final TarifaRepository repo;
  private final InventarioRepository inventario;
  private final SqliteTransactionExecutor tx;

  public TarifaService(TarifaRepository repo, InventarioRepository inventario,
                       SqliteTransactionExecutor tx) {
    this.repo = repo;
    this.inventario = inventario;
    this.tx = tx;
  }

  public PlanTarifario crearPlan(String codigo, String nombre, String moneda) {
    return crearPlan(codigo, nombre, moneda, 0);
  }

  /**
   * Plan con descuento porcentual desde el alta ("Flexible -15%"). Sin descuento declarado no hay
   * rebaja: el 0 por defecto no es una suposición, es ausencia de descuento.
   */
  public PlanTarifario crearPlan(String codigo, String nombre, String moneda, int descuentoPct) {
    if (codigo == null || codigo.isBlank()) throw new DatosInvalidosException("código de plan requerido");
    if (nombre == null || nombre.isBlank()) throw new DatosInvalidosException("nombre de plan requerido");
    String iso = moneda == null ? "" : moneda.trim().toUpperCase();
    try {
      java.util.Currency.getInstance(iso);
    } catch (IllegalArgumentException e) {
      throw new DatosInvalidosException("moneda inválida: use el código ISO 4217, p.ej. COP");
    }
    validarDescuento(descuentoPct);
    String cod = codigo.trim();
    try {
      return repo.planPorId(repo.insertarPlan(cod, nombre.trim(), iso, descuentoPct))
        .orElseThrow(() -> new DatosInvalidosException("no se pudo crear el plan"));
    } catch (org.springframework.dao.DataAccessException e) {
      for (Throwable t = e; t != null; t = t.getCause())
        if (t.getMessage() != null && t.getMessage().contains("UNIQUE"))
          throw new DatosInvalidosException("ya existe un plan con el código " + cod);
      throw e;
    }
  }

  /** Cambia el descuento de un plan existente. Las reservas ya guardadas no se tocan. */
  public PlanTarifario fijarDescuento(long planId, int descuentoPct) {
    validarDescuento(descuentoPct);
    PlanTarifario plan = planPorId(planId);
    repo.fijarDescuento(plan.id(), descuentoPct);
    return planPorId(plan.id());
  }

  private static void validarDescuento(int descuentoPct) {
    if (descuentoPct < 0 || descuentoPct > 100)
      throw new DatosInvalidosException("el descuento debe estar entre 0 y 100");
  }

  public PlanTarifario planPorId(long id) {
    return repo.planPorId(id).orElseThrow(() -> new DatosInvalidosException("plan tarifario no encontrado"));
  }

  public Optional<TarifaRepository.TarifaNoche> nocheDe(long planId, long tipoId, LocalDate fecha) {
    return repo.nocheDe(planId, tipoId, fecha);
  }

  /** Una noche dentro de un lote: lo ausente se conserva, como en el alta individual. */
  public record CambioNoche(LocalDate fecha, Long precioCents, Integer minEstancia,
                            Integer maxEstancia, Boolean cerrado) {}

  /** Resultado por fila de la previa: lo válido trae lo que se guardaría, lo inválido el motivo. */
  public record FilaPrevia(LocalDate fecha, boolean valida, String motivo, Long precioCents,
                           boolean cerrado, boolean nueva) {}

  public record PreviaLote(java.util.List<FilaPrevia> filas) {
    public boolean lista() { return filas.stream().allMatch(FilaPrevia::valida); }
  }

  /** El lote trae al menos una fila inválida: nada se escribió y la previa dice qué falló. */
  public static class LoteRechazadoException extends DatosInvalidosException {
    private final PreviaLote previa;
    public LoteRechazadoException(PreviaLote previa) {
      super(previa.filas().stream().filter(f -> !f.valida()).findFirst()
        .map(f -> (f.fecha() == null ? "sin fecha" : f.fecha().toString()) + ": " + f.motivo())
        .orElse("lote inválido"));
      this.previa = previa;
    }
    public PreviaLote previa() { return previa; }
  }

  /**
   * Previa de un lote: valida cada fila y dice qué se guardaría, sin escribir nada.
   * Lo inválido no aborta la previa: cada fila trae su propio motivo. Las noches del
   * rango viajan una vez, no una por fila.
   */
  public PreviaLote previsualizarLote(long planId, long tipoId, java.util.List<CambioNoche> cambios) {
    return resolverLote(planId, tipoId, cambios).previa();
  }

  /**
   * Aplica un lote en una sola transacción: una fila inválida revierte todo y responde
   * con el detalle por fila. Lo omitido se conserva, incluido el cierre. No resuelve dos
   * veces ni relee lo que acaba de guardar: lo resuelto es lo que se escribe.
   */
  public java.util.List<TarifaRepository.TarifaNoche> aplicarLote(long planId, long tipoId,
      java.util.List<CambioNoche> cambios) {
    var resuelto = resolverLote(planId, tipoId, cambios);
    if (!resuelto.previa().lista()) throw new LoteRechazadoException(resuelto.previa());
    return tx.enTransaccion(estado -> {
      var guardadas = new java.util.ArrayList<TarifaRepository.TarifaNoche>();
      for (var r : resuelto.resueltas()) {
        repo.guardarNoche(planId, tipoId, r.fecha(), r.precio(), r.minimo(), r.maximo(), r.cerrada());
        guardadas.add(new TarifaRepository.TarifaNoche(r.fecha(), r.precio(), r.minimo(), r.maximo(),
          r.cerrada()));
      }
      return guardadas;
    });
  }

  private record LoteResuelto(PreviaLote previa, java.util.List<ResueltaNoche> resueltas) {}

  private LoteResuelto resolverLote(long planId, long tipoId, java.util.List<CambioNoche> cambios) {
    validarLoteBasico(planId, tipoId, cambios);
    var previas = previasPorFecha(planId, tipoId, cambios);
    var filas = new java.util.ArrayList<FilaPrevia>();
    var resueltas = new java.util.ArrayList<ResueltaNoche>();
    for (CambioNoche c : cambios) {
      if (c.fecha() == null) {
        filas.add(new FilaPrevia(null, false, "la fecha es obligatoria", null, false, false));
        continue;
      }
      try {
        var r = resolverConPrevia(c.fecha(), c.precioCents(), c.minEstancia(), c.maxEstancia(),
          c.cerrado(), java.util.Optional.ofNullable(previas.get(c.fecha())));
        resueltas.add(r);
        filas.add(new FilaPrevia(c.fecha(), true, "", r.precio(), r.cerrada(), r.crea()));
      } catch (DatosInvalidosException e) {
        filas.add(new FilaPrevia(c.fecha(), false, e.getMessage(), null, false, false));
      }
    }
    return new LoteResuelto(new PreviaLote(filas), resueltas);
  }

  /** Las noches del rango que cubre el lote, en una sola lectura indexada por fecha. */
  private java.util.Map<LocalDate, TarifaRepository.TarifaNoche> previasPorFecha(long planId,
      long tipoId, java.util.List<CambioNoche> cambios) {
    LocalDate min = null;
    LocalDate max = null;
    for (CambioNoche c : cambios) {
      if (c.fecha() == null) continue;
      if (min == null || c.fecha().isBefore(min)) min = c.fecha();
      if (max == null || c.fecha().isAfter(max)) max = c.fecha();
    }
    var mapa = new java.util.HashMap<LocalDate, TarifaRepository.TarifaNoche>();
    if (min == null) return mapa;
    for (var n : repo.nochesDelPeriodo(planId, tipoId, min, max.plusDays(1))) mapa.put(n.fecha(), n);
    return mapa;
  }

  private void validarLoteBasico(long planId, long tipoId, java.util.List<CambioNoche> cambios) {
    if (cambios == null || cambios.isEmpty())
      throw new DatosInvalidosException("el lote no trae noches");
    if (cambios.size() > 366)
      throw new DatosInvalidosException("el lote no puede pasar de 366 noches");
    if (repo.planPorId(planId).isEmpty())
      throw new DatosInvalidosException("plan tarifario no encontrado");
    if (inventario.tipoPorId(tipoId).isEmpty())
      throw new DatosInvalidosException("tipo de habitación no encontrado");
  }

  private record ResueltaNoche(LocalDate fecha, long precio, Integer minimo, Integer maximo,
                               boolean cerrada, boolean crea) {}

  private ResueltaNoche resolverNoche(long planId, long tipoId, LocalDate fecha,
      Long precioCents, Integer minEstancia, Integer maxEstancia, Boolean cerrado) {
    if (fecha == null) throw new DatosInvalidosException("la fecha es obligatoria");
    return resolverConPrevia(fecha, precioCents, minEstancia, maxEstancia, cerrado,
      repo.nocheDe(planId, tipoId, fecha));
  }

  private ResueltaNoche resolverConPrevia(LocalDate fecha,
      Long precioCents, Integer minEstancia, Integer maxEstancia, Boolean cerrado,
      java.util.Optional<TarifaRepository.TarifaNoche> previa) {
    if (precioCents != null && precioCents < 0)
      throw new DatosInvalidosException("el precio no puede ser negativo");
    if (precioCents != null && precioCents > PRECIO_MAXIMO_CENTS)
      throw new DatosInvalidosException("el precio supera el máximo de "
        + PRECIO_MAXIMO_CENTS + " céntimos por noche");
    if (minEstancia != null && minEstancia < 1)
      throw new DatosInvalidosException("la estancia mínima debe ser al menos 1 noche");
    if (maxEstancia != null && maxEstancia < 1)
      throw new DatosInvalidosException("la estancia máxima debe ser al menos 1 noche");
    if (previa.isEmpty() && precioCents == null) {
      throw new DatosInvalidosException(
        "la noche no existe: fija primero el precio para crearla");
    }
    long precio = precioCents != null ? precioCents : previa.orElseThrow().precioCents();
    Integer minimo = minEstancia != null ? minEstancia : previa.map(TarifaRepository.TarifaNoche::minEstancia).orElse(null);
    Integer maximo = maxEstancia != null ? maxEstancia : previa.map(TarifaRepository.TarifaNoche::maxEstancia).orElse(null);
    if (minimo != null && maximo != null && minimo > maximo) {
      throw new DatosInvalidosException(
        "la estancia mínima (" + minimo + ") no puede superar a la máxima (" + maximo + ")");
    }
    boolean cerrada = cerrado != null ? cerrado : previa.map(TarifaRepository.TarifaNoche::cerrado).orElse(false);
    return new ResueltaNoche(fecha, precio, minimo, maximo, cerrada, previa.isEmpty());
  }

  public void fijarPrecio(PlanTarifario plan, long tipoId, LocalDate fecha, long precioCents) {
    if (precioCents < 0) throw new DatosInvalidosException("el precio no puede ser negativo");
    if (precioCents > PRECIO_MAXIMO_CENTS) throw new DatosInvalidosException("el precio supera el máximo de "
      + PRECIO_MAXIMO_CENTS + " céntimos por noche");
    repo.fijarPrecio(plan.id(), tipoId, fecha, precioCents);
  }

  /**
   * Fija una noche completa en una sola unidad transaccional: precio, restricciones y cierre.
   *
   * Semántica explícita: lo omitido se conserva (incluido el cierre: cambiar el precio no
   * reabre); lo presente se valida todo antes de escribir nada. Sin fila previa hace falta el
   * precio: una restricción sobre una noche inexistente no significa nada y antes pasaba como
   * éxito silencioso.
   */
  public TarifaRepository.TarifaNoche fijarNoche(long planId, long tipoId, LocalDate fecha,
      Long precioCents, Integer minEstancia, Integer maxEstancia, Boolean cerrado) {
    if (fecha == null) throw new DatosInvalidosException("la fecha es obligatoria");
    if (repo.planPorId(planId).isEmpty())
      throw new DatosInvalidosException("plan tarifario no encontrado");
    if (inventario.tipoPorId(tipoId).isEmpty())
      throw new DatosInvalidosException("tipo de habitación no encontrado");
    var r = resolverNoche(planId, tipoId, fecha, precioCents, minEstancia, maxEstancia, cerrado);
    return tx.enTransaccion(estado -> {
      repo.guardarNoche(planId, tipoId, r.fecha(), r.precio(), r.minimo(), r.maximo(), r.cerrada());
      return repo.nocheDe(planId, tipoId, r.fecha()).orElseThrow();
    });
  }

  public void fijarMinimoEstancia(PlanTarifario plan, long tipoId, LocalDate fecha, int noches) {
    if (noches < 1) throw new DatosInvalidosException("la estancia mínima debe ser al menos 1 noche");
    repo.fijarMinimoEstancia(plan.id(), tipoId, fecha, noches);
  }

  public void fijarMaximoEstancia(PlanTarifario plan, long tipoId, LocalDate fecha, int noches) {
    if (noches < 1) throw new DatosInvalidosException("la estancia máxima debe ser al menos 1 noche");
    repo.fijarMaximoEstancia(plan.id(), tipoId, fecha, noches);
  }

  /** El hotel marca una noche como no vendible (por ejemplo, trabajo de mantenimiento). */
  public void cerrarNoche(PlanTarifario plan, long tipoId, LocalDate fecha) {
    repo.cerrarNoche(plan.id(), tipoId, fecha);
  }

  public void abrirNoche(PlanTarifario plan, long tipoId, LocalDate fecha) {
    repo.abrirNoche(plan.id(), tipoId, fecha);
  }

  /** Los planes con los que el hotel ofrece inventario. Sin planes, no hay nada que tarifar. */
  public java.util.List<PlanTarifario> listarPlanes() {
    return repo.planesActivos();
  }

  /**
   * Noches con precio del tipo en el intervalo, para que el hotel vea y corrija lo que ha fijado.
   * El intervalo se acota a un año: más que eso nadie lo revisa en pantalla y solo carga la tabla.
   */
  public java.util.List<TarifaRepository.TarifaNoche> nochesDe(long planId, long tipoId,
      LocalDate desde, LocalDate hasta) {
    if (desde == null || hasta == null)
      throw new DatosInvalidosException("desde y hasta son obligatorios");
    if (!hasta.isAfter(desde))
      throw new DatosInvalidosException("'hasta' debe ser posterior a 'desde'");
    if (java.time.temporal.ChronoUnit.DAYS.between(desde, hasta) > 366)
      throw new DatosInvalidosException("el periodo no puede superar un año");
    return repo.nochesDelPeriodo(planId, tipoId, desde, hasta);
  }
}