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
    if (moneda == null || !moneda.matches("[A-Z]{3}"))
      throw new DatosInvalidosException("moneda inválida: use el código ISO 4217, p.ej. COP");
    validarDescuento(descuentoPct);
    return repo.planPorId(repo.insertarPlan(codigo.trim(), nombre.trim(), moneda.trim(), descuentoPct))
      .orElseThrow(() -> new DatosInvalidosException("no se pudo crear el plan"));
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

  public void fijarPrecio(PlanTarifario plan, long tipoId, LocalDate fecha, long precioCents) {
    if (precioCents < 0) throw new DatosInvalidosException("el precio no puede ser negativo");
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
    if (precioCents != null && precioCents < 0)
      throw new DatosInvalidosException("el precio no puede ser negativo");
    if (minEstancia != null && minEstancia < 1)
      throw new DatosInvalidosException("la estancia mínima debe ser al menos 1 noche");
    if (maxEstancia != null && maxEstancia < 1)
      throw new DatosInvalidosException("la estancia máxima debe ser al menos 1 noche");
    var previa = repo.nocheDe(planId, tipoId, fecha);
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
    return tx.enTransaccion(estado -> {
      repo.guardarNoche(planId, tipoId, fecha, precio, minimo, maximo, cerrada);
      return repo.nocheDe(planId, tipoId, fecha).orElseThrow();
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