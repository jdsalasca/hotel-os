package co.hotel.ota;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Bitácora de sincronizaciones con los proveedores. El detalle se sanea antes de guardarse. */
@Repository
public class OtaSyncRepository {
  private final JdbcTemplate jdbc;

  public OtaSyncRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public void registrar(String canal, String operacion, boolean exitosa, String detalle) {
    jdbc.update("INSERT INTO ota_syncs(channel_codigo,operacion,exitosa,detalle,en) VALUES(?,?,?,?,?)",
      canal, operacion, exitosa ? 1 : 0, Bitacora.sanear(detalle), LocalDateTime.now().toString());
  }

  public List<Map<String, Object>> ultimasPorCanal(String canal, int limite) {
    return jdbc.queryForList("SELECT canal, operacion, exitosa, detalle, en FROM ("
        + "SELECT channel_codigo AS canal, operacion, exitosa, detalle, en FROM ota_syncs "
        + "WHERE channel_codigo = ? ORDER BY id DESC LIMIT ?)",
      canal, Math.min(limite, 100));
  }

  /** Bitácoras de todos los canales, para la pantalla de integraciones. */
  public Map<String, List<Map<String, Object>>> ultimasPorTodosLosCanales(int limite) {
    Map<String, List<Map<String, Object>>> porCanal = new java.util.LinkedHashMap<>();
    for (Canal c : List.of(Canal.BOOKING, Canal.DESPEGAR, Canal.AIRBNB))
      porCanal.put(c.name(), ultimasPorCanal(c.name(), limite));
    return porCanal;
  }

  /** Mapeo entre inventario local y la unidad que reconoce un canal. */
  public record Mapeo(long id, String canal, Long roomId, String roomCodigo, String roomNombre,
                      Long roomTypeId, String tipoNombre, Long ratePlanId, String planNombre,
                      String planMoneda, String externalId) {}

  /** SQLite devuelve NULL como 0 con `getLong`; hay que preguntar si era nulo. */
  private static Long idONulo(java.sql.ResultSet rs, String columna) throws java.sql.SQLException {
    long valor = rs.getLong(columna);
    return rs.wasNull() ? null : valor;
  }

  /** Mapeos configurados por canal, con los nombres locales para que el panel no muestre solo IDs. */
  public List<Mapeo> mapeosDe(String canal) {
    return jdbc.query("SELECT m.id, m.channel_codigo, m.room_id, r.codigo AS room_codigo, "
        + "r.nombre AS room_nombre, m.room_type_id, t.nombre AS tipo_nombre, m.rate_plan_id, "
        + "p.nombre AS plan_nombre, p.moneda AS plan_moneda, m.external_id "
        + "FROM channel_mappings m "
        + "LEFT JOIN rooms r ON r.id = m.room_id "
        + "LEFT JOIN room_types t ON t.id = m.room_type_id "
        + "LEFT JOIN rate_plans p ON p.id = m.rate_plan_id "
        + "WHERE m.channel_codigo = ? ORDER BY m.id",
      (rs, n) -> new Mapeo(rs.getLong("id"), rs.getString("channel_codigo"),
        idONulo(rs, "room_id"), rs.getString("room_codigo"), rs.getString("room_nombre"),
        idONulo(rs, "room_type_id"), rs.getString("tipo_nombre"),
        idONulo(rs, "rate_plan_id"), rs.getString("plan_nombre"),
        rs.getString("plan_moneda"), rs.getString("external_id")), canal);
  }

  public Mapeo mapeoPorId(long id) {
    return jdbc.query("SELECT m.id, m.channel_codigo, m.room_id, r.codigo AS room_codigo, "
        + "r.nombre AS room_nombre, m.room_type_id, t.nombre AS tipo_nombre, m.rate_plan_id, "
        + "p.nombre AS plan_nombre, p.moneda AS plan_moneda, m.external_id "
        + "FROM channel_mappings m "
        + "LEFT JOIN rooms r ON r.id = m.room_id "
        + "LEFT JOIN room_types t ON t.id = m.room_type_id "
        + "LEFT JOIN rate_plans p ON p.id = m.rate_plan_id "
        + "WHERE m.id = ?",
      (rs, n) -> new Mapeo(rs.getLong("id"), rs.getString("channel_codigo"),
        idONulo(rs, "room_id"), rs.getString("room_codigo"), rs.getString("room_nombre"),
        idONulo(rs, "room_type_id"), rs.getString("tipo_nombre"),
        idONulo(rs, "rate_plan_id"), rs.getString("plan_nombre"),
        rs.getString("plan_moneda"), rs.getString("external_id")), id).stream().findFirst().orElse(null);
  }

  public boolean existeMapeo(String canal, String externalId) {
    Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM channel_mappings "
      + "WHERE channel_codigo = ? AND external_id = ?", Integer.class, canal, externalId);
    return n != null && n > 0;
  }

  public long insertarMapeo(String canal, Long roomId, Long roomTypeId, Long ratePlanId, String externalId) {
    jdbc.update("INSERT INTO channel_mappings(channel_codigo,room_id,room_type_id,rate_plan_id,external_id) "
      + "VALUES(?,?,?,?,?)", canal, roomId, roomTypeId, ratePlanId, externalId);
    return jdbc.queryForObject("SELECT id FROM channel_mappings WHERE channel_codigo = ? AND external_id = ?",
      Long.class, canal, externalId);
  }

  public int eliminarMapeo(long id) {
    return jdbc.update("DELETE FROM channel_mappings WHERE id = ?", id);
  }
}