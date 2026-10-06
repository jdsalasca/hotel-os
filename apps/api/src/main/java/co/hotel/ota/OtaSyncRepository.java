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

  /** Mapeos configurados por canal. */
  public List<Map<String, Object>> mapeosDe(String canal) {
    return jdbc.queryForList("SELECT room_id, room_type_id, rate_plan_id, external_id "
      + "FROM channel_mappings WHERE channel_codigo = ? ORDER BY id", canal);
  }
}