package co.hotel.indicadores;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Informe de indicadores. Cada indicador viaja con su definición, fórmula, fuente, periodo, unidad
 * y, si falta el dato, el motivo. Lo que no se calcula es explícito: `resultado: null`.
 */
@RestController
public class IndicadoresController {
  private final IndicadoresService indicadores;

  public IndicadoresController(IndicadoresService indicadores) { this.indicadores = indicadores; }

  public record ActividadReq(String tipo, String descripcion, String fecha, Integer participantes,
                             String confirmadaPor, Boolean conDatosDeOrigen) {}

  @GetMapping("/api/admin/indicadores")
  public ResponseEntity<?> informe(@RequestParam(required = false) String periodo) {
    try {
      String p = normalizarPeriodo(periodo);
      List<Map<String, Object>> filas = indicadores.informe(p).stream()
        .map(IndicadoresController::fila).toList();
      return ResponseEntity.ok(Map.of(
        "periodo", p,
        "indicadores", filas,
        "reservasPorCanal", indicadores.reservasPorCanal(p),
        "actividades", indicadores.actividades(p).stream().map(IndicadoresController::actividad).toList()));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
  }

  /** Exportación CSV: una fila por indicador, con los sin datos marcados como SIN_DATOS. */
  @GetMapping(value = "/api/admin/indicadores.csv", produces = "text/csv;charset=UTF-8")
  public ResponseEntity<?> csv(@RequestParam(required = false) String periodo) {
    final String p;
    try {
      p = normalizarPeriodo(periodo);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
    return ResponseEntity.ok()
      .header("Content-Disposition", "attachment; filename=indicadores-" + p + ".csv")
      .contentType(MediaType.parseMediaType("text/csv"))
      .body(indicadores.exportarCsv(p));
  }

  /** El hotel declara cuántas habitaciones debía cargar: sin eso, el porcentaje no tiene denominador. */
  @PostMapping("/api/admin/indicadores/inventario-esperado")
  public ResponseEntity<?> fijarInventarioEsperado(
      @RequestBody(required = false) Map<String, Integer> cuerpo) {
    Integer esperado = cuerpo == null ? null : cuerpo.get("habitaciones");
    if (esperado == null || esperado < 0) {
      return ResponseEntity.badRequest()
        .body(Map.of("error", "habitaciones es obligatorio y no puede ser negativo"));
    }
    indicadores.fijarInventarioEsperado(esperado);
    return ResponseEntity.ok(Map.of("inventarioEsperado", esperado));
  }

  @PostMapping("/api/admin/indicadores/actividades")
  public ResponseEntity<?> registrarActividad(@RequestBody ActividadReq req) {
    if (req == null || req.tipo() == null || req.descripcion() == null || req.fecha() == null)
      return ResponseEntity.badRequest().body(Map.of("error", "tipo, descripcion y fecha son obligatorios"));
    String fecha = req.fecha().trim();
    try {
      indicadores.registrarActividad(req.tipo(), req.descripcion(), fecha, req.participantes(),
        req.confirmadaPor(), Boolean.TRUE.equals(req.conDatosDeOrigen()));
      return ResponseEntity.status(201).body(actividad(new Actividad(req.tipo().trim(),
        req.descripcion().trim(), LocalDate.parse(fecha), req.participantes(), req.confirmadaPor(),
        Boolean.TRUE.equals(req.conDatosDeOrigen()))));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
  }

  private static Map<String, Object> actividad(Actividad a) {
    return Map.of(
      "tipo", a.tipo(),
      "descripcion", a.descripcion(),
      "fecha", a.fecha().toString(),
      "participantes", a.participantes() == null ? "" : a.participantes(),
      "confirmadaPor", a.confirmadaPor() == null ? "" : a.confirmadaPor(),
      "tieneDatosDeOrigen", a.tieneDatosDeOrigen(),
      "nota", a.tieneDatosDeOrigen()
        ? "hay datos de origen medibles"
        : "sin datos de origen: no se atribuyen ventas a esta actividad");
  }

  private static Map<String, Object> fila(ResultadoIndicador r) {
    DefinicionIndicador d = r.definicion();
    Map<String, Object> fila = new java.util.LinkedHashMap<>();
    fila.put("clave", d.clave());
    fila.put("fase", d.fase());
    fila.put("nombre", d.nombre());
    fila.put("definicion", d.definicion());
    fila.put("formula", d.formula());
    fila.put("fuente", d.fuente());
    fila.put("periodo", r.periodo());
    fila.put("unidad", d.unidad());
    fila.put("resultado", r.valor());
    fila.put("numerador", r.numerador());
    fila.put("denominador", r.denominador());
    fila.put("tieneResultado", r.tieneResultado());
    fila.put("datosFaltantes", r.motivoFaltante());
    fila.put("lineaBase", d.lineaBase());
    fila.put("meta", d.meta());
    fila.put("responsable", d.responsable());
    fila.put("nota", r.nota());
    return fila;
  }

  /** Sin periodo, el mes en curso. Formato YYYY-MM con mes real: 2026-99 no es un periodo. */
  private static String normalizarPeriodo(String periodo) {
    if (periodo == null || periodo.isBlank()) return LocalDate.now().toString().substring(0, 7);
    if (!periodo.matches("^\\d{4}-(0[1-9]|1[0-2])$"))
      throw new IllegalArgumentException("el periodo debe tener formato YYYY-MM con mes de 01 a 12");
    return periodo;
  }
}