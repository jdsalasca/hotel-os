package co.hotel.reservas;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Acceso SQL a reservas, líneas de reserva e inventario ocupado. Sin JPA, SQL explícito. */
@Repository
public class ReservaRepository {
  private static final String CAMPOS =
      "codigo,email,nombre,llegada,salida,huespedes,estado,origen,creado_en";

  private final JdbcTemplate jdbc;

  public ReservaRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public Optional<String> codigoPorClave(String clave) {
    try {
      return Optional.ofNullable(jdbc.queryForObject(
        "SELECT codigo FROM reservations WHERE idempotencia=?", String.class, clave));
    } catch (EmptyResultDataAccessException e) {
      return Optional.empty();
    }
  }

  public long insertar(String codigo, CrearReserva datos, String claveIdempotencia) {
    jdbc.update("INSERT INTO reservations(codigo,email,nombre,llegada,salida,huespedes,estado,origen,"
        + "idempotencia,creado_en) VALUES(?,?,?,?,?,?,?,?,?,datetime('now'))",
      codigo, datos.email(), datos.nombre(), datos.llegada().toString(), datos.salida().toString(),
      datos.huespedes(), EstadoReserva.PENDIENTE.name(), datos.origen().name(), claveIdempotencia);
    return jdbc.queryForObject("SELECT id FROM reservations WHERE codigo=?", Long.class, codigo);
  }

  public void insertarLinea(long reservaId, long roomId, LocalDate desde, LocalDate hasta) {
    jdbc.update("INSERT INTO reservation_items(reservation_id,room_id,desde,hasta) VALUES(?,?,?,?)",
      reservaId, roomId, desde.toString(), hasta.toString());
  }

  /** Solape en el intervalo semiabierto:ocupa si existing.desde < nueva.hasta y existing.hasta > nueva.desde. */
  public boolean hayReservaSolapada(long roomId, LocalDate desde, LocalDate hasta) {
    Integer n = jdbc.queryForObject(
      "SELECT COUNT(*) FROM reservation_items ri JOIN reservations r ON r.id = ri.reservation_id "
        + "WHERE ri.room_id = ? AND r.estado IN ('PENDIENTE','CONFIRMADA') "
        + "AND ri.desde < ? AND ? < ri.hasta",
      Integer.class, roomId, hasta.toString(), desde.toString());
    return n != null && n > 0;
  }

  public boolean hayBloqueoSolapado(long roomId, LocalDate desde, LocalDate hasta) {
    Integer n = jdbc.queryForObject(
      "SELECT COUNT(*) FROM blocks WHERE (room_id = ? OR room_id IS NULL) "
        + "AND desde < ? AND ? < hasta",
      Integer.class, roomId, hasta.toString(), desde.toString());
    return n != null && n > 0;
  }

  /** Habitaciones activas sin reserva vigente ni bloqueo en el intervalo semiabierto. */
  public List<Long> habitacionesLibres(LocalDate desde, LocalDate hasta) {
    return jdbc.queryForList(
      "SELECT r.id FROM rooms r WHERE r.estado = 'ACTIVA' "
        + "AND NOT EXISTS (SELECT 1 FROM reservation_items ri JOIN reservations res ON res.id = ri.reservation_id "
        + "  WHERE ri.room_id = r.id AND res.estado IN ('PENDIENTE','CONFIRMADA') "
        + "  AND ri.desde < ? AND ? < ri.hasta) "
        + "AND NOT EXISTS (SELECT 1 FROM blocks b WHERE (b.room_id = r.id OR b.room_id IS NULL) "
        + "  AND b.desde < ? AND ? < b.hasta) "
        + "ORDER BY r.codigo",
      Long.class, hasta.toString(), desde.toString(), hasta.toString(), desde.toString());
  }

  public Optional<Reserva> porCodigo(String codigo) {
    try {
      return Optional.ofNullable(jdbc.query(
        "SELECT " + CAMPOS + " FROM reservations WHERE codigo=?", (rs, n) -> mapear(rs), codigo).stream().findFirst().orElse(null));
    } catch (EmptyResultDataAccessException e) {
      return Optional.empty();
    }
  }

  public Optional<Long> idPorCodigo(String codigo) {
    try {
      return Optional.ofNullable(jdbc.queryForObject("SELECT id FROM reservations WHERE codigo=?", Long.class, codigo));
    } catch (EmptyResultDataAccessException e) {
      return Optional.empty();
    }
  }

  public List<Reserva> listar(int limite) {
    return jdbc.query("SELECT " + CAMPOS + " FROM reservations ORDER BY id DESC LIMIT ?",
      (rs, numFila) -> mapear(rs), Math.min(limite, 200));
  }

  public void actualizarEstado(String codigo, EstadoReserva estado) {
    jdbc.update("UPDATE reservations SET estado=? WHERE codigo=?", estado.name(), codigo);
  }

  private Reserva mapear(ResultSet rs) throws SQLException {
    return new Reserva(
      rs.getString("codigo"),
      rs.getString("email"),
      rs.getString("nombre"),
      LocalDate.parse(rs.getString("llegada")),
      LocalDate.parse(rs.getString("salida")),
      rs.getInt("huespedes"),
      EstadoReserva.valueOf(rs.getString("estado")),
      Origen.valueOf(rs.getString("origen")),
      rs.getString("creado_en"));
  }
}