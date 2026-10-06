package co.hotel.inventario;

import java.time.LocalDate;
import org.springframework.stereotype.Service;

/**
 * Tarifas del hotel. No hay precios por defecto ni moneda por defecto: sin configuración del
 * hotel, el flujo público no puede mostrar un precio.
 */
@Service
public class TarifaService {
  private final TarifaRepository repo;

  public TarifaService(TarifaRepository repo) { this.repo = repo; }

  public PlanTarifario crearPlan(String codigo, String nombre, String moneda) {
    if (codigo == null || codigo.isBlank()) throw new DatosInvalidosException("código de plan requerido");
    if (nombre == null || nombre.isBlank()) throw new DatosInvalidosException("nombre de plan requerido");
    if (moneda == null || !moneda.matches("[A-Z]{3}"))
      throw new DatosInvalidosException("moneda inválida: use el código ISO 4217, p.ej. COP");
    return repo.planPorId(repo.insertarPlan(codigo.trim(), nombre.trim(), moneda.trim()))
      .orElseThrow(() -> new DatosInvalidosException("no se pudo crear el plan"));
  }

  public PlanTarifario planPorId(long id) {
    return repo.planPorId(id).orElseThrow(() -> new DatosInvalidosException("plan tarifario no encontrado"));
  }

  public void fijarPrecio(PlanTarifario plan, long tipoId, LocalDate fecha, long precioCents) {
    if (precioCents < 0) throw new DatosInvalidosException("el precio no puede ser negativo");
    repo.fijarPrecio(plan.id(), tipoId, fecha, precioCents);
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