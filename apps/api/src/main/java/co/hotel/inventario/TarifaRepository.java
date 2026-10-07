package co.hotel.inventario;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** SQL de planes tarifarios y precios por fecha. */
@Repository
public class TarifaRepository {
  private final JdbcTemplate jdbc;

  public TarifaRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public long insertarPlan(String codigo, String nombre, String moneda, int descuentoPct) {
    jdbc.update("INSERT INTO rate_plans(codigo,nombre,moneda,activo,descuento_pct) VALUES(?,?,?,1,?)",
      codigo, nombre, moneda, descuentoPct);
    return jdbc.queryForObject("SELECT id FROM rate_plans WHERE codigo=?", Long.class, codigo);
  }

  public void fijarDescuento(long planId, int descuentoPct) {
    jdbc.update("UPDATE rate_plans SET descuento_pct=? WHERE id=?", descuentoPct, planId);
  }

  public Optional<PlanTarifario> planPorId(long id) {
    return jdbc.query("SELECT id,codigo,nombre,moneda,activo,descuento_pct FROM rate_plans WHERE id=?",
      (rs, n) -> new PlanTarifario(rs.getLong("id"), rs.getString("codigo"), rs.getString("nombre"),
        rs.getString("moneda"), rs.getInt("activo") == 1, rs.getInt("descuento_pct")), id).stream().findFirst();
  }

  public void fijarPrecio(long planId, long tipoId, LocalDate fecha, long precioCents) {
    jdbc.update("INSERT INTO rates(rate_plan_id,room_type_id,fecha,precio_cents,cerrado) VALUES(?,?,?,?,0) "
        + "ON CONFLICT(rate_plan_id,room_type_id,fecha) DO UPDATE SET precio_cents=excluded.precio_cents, cerrado=0",
      planId, tipoId, fecha.toString(), precioCents);
  }

  public void fijarMinimoEstancia(long planId, long tipoId, LocalDate fecha, int minimo) {
    jdbc.update("UPDATE rates SET min_estancia=? WHERE rate_plan_id=? AND room_type_id=? AND fecha=?",
      minimo, planId, tipoId, fecha.toString());
  }

  public void fijarMaximoEstancia(long planId, long tipoId, LocalDate fecha, int maximo) {
    jdbc.update("UPDATE rates SET max_estancia=? WHERE rate_plan_id=? AND room_type_id=? AND fecha=?",
      maximo, planId, tipoId, fecha.toString());
  }

  public void cerrarNoche(long planId, long tipoId, LocalDate fecha) {
    jdbc.update("UPDATE rates SET cerrado=1 WHERE rate_plan_id=? AND room_type_id=? AND fecha=?",
      planId, tipoId, fecha.toString());
  }

  public void abrirNoche(long planId, long tipoId, LocalDate fecha) {
    jdbc.update("UPDATE rates SET cerrado=0 WHERE rate_plan_id=? AND room_type_id=? AND fecha=?",
      planId, tipoId, fecha.toString());
  }

  /** Planes con los que el hotel ofrece inventario, en orden de preferencia. */
  public List<PlanTarifario> planesActivos() {
    return jdbc.query("SELECT id,codigo,nombre,moneda,activo,descuento_pct FROM rate_plans WHERE activo=1 ORDER BY id",
      (rs, n) -> new PlanTarifario(rs.getLong("id"), rs.getString("codigo"), rs.getString("nombre"),
        rs.getString("moneda"), rs.getInt("activo") == 1, rs.getInt("descuento_pct")));
  }

  /**
   * Precios por noche del tipo en el intervalo. Faltan las noches sin fila: quien llama debe
   * decidir qué hacer con ellas (acá, no ofrecer la habitación).
   */
  public List<TarifaNoche> nochesDelPeriodo(long planId, long tipoId, LocalDate desde, LocalDate hasta) {
    return jdbc.query("SELECT fecha,precio_cents,min_estancia,max_estancia,cerrado FROM rates "
        + "WHERE rate_plan_id=? AND room_type_id=? AND fecha >= ? AND fecha < ? ORDER BY fecha",
      (rs, n) -> new TarifaNoche(LocalDate.parse(rs.getString("fecha")), rs.getLong("precio_cents"),
        (Integer) rs.getObject("min_estancia"), (Integer) rs.getObject("max_estancia"),
        rs.getInt("cerrado") == 1),
      planId, tipoId, desde.toString(), hasta.toString());
  }

  /** Una noche con su precio y sus restricciones. */
  public record TarifaNoche(LocalDate fecha, long precioCents, Integer minEstancia, Integer maxEstancia,
                             boolean cerrado) {}
}