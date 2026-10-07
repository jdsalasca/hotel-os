package co.hotel.inventario;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** SQL de inventario y bloqueos. Explícito, sin JPA. */
@Repository
public class InventarioRepository {
  private final JdbcTemplate jdbc;

  public InventarioRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public long insertarTipo(String codigo, String nombre, int capacidadMax) {
    jdbc.update("INSERT INTO room_types(codigo,nombre,capacidad_max) VALUES(?,?,?)", codigo, nombre, capacidadMax);
    return jdbc.queryForObject("SELECT id FROM room_types WHERE codigo=?", Long.class, codigo);
  }

  public long insertarHabitacion(String codigo, long roomTypeId, String nombre) {
    jdbc.update("INSERT INTO rooms(codigo,room_type_id,nombre,estado) VALUES(?,?,?,'ACTIVA')", codigo, roomTypeId, nombre);
    return jdbc.queryForObject("SELECT id FROM rooms WHERE codigo=?", Long.class, codigo);
  }

  public Optional<Long> idPorCodigoHabitacion(String codigo) {
    return Optional.ofNullable(jdbc.query("SELECT id FROM rooms WHERE codigo=?",
      (rs, n) -> rs.getLong(1), codigo).stream().findFirst().orElse(null));
  }

  public void actualizarEstado(long habitacionId, EstadoHabitacion estado) {
    jdbc.update("UPDATE rooms SET estado=? WHERE id=?", estado.name(), habitacionId);
  }

  public long insertarBloqueo(Long habitacionId, LocalDate desde, LocalDate hasta, String motivo) {
    if (habitacionId == null) {
      jdbc.update("INSERT INTO blocks(room_id,desde,hasta,motivo) VALUES(NULL,?,?,?)",
        desde.toString(), hasta.toString(), motivo);
    } else {
      jdbc.update("INSERT INTO blocks(room_id,desde,hasta,motivo) VALUES(?,?,?,?)",
        habitacionId, desde.toString(), hasta.toString(), motivo);
    }
    return jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
  }

  public List<Habitacion> habitaciones() {
    return jdbc.query("SELECT id,codigo,room_type_id,nombre,estado FROM rooms ORDER BY codigo",
      (rs, n) -> new Habitacion(rs.getLong("id"), rs.getString("codigo"), rs.getLong("room_type_id"),
        rs.getString("nombre"), EstadoHabitacion.valueOf(rs.getString("estado"))));
  }

  public List<RoomType> tipos() {
    return jdbc.query("SELECT id,codigo,nombre,capacidad_max FROM room_types ORDER BY codigo",
      (rs, n) -> new RoomType(rs.getLong("id"), rs.getString("codigo"), rs.getString("nombre"),
        rs.getInt("capacidad_max")));
  }

  public Optional<RoomType> tipoPorId(long id) {
    return jdbc.query("SELECT id,codigo,nombre,capacidad_max FROM room_types WHERE id=?",
      (rs, n) -> new RoomType(rs.getLong("id"), rs.getString("codigo"), rs.getString("nombre"),
        rs.getInt("capacidad_max")), id).stream().findFirst();
  }

  public Optional<Habitacion> habitacionPorId(long id) {
    return jdbc.query("SELECT id,codigo,room_type_id,nombre,estado FROM rooms WHERE id=?",
      (rs, n) -> new Habitacion(rs.getLong("id"), rs.getString("codigo"), rs.getLong("room_type_id"),
        rs.getString("nombre"), EstadoHabitacion.valueOf(rs.getString("estado"))), id).stream().findFirst();
  }

  /** Un ítem de reserva vigente que se solapa con el intervalo pedido. */
  public record OcupacionReserva(long roomId, LocalDate desde, LocalDate hasta, String codigo) {}

  /**
   * Reservas vigentes (no canceladas ni rechazadas) que se solapan con el intervalo. No se expande
   * la fecha aquí: el servicio recorre las noches, porque SQLite no genera días.
   */
  public List<OcupacionReserva> ocupacionesDeReservas(LocalDate desde, LocalDate hasta) {
    return jdbc.query(
      "SELECT ri.room_id, ri.desde, ri.hasta, res.codigo FROM reservation_items ri "
        + "JOIN reservations res ON res.id = ri.reservation_id "
        + "WHERE res.estado IN ('PENDIENTE','CONFIRMADA') "
        + "  AND ri.desde < ? AND ? < ri.hasta "
        + "ORDER BY ri.room_id, ri.desde",
      (rs, n) -> new OcupacionReserva(rs.getLong("room_id"), LocalDate.parse(rs.getString("desde")),
        LocalDate.parse(rs.getString("hasta")), rs.getString("codigo")),
      hasta.toString(), desde.toString());
  }

  /** Un bloqueo. `roomId` nulo es un bloqueo del hotel entero. */
  public record OcupacionBloqueo(Long roomId, LocalDate desde, LocalDate hasta, String motivo) {}

  /** Un bloqueo con su id y el código de habitación, para gestionarlo desde el panel. */
  public record BloqueoVista(long id, Long roomId, String habitacion, LocalDate desde, LocalDate hasta,
                             String motivo) {}

  public boolean existeBloqueo(long id) {
    Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM blocks WHERE id=?", Integer.class, id);
    return n != null && n > 0;
  }

  public void eliminarBloqueo(long id) {
    jdbc.update("DELETE FROM blocks WHERE id=?", id);
  }

  /**
   * Bloqueos que aún cubren alguna noche futura, los más próximos primero. Los ya terminados no
   * salen: el panel es para gestionar, no para archivar.
   */
  public List<BloqueoVista> bloqueosVigentes() {
    return jdbc.query(
      "SELECT b.id, b.room_id, r.codigo AS habitacion, b.desde, b.hasta, b.motivo FROM blocks b "
        + "LEFT JOIN rooms r ON r.id = b.room_id "
        + "WHERE date('now') < b.hasta ORDER BY b.desde, b.id",
      (rs, n) -> {
        long roomId = rs.getLong("room_id");
        return new BloqueoVista(rs.getLong("id"), rs.wasNull() ? null : roomId,
          rs.getString("habitacion"), LocalDate.parse(rs.getString("desde")),
          LocalDate.parse(rs.getString("hasta")), rs.getString("motivo"));
      });
  }

  public List<OcupacionBloqueo> bloqueosDe(LocalDate desde, LocalDate hasta) {
    return jdbc.query(
      "SELECT room_id, desde, hasta, motivo FROM blocks "
        + "WHERE desde < ? AND ? < hasta ORDER BY desde",
      (rs, n) -> {
        long id = rs.getLong("room_id");
        return new OcupacionBloqueo(rs.wasNull() ? null : id, LocalDate.parse(rs.getString("desde")),
          LocalDate.parse(rs.getString("hasta")), rs.getString("motivo"));
      },
      hasta.toString(), desde.toString());
  }

  /** Habitaciones activas sin reserva vigente ni bloqueo en el intervalo semiabierto. */
  public List<Habitacion> disponibles(LocalDate desde, LocalDate hasta) {
    return jdbc.query(
      "SELECT r.id, r.codigo, r.room_type_id, r.nombre, r.estado FROM rooms r "
        + "WHERE r.estado = 'ACTIVA' "
        + "AND NOT EXISTS (SELECT 1 FROM reservation_items ri JOIN reservations res ON res.id = ri.reservation_id "
        + "  WHERE ri.room_id = r.id AND res.estado IN ('PENDIENTE','CONFIRMADA') "
        + "  AND ri.desde < ? AND ? < ri.hasta) "
        + "AND NOT EXISTS (SELECT 1 FROM blocks b WHERE (b.room_id = r.id OR b.room_id IS NULL) "
        + "  AND b.desde < ? AND ? < b.hasta) "
        + "ORDER BY r.codigo",
      (rs, n) -> new Habitacion(rs.getLong("id"), rs.getString("codigo"), rs.getLong("room_type_id"),
        rs.getString("nombre"), EstadoHabitacion.valueOf(rs.getString("estado"))),
      hasta.toString(), desde.toString(), hasta.toString(), desde.toString());
  }
}