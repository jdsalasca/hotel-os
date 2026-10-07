package co.hotel.indicadores;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** SQL de definiciones, resultados y actividades de adopción. */
@Repository
public class IndicadoresRepository {
  private final JdbcTemplate jdbc;

  public IndicadoresRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public List<DefinicionIndicador> definiciones() {
    return jdbc.query("SELECT clave,fase,nombre,definicion,formula,fuente,unidad,periodo_por_defecto,"
        + "linea_base,meta,responsable FROM indicator_definitions WHERE activa=1 ORDER BY fase,clave",
      (rs, n) -> new DefinicionIndicador(rs.getString("clave"), rs.getInt("fase"), rs.getString("nombre"),
        rs.getString("definicion"), rs.getString("formula"), rs.getString("fuente"), rs.getString("unidad"),
        rs.getString("periodo_por_defecto"), rs.getString("linea_base"), rs.getString("meta"),
        rs.getString("responsable")));
  }

  /** Habitaciones activas cargadas. */
  public long habitacionesActivas() {
    return jdbc.queryForObject("SELECT COUNT(*) FROM rooms WHERE estado='ACTIVA'", Long.class);
  }

  /** Noches de habitación ocupadas en el periodo: cada línea de reserva aporta su duración. */
  public long nochesOcupadas(String desde, String hasta) {
    Long n = jdbc.queryForObject("SELECT COALESCE(SUM(julianday(ri.hasta) - julianday(ri.desde)), 0) "
        + "FROM reservation_items ri JOIN reservations r ON r.id = ri.reservation_id "
        + "WHERE r.estado IN ('PENDIENTE','CONFIRMADA') AND ri.desde < ? AND ? < ri.hasta",
      Long.class, hasta, desde);
    return n == null ? 0L : n;
  }

  /** Noches disponibles para la venta: habitaciones activas por noche del periodo. */
  public long nochesDisponibles(String desde, String hasta) {
    long dias = java.time.temporal.ChronoUnit.DAYS.between(
      java.time.LocalDate.parse(desde), java.time.LocalDate.parse(hasta));
    return dias * habitacionesActivas();
  }

  public long reservasCreadas(String desde, String hasta) {
    Long n = jdbc.queryForObject("SELECT COUNT(*) FROM reservations WHERE llegada >= ? AND llegada < ?",
      Long.class, desde, hasta);
    return n == null ? 0L : n;
  }

  public long reservasCanceladas(String desde, String hasta) {
    Long n = jdbc.queryForObject("SELECT COUNT(*) FROM reservations WHERE estado='CANCELADA' "
      + "AND llegada >= ? AND llegada < ?", Long.class, desde, hasta);
    return n == null ? 0L : n;
  }

  public Map<String, Long> reservasPorOrigen(String desde, String hasta) {
    Map<String, Long> conteo = new java.util.LinkedHashMap<>();
    jdbc.query("SELECT origen, COUNT(*) AS n FROM reservations WHERE llegada >= ? AND llegada < ? "
        + "GROUP BY origen", rs -> { conteo.put(rs.getString("origen"), rs.getLong("n")); },
      desde, hasta);
    return conteo;
  }

  public long sincronizacionesIntentadas(String desde, String hasta) {
    Long n = jdbc.queryForObject("SELECT COUNT(*) FROM ota_syncs WHERE substr(en,1,10) >= ? AND substr(en,1,10) < ?",
      Long.class, desde, hasta);
    return n == null ? 0L : n;
  }

  public long sincronizacionesExitosas(String desde, String hasta) {
    Long n = jdbc.queryForObject("SELECT COUNT(*) FROM ota_syncs WHERE exitosa=1 "
      + "AND substr(en,1,10) >= ? AND substr(en,1,10) < ?", Long.class, desde, hasta);
    return n == null ? 0L : n;
  }

  public long actividadesDeTipo(String tipo, String desde, String hasta) {
    Long n = jdbc.queryForObject("SELECT COUNT(*) FROM adoption_activities WHERE tipo=? "
      + "AND fecha >= ? AND fecha < ?", Long.class, tipo, desde, hasta);
    return n == null ? 0L : n;
  }

  public List<Actividad> actividades(String desde, String hasta) {
    return jdbc.query("SELECT tipo,descripcion,fecha,participantes,confirmada_por,con_datos_de_origen "
        + "FROM adoption_activities WHERE fecha >= ? AND fecha < ? ORDER BY fecha",
      (rs, n) -> new Actividad(rs.getString("tipo"), rs.getString("descripcion"),
        java.time.LocalDate.parse(rs.getString("fecha")), (Integer) rs.getObject("participantes"),
        rs.getString("confirmada_por"), rs.getInt("con_datos_de_origen") == 1),
      desde, hasta);
  }

  public void registrarActividad(String tipo, String descripcion, String fecha, Integer participantes,
                                 String confirmadaPor, boolean conDatosDeOrigen) {
    jdbc.update("INSERT INTO adoption_activities(tipo,descripcion,fecha,participantes,confirmada_por,"
      + "con_datos_de_origen) VALUES(?,?,?,?,?,?)",
      tipo, descripcion, fecha, participantes, confirmadaPor, conDatosDeOrigen ? 1 : 0);
  }

  /** El hotel declara cuántas habitaciones debía cargar; sin ese dato no hay porcentaje. */
  public Integer inventarioEsperado() {
    List<Integer> valores = jdbc.queryForList("SELECT valor FROM hotel_config WHERE clave='inventario_esperado'",
      Integer.class);
    return valores.isEmpty() ? null : valores.get(0);
  }

  public void fijarInventarioEsperado(int cantidad) {
    jdbc.update("INSERT INTO hotel_config(clave,valor,actualizado_en) VALUES('inventario_esperado',?,datetime('now')) "
      + "ON CONFLICT(clave) DO UPDATE SET valor=excluded.valor, actualizado_en=datetime('now')",
      String.valueOf(cantidad));
  }
}