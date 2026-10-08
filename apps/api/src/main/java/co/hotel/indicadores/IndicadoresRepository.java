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

  public java.util.Optional<DefinicionIndicador> definicionPorClave(String clave) {
    return jdbc.query("SELECT clave,fase,nombre,definicion,formula,fuente,unidad,periodo_por_defecto,"
        + "linea_base,meta,responsable FROM indicator_definitions WHERE clave=?",
      (rs, n) -> new DefinicionIndicador(rs.getString("clave"), rs.getInt("fase"), rs.getString("nombre"),
        rs.getString("definicion"), rs.getString("formula"), rs.getString("fuente"), rs.getString("unidad"),
        rs.getString("periodo_por_defecto"), rs.getString("linea_base"), rs.getString("meta"),
        rs.getString("responsable")), clave).stream().findFirst();
  }

  /**
   * Fija línea base, meta y responsable de un indicador. Devuelve cuántas filas tocó:
   * cero es clave inexistente, no un éxito silencioso.
   */
  public int fijarReferencia(String clave, String lineaBase, String meta, String responsable) {
    return jdbc.update("UPDATE indicator_definitions SET linea_base=?, meta=?, responsable=? WHERE clave=?",
      lineaBase, meta, responsable, clave);
  }

  /** Habitaciones activas cargadas. */
  public long habitacionesActivas() {
    return jdbc.queryForObject("SELECT COUNT(*) FROM rooms WHERE estado='ACTIVA'", Long.class);
  }

  /**
   * Noches de habitación ocupadas en el periodo: solo la intersección de cada estancia con el
   * periodo. Sumar la estancia entera hinchaba el mes con noches de fuera: una reserva de 9 noches
   * con 2 en noviembre aportaba 9 a noviembre.
   */
  public long nochesOcupadas(String desde, String hasta) {
    Long n = jdbc.queryForObject("SELECT COALESCE(CAST(SUM(julianday(MIN(ri.hasta, ?)) "
        + "- julianday(MAX(ri.desde, ?))) AS INTEGER), 0) "
        + "FROM reservation_items ri JOIN reservations r ON r.id = ri.reservation_id "
        + "WHERE r.estado IN ('PENDIENTE','CONFIRMADA') AND ri.desde < ? AND ? < ri.hasta",
      Long.class, hasta, desde, hasta, desde);
    return n == null ? 0L : n;
  }

  /**
   * Noches bloqueadas en el periodo, en noches de habitación: la unión por (habitación, noche),
   * no la suma de intervalos. Dos bloqueos que cubren la misma noche de la misma habitación
   * (duplicados, solapados, o global + individual) restan una sola vez: sumar intervalos
   * encogía el denominador y la ocupación salía inflada.
   *
   * Un bloqueo de todo el hotel (`room_id` nulo) aporta su intersección a cada habitación
   * activa. Solo cuentan bloqueos de habitaciones activas: lo retirado ya salió del inventario
   * por otro lado. La unión se fusiona por intervalos en Java (sin expandir día por día), así
   * que un periodo amplio nunca revienta la memoria.
   */
  public long nochesBloqueadas(String desde, String hasta) {
    java.time.LocalDate inicio = java.time.LocalDate.parse(desde);
    java.time.LocalDate fin = java.time.LocalDate.parse(hasta);
    java.util.List<Long> activas = jdbc.queryForList(
      "SELECT id FROM rooms WHERE estado='ACTIVA'", Long.class);
    if (activas.isEmpty()) return 0L;
    java.util.Set<Long> activasSet = new java.util.HashSet<>(activas);
    java.util.Map<Long, java.util.List<long[]>> porHabitacion = new java.util.HashMap<>();
    for (Long id : activas) porHabitacion.put(id, new java.util.ArrayList<>());
    jdbc.query("SELECT room_id, desde, hasta FROM blocks WHERE desde < ? AND ? < hasta",
      rs -> {
        long crudo = rs.getLong("room_id");
        Long roomId = rs.wasNull() ? null : crudo;
        java.time.LocalDate bDesde = java.time.LocalDate.parse(rs.getString("desde"));
        java.time.LocalDate bHasta = java.time.LocalDate.parse(rs.getString("hasta"));
        java.time.LocalDate s = bDesde.isAfter(inicio) ? bDesde : inicio;
        java.time.LocalDate e = bHasta.isBefore(fin) ? bHasta : fin;
        if (!s.isBefore(e)) return;
        long[] tramo = {s.toEpochDay(), e.toEpochDay()};
        if (roomId == null) {
          for (Long id : activas) porHabitacion.get(id).add(tramo);
        } else if (activasSet.contains(roomId)) {
          porHabitacion.get(roomId).add(tramo);
        }
      }, hasta, desde);
    long total = 0;
    for (java.util.List<long[]> tramos : porHabitacion.values()) {
      if (tramos.isEmpty()) continue;
      tramos.sort(java.util.Comparator.comparingLong(a -> a[0]));
      long s = tramos.get(0)[0];
      long e = tramos.get(0)[1];
      for (int i = 1; i < tramos.size(); i++) {
        long ns = tramos.get(i)[0];
        long ne = tramos.get(i)[1];
        if (ns <= e) {
          if (ne > e) e = ne;
        } else {
          total += e - s;
          s = ns;
          e = ne;
        }
      }
      total += e - s;
    }
    return total;
  }

  /**
   * Noches disponibles para la venta: habitaciones activas por noche del periodo, menos las
   * bloqueadas (en unión, así que nunca supera el total). Un hotel cerrado por mantenimiento
   * no tiene nada vendible, aunque tenga habitaciones dadas de alta. El max(0, …) es solo el
   * invariante defensivo, no el que cuadra las cuentas.
   */
  public long nochesDisponibles(String desde, String hasta) {
    long dias = java.time.temporal.ChronoUnit.DAYS.between(
      java.time.LocalDate.parse(desde), java.time.LocalDate.parse(hasta));
    return Math.max(0, dias * habitacionesActivas() - nochesBloqueadas(desde, hasta));
  }

  /**
   * Reservas creadas en el periodo, por fecha de creación y no de llegada: una reserva creada en
   * octubre para diciembre es de la cohorte de octubre. `creado_en` trae fecha y hora, así que se
   * compara solo la fecha.
   */
  public long reservasCreadas(String desde, String hasta) {
    Long n = jdbc.queryForObject("SELECT COUNT(*) FROM reservations "
        + "WHERE substr(creado_en,1,10) >= ? AND substr(creado_en,1,10) < ?",
      Long.class, desde, hasta);
    return n == null ? 0L : n;
  }

  /**
   * Canceladas de la cohorte creada en el periodo: las que se crearon aquí y hoy están
   * canceladas. Mezclar criterios (creadas por llegada, canceladas por otro) partía la tasa
   * en dos cohortes distintas sin decirlo.
   */
  public long reservasCanceladas(String desde, String hasta) {
    Long n = jdbc.queryForObject("SELECT COUNT(*) FROM reservations WHERE estado='CANCELADA' "
        + "AND substr(creado_en,1,10) >= ? AND substr(creado_en,1,10) < ?", Long.class, desde, hasta);
    return n == null ? 0L : n;
  }

  public Map<String, Long> reservasPorOrigen(String desde, String hasta) {
    Map<String, Long> conteo = new java.util.LinkedHashMap<>();
    jdbc.query("SELECT origen, COUNT(*) AS n FROM reservations "
        + "WHERE substr(creado_en,1,10) >= ? AND substr(creado_en,1,10) < ? "
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

  /** Canales que el hotel tiene configurados (activos): el denominador de conectados. */
  public long canalesConfigurados() {
    Long n = jdbc.queryForObject("SELECT COUNT(*) FROM channels WHERE activo=1", Long.class);
    return n == null ? 0L : n;
  }

  /**
   * Canales configurados con al menos una sincronización exitosa en el periodo: estar
   * configurado sin haber sincronizado nunca no es estar conectado.
   */
  public long canalesConSyncExitosa(String desde, String hasta) {
    Long n = jdbc.queryForObject("SELECT COUNT(DISTINCT s.channel_codigo) FROM ota_syncs s "
      + "JOIN channels c ON c.codigo = s.channel_codigo AND c.activo=1 "
      + "WHERE s.exitosa=1 AND substr(s.en,1,10) >= ? AND substr(s.en,1,10) < ?",
      Long.class, desde, hasta);
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