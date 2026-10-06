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