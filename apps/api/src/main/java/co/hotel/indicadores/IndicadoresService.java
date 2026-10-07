package co.hotel.indicadores;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Indicadores de las tres fases de la implementación del software.
 *
 * Reglas que no se negocian:
 * - Un indicador sin datos no vale cero: se devuelve {@link ResultadoIndicador#faltante} con el motivo.
 * - No se inventan líneas base ni metas: llegan en null hasta que el hotel las fije.
 * - No se atribuyen ventas a campañas sin datos de origen medibles.
 */
@Service
public class IndicadoresService {
  private final IndicadoresRepository repo;

  public IndicadoresService(IndicadoresRepository repo) { this.repo = repo; }

  public DefinicionIndicador definicion(String clave) {
    return definiciones().stream().filter(d -> d.clave().equals(clave)).findFirst()
      .orElseThrow(() -> new IllegalArgumentException("indicador desconocido: " + clave));
  }

  public List<DefinicionIndicador> definiciones() { return repo.definiciones(); }

  /**
   * Calcula un indicador. NO persiste: `indicator_results` no la consulta nadie, así que guardar
   * aquí era trabajo que se perdía en cada lectura del informe.
   *
   * Antes persistía, y eso rompía el sentido de un GET: `/api/admin/indicadores` y su CSV son
   * lecturas, y el navegador prefetchea enlaces. Abrir el panel escribía doce filas sin que nadie
   * las hubiera pedido. Si algún día hace falta un histórico de indicadores, que sea por una tarea
   * programada que lo escriba a propósito, no de paso por una pantalla.
   */
  public ResultadoIndicador calcular(String clave, String periodo) {
    DefinicionIndicador definicion = definicion(clave);
    return switch (definicion.fase()) {
      case 1 -> calcularFase1(definicion, periodo);
      case 2 -> calcularFase2(definicion, periodo);
      default -> calcularFase3(definicion, periodo);
    };
  }

  private ResultadoIndicador calcularFase1(DefinicionIndicador d, String periodo) {
    return switch (d.clave()) {
      case "f1_inventario_cargado" -> {
        Integer esperado = repo.inventarioEsperado();
        if (esperado == null || esperado == 0)
          yield ResultadoIndicador.faltante(d, periodo,
            "falta el inventario esperado: el hotel aún no declara cuántas habitaciones debía cargar");
        long cargadas = repo.habitacionesActivas();
        yield ResultadoIndicador.medido(d, periodo, porcentaje(cargadas, esperado), cargadas,
          (long) esperado, null);
      }
      case "f1_canales_conectados" -> {
        String desde = periodoDesde(periodo);
        long intentadas = repo.sincronizacionesIntentadas(desde, periodoHasta(periodo));
        long exitosas = repo.sincronizacionesExitosas(desde, periodoHasta(periodo));
        if (exitosas == 0)
          yield ResultadoIndicador.faltante(d, periodo, "no hay ninguna sincronización autorizada exitosa");
        yield ResultadoIndicador.medido(d, periodo, porcentaje(exitosas, intentadas), exitosas, intentadas, null);
      }
      case "f1_pruebas_sync" -> {
        String desde = periodoDesde(periodo);
        String hasta = periodoHasta(periodo);
        long exitosas = repo.sincronizacionesExitosas(desde, hasta);
        long intentadas = repo.sincronizacionesIntentadas(desde, hasta);
        if (intentadas == 0)
          yield ResultadoIndicador.faltante(d, periodo, "no hay sincronizaciones registradas en el periodo");
        yield ResultadoIndicador.medido(d, periodo, porcentaje(exitosas, intentadas), exitosas, intentadas, null);
      }
      default -> ResultadoIndicador.faltante(d, periodo, "indicador de la fase 1 pendiente de datos de mapeos");
    };
  }

  private ResultadoIndicador calcularFase2(DefinicionIndicador d, String periodo) {
    String desde = periodoDesde(periodo);
    String hasta = periodoHasta(periodo);
    String tipo = switch (d.clave()) {
      case "f2_capacitaciones" -> "CAPACITACION";
      case "f2_uso_personal" -> "USO_PERSONAL";
      case "f2_difusion" -> "DIFUSION";
      default -> null;
    };
    if (tipo == null) return ResultadoIndicador.faltante(d, periodo, "indicador de fase 2 sin tipo asociado");

    long conteo = repo.actividadesDeTipo(tipo, desde, hasta);
    String nota = conteo == 0
      ? "el hotel todavía no ha confirmado actividades de este tipo en el periodo"
      : "conteo de actividades confirmadas por el hotel; no se atribuyen ventas a campañas";
    return ResultadoIndicador.medido(d, periodo, (double) conteo, conteo, null, nota);
  }

  private ResultadoIndicador calcularFase3(DefinicionIndicador d, String periodo) {
    String desde = periodoDesde(periodo);
    String hasta = periodoHasta(periodo);
    return switch (d.clave()) {
      case "f3_ocupacion" -> {
        long disponibles = repo.nochesDisponibles(desde, hasta);
        if (disponibles == 0)
          yield ResultadoIndicador.faltante(d, periodo,
            "no hay habitaciones cargadas: sin inventario no hay noches disponibles para la venta");
        long ocupadas = repo.nochesOcupadas(desde, hasta);
        yield ResultadoIndicador.medido(d, periodo, porcentaje(ocupadas, disponibles), ocupadas, disponibles, null);
      }
      case "f3_tasa_cancelacion" -> {
        long creadas = repo.reservasCreadas(desde, hasta);
        if (creadas == 0)
          yield ResultadoIndicador.faltante(d, periodo, "no hay reservas creadas en el periodo");
        long canceladas = repo.reservasCanceladas(desde, hasta);
        yield ResultadoIndicador.medido(d, periodo, porcentaje(canceladas, creadas), canceladas, creadas, null);
      }
      case "f3_sync_exitosas" -> {
        long intentadas = repo.sincronizacionesIntentadas(desde, hasta);
        if (intentadas == 0)
          yield ResultadoIndicador.faltante(d, periodo, "no hay sincronizaciones registradas en el periodo");
        long exitosas = repo.sincronizacionesExitosas(desde, hasta);
        yield ResultadoIndicador.medido(d, periodo, porcentaje(exitosas, intentadas), exitosas, intentadas, null);
      }
      case "f3_sobreventa" -> ResultadoIndicador.faltante(d, periodo,
        "sin registro de incidentes: el control transaccional rechaza la sobreventa antes de que "
          + "ocurra, así que un cero afirmaría una medición que nadie hizo");
      case "f3_reservas_por_canal" -> {
        long total = repo.reservasCreadas(desde, hasta);
        if (total == 0) yield ResultadoIndicador.faltante(d, periodo, "no hay reservas en el periodo");
        yield ResultadoIndicador.medido(d, periodo, (double) total, total, null, "detalle por canal en el informe");
      }
      default -> ResultadoIndicador.faltante(d, periodo, "indicador de fase 3 sin datos disponibles");
    };
  }

  /** Reservas por canal del periodo. */
  public Map<String, Long> reservasPorCanal(String periodo) {
    return repo.reservasPorOrigen(periodoDesde(periodo), periodoHasta(periodo));
  }

  public List<Actividad> actividades(String periodo) {
    return repo.actividades(periodoDesde(periodo), periodoHasta(periodo));
  }

  public void fijarInventarioEsperado(int cantidad) { repo.fijarInventarioEsperado(cantidad); }

  /**
   * Registra una actividad de adopción o difusión. El hotel confirma que ocurrió; si no hay datos
   * de origen medibles, queda marcado así para que nadie atribuya ventas a la campaña.
   */
  public void registrarActividad(String tipo, String descripcion, String fecha, Integer participantes,
                                 String confirmadaPor, boolean conDatosDeOrigen) {
    repo.registrarActividad(tipo, descripcion, fecha, participantes, confirmadaPor, conDatosDeOrigen);
  }

  /** Informe completo del periodo, cubriendo las tres fases. */
  public List<ResultadoIndicador> informe(String periodo) {
    List<ResultadoIndicador> informe = new ArrayList<>();
    for (DefinicionIndicador d : definiciones()) informe.add(calcular(d.clave(), periodo));
    return informe;
  }

  /** CSV con una fila por indicador. Los indicadores sin datos se marcan SIN_DATOS, nunca 0. */
  public String exportarCsv(String periodo) {
    StringBuilder csv = new StringBuilder();
    csv.append("clave,fase,nombre,definicion,formula,fuente,periodo,unidad,linea_base,meta,responsable,")
      .append("resultado,numerador,denominador,estado,nota\n");
    for (ResultadoIndicador r : informe(periodo)) {
      DefinicionIndicador d = r.definicion();
      csv.append(csv(d.clave())).append(',').append(d.fase()).append(',')
        .append(csv(d.nombre())).append(',').append(csv(d.definicion())).append(',')
        .append(csv(d.formula())).append(',').append(csv(d.fuente())).append(',')
        .append(csv(r.periodo())).append(',').append(csv(d.unidad())).append(',')
        .append(csv(d.lineaBase())).append(',').append(csv(d.meta())).append(',')
        .append(csv(d.responsable())).append(',')
        .append(r.tieneResultado() ? decimal(r.valor()) : "")
        .append(',').append(r.numerador() == null ? "" : r.numerador()).append(',')
        .append(r.denominador() == null ? "" : r.denominador()).append(',')
        .append(r.tieneResultado() ? "CALCULADO" : "SIN_DATOS").append(',')
        .append(csv(r.tieneResultado() ? r.nota() : r.motivoFaltante()))
        .append('\n');
    }
    return csv.toString();
  }

  private static double porcentaje(long numerador, long denominador) {
    return denominador == 0 ? 0.0 : Math.round(numerador * 10000.0 / denominador) / 100.0;
  }

  /**
   * Decimal con punto, siempre. String.format usa el locale del sistema: en es-CO devuelve "0,00",
   * y con coma separadora de campos el CSV queda ilegible para una hoja de cálculo.
   */
  private static String decimal(double valor) {
    return String.format(java.util.Locale.ROOT, "%.2f", valor);
  }

  /** "2026-11" -> ["2026-11-01", "2026-12-01"). Un periodo "PUNTO" usa todo el histórico. */
  static String periodoDesde(String periodo) {
    return periodo.length() == 7 ? periodo + "-01" : "0000-01-01";
  }

  static String periodoHasta(String periodo) {
    if (periodo.length() != 7) return "9999-12-31";
    LocalDate inicio = LocalDate.parse(periodo + "-01");
    return inicio.plusMonths(1).toString();
  }

  /**
   * Celda CSV entre comillas y con las comillas internas duplicadas. Y con una más: entrecomillar
   * NO impide que Excel ejecute una fórmula, así que si el texto empieza por = + - @ se le
   * antepone una comilla simple para que quede como texto. Los textos salen de la base (nombres,
   * motivos, responsables que escribe el hotel), así que alguien puede colar una fórmula sin
   * querer —o queriendo— y el que abre la hoja no tiene por qué pagarla.
   */
  private static String csv(String valor) {
    if (valor == null) return "";
    String seguro = valor.isEmpty() || "=+-@".indexOf(valor.charAt(0)) < 0 ? valor : "'" + valor;
    return "\"" + seguro.replace("\"", "\"\"") + "\"";
  }
}