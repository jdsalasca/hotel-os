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
      "codigo,email,nombre,llegada,salida,huespedes,estado,origen,creado_en,total_cents,moneda,rate_plan_id";

  private final JdbcTemplate jdbc;

  public ReservaRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  /**
   * La reserva creada con una clave de idempotencia, si la pidió el mismo huésped.
   *
   * El correo va en la búsqueda a propósito. Buscar solo por la clave devolvía la reserva de
   * quien la hubiera usado antes, con su nombre, correo y total, a cualquiera que shares la clave.
   * La clave identifica un intento de reserva, no a una persona, así que la petición de otro
   * es una reserva nueva.
   *
   * `lower()` porque el correo se compara sin distinguir mayúsculas en toda la aplicación.
   */
  public Optional<String> codigoPorClave(String clave, String email) {
    try {
      return Optional.ofNullable(jdbc.queryForObject(
        "SELECT codigo FROM reservations WHERE idempotencia=? AND lower(email)=lower(?)",
        String.class, clave, email));
    } catch (EmptyResultDataAccessException e) {
      return Optional.empty();
    }
  }

  public long insertar(String codigo, CrearReserva datos, String claveIdempotencia,
      Long totalCents, String moneda, Long ratePlanId) {
    jdbc.update("INSERT INTO reservations(codigo,email,nombre,llegada,salida,huespedes,estado,origen,"
        + "idempotencia,creado_en,total_cents,moneda,rate_plan_id) VALUES(?,?,?,?,?,?,?,?,?,datetime('now'),?,?,?)",
      codigo, datos.email(), datos.nombre(), datos.llegada().toString(), datos.salida().toString(),
      datos.huespedes(), EstadoReserva.PENDIENTE.name(), datos.origen().name(), claveIdempotencia,
      totalCents, moneda, ratePlanId);
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

  /** Lo mismo pero sin contar la propia reserva: al mover fechas, ella siempre se solapa consigo. */
  public boolean hayReservaSolapadaExcepto(long roomId, LocalDate desde, LocalDate hasta, long reservaId) {
    Integer n = jdbc.queryForObject(
      "SELECT COUNT(*) FROM reservation_items ri JOIN reservations r ON r.id = ri.reservation_id "
        + "WHERE ri.room_id = ? AND ri.reservation_id <> ? AND r.estado IN ('PENDIENTE','CONFIRMADA') "
        + "AND ri.desde < ? AND ? < ri.hasta",
      Integer.class, roomId, reservaId, hasta.toString(), desde.toString());
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

  /** Una reserva que llega o se va un día: quién, a qué habitación y con cuántos. */
  public record Movimiento(String codigo, String email, String nombre, int huespedes, String habitacion) {}

  public List<Movimiento> llegadas(LocalDate fecha) { return movimientos("llegada", fecha); }

  public List<Movimiento> salidas(LocalDate fecha) { return movimientos("salida", fecha); }

  /**
   * Quién duerme esa noche: llegó ese día o antes y se va después. Intervalo semiabierto, como el
   * resto del sistema: el día de salida ya no cuenta.
   */
  public List<Movimiento> enCasa(LocalDate fecha) {
    return jdbc.query(
      "SELECT r.codigo, r.email, r.nombre, r.huespedes, rm.codigo AS habitacion FROM reservations r "
        + "JOIN reservation_items ri ON ri.reservation_id = r.id "
        + "JOIN rooms rm ON rm.id = ri.room_id "
        + "WHERE r.llegada <= ? AND ? < r.salida AND r.estado IN ('PENDIENTE','CONFIRMADA') "
        + "ORDER BY rm.codigo",
      (rs, n) -> new Movimiento(rs.getString("codigo"), rs.getString("email"),
        rs.getString("nombre"), rs.getInt("huespedes"), rs.getString("habitacion")),
      fecha.toString(), fecha.toString());
  }

  private List<Movimiento> movimientos(String columnaFecha, LocalDate fecha) {
    // La columna se elige aquí dentro, nunca llega del exterior: no hay inyección posible.
    // Solo cuentan las vigentes: una cancelada no llega ni se va.
    return jdbc.query(
      "SELECT r.codigo, r.email, r.nombre, r.huespedes, rm.codigo AS habitacion FROM reservations r "
        + "JOIN reservation_items ri ON ri.reservation_id = r.id "
        + "JOIN rooms rm ON rm.id = ri.room_id "
        + "WHERE r." + columnaFecha + " = ? AND r.estado IN ('PENDIENTE','CONFIRMADA') "
        + "ORDER BY rm.codigo",
      (rs, n) -> new Movimiento(rs.getString("codigo"), rs.getString("email"),
        rs.getString("nombre"), rs.getInt("huespedes"), rs.getString("habitacion")),
      fecha.toString());
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

  /** Lo que la reserva pidió: habitación, fechas y huéspedes. Para comparar reintentos. */
  public record ContenidoReserva(long roomId, LocalDate llegada, LocalDate salida, int huespedes) {}

  public Optional<ContenidoReserva> contenidoPorCodigo(String codigo) {
    var filas = jdbc.query(
      "SELECT ri.room_id, r.llegada, r.salida, r.huespedes FROM reservations r "
        + "JOIN reservation_items ri ON ri.reservation_id = r.id WHERE r.codigo = ?",
      (rs, n) -> new ContenidoReserva(rs.getLong("room_id"),
        LocalDate.parse(rs.getString("llegada")), LocalDate.parse(rs.getString("salida")),
        rs.getInt("huespedes")),
      codigo);
    return filas.stream().findFirst();
  }

  /** Habitación asignada a la reserva, con su tipo. Una reserva siempre tiene una sola línea. */
  public record HabitacionReserva(long roomId, String codigo, String nombre, String tipoNombre) {}

  public Optional<HabitacionReserva> habitacionDe(long reservaId) {
    return jdbc.query("SELECT r.id, r.codigo, r.nombre, t.nombre AS tipo FROM reservation_items ri "
        + "JOIN rooms r ON r.id = ri.room_id LEFT JOIN room_types t ON t.id = r.room_type_id "
        + "WHERE ri.reservation_id = ?",
      (rs, n) -> new HabitacionReserva(rs.getLong("id"), rs.getString("codigo"),
        rs.getString("nombre"), rs.getString("tipo")), reservaId).stream().findFirst();
  }

  public List<Reserva> listar(int limite) {
    return jdbc.query("SELECT " + CAMPOS + " FROM reservations ORDER BY id DESC LIMIT ?",
      (rs, numFila) -> mapear(rs), Math.min(limite, 200));
  }

  /**
   * Listado con búsqueda y filtro de estado para la recepción. El texto busca en código, correo
   * y nombre sin distinguir mayúsculas; los comodines que escriba el hotel se buscan literales,
   * no como patrones.
   */
  public List<Reserva> listarFiltrado(String texto, EstadoReserva estado, int limite) {
    String patron = texto == null || texto.isBlank() ? null
      : "%" + texto.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    return jdbc.query("SELECT " + CAMPOS + " FROM reservations "
        + "WHERE (? IS NULL OR codigo LIKE ? ESCAPE '\\' OR lower(email) LIKE lower(?) ESCAPE '\\' "
        + "OR lower(nombre) LIKE lower(?) ESCAPE '\\') "
        + "AND (? IS NULL OR estado = ?) ORDER BY id DESC LIMIT ?",
      (rs, numFila) -> mapear(rs), patron, patron, patron, patron,
      estado == null ? null : estado.name(), estado == null ? null : estado.name(),
      Math.min(limite, 200));
  }

  public void actualizarEstado(String codigo, EstadoReserva estado) {
    jdbc.update("UPDATE reservations SET estado=? WHERE codigo=?", estado.name(), codigo);
  }

  /** Habitación asignada hoy a la reserva. */
  public Optional<Long> roomIdDe(long reservaId) {
    try {
      return Optional.ofNullable(jdbc.queryForObject(
        "SELECT room_id FROM reservation_items WHERE reservation_id=?", Long.class, reservaId));
    } catch (EmptyResultDataAccessException e) {
      return Optional.empty();
    }
  }

  public Optional<String> codigoHabitacion(long roomId) {
    try {
      return Optional.ofNullable(jdbc.queryForObject("SELECT codigo FROM rooms WHERE id=?",
        String.class, roomId));
    } catch (EmptyResultDataAccessException e) {
      return Optional.empty();
    }
  }

  /** Mueve la línea de la reserva a otra habitación, con sus mismas fechas. */
  public void reasignarHabitacion(long reservaId, long roomId, LocalDate llegada, LocalDate salida) {
    jdbc.update("UPDATE reservation_items SET room_id=?, desde=?, hasta=? WHERE reservation_id=?",
      roomId, llegada.toString(), salida.toString(), reservaId);
  }

  /** Mueve las fechas de la reserva, en cabecera y en línea. */
  public void actualizarFechas(long reservaId, String codigo, LocalDate llegada, LocalDate salida) {
    jdbc.update("UPDATE reservations SET llegada=?, salida=? WHERE id=?",
      llegada.toString(), salida.toString(), reservaId);
    jdbc.update("UPDATE reservation_items SET desde=?, hasta=? WHERE reservation_id=?",
      llegada.toString(), salida.toString(), reservaId);
  }

  /** Cambia el número de huéspedes de la reserva (solo el contador de cabecera). */
  public void actualizarHuespedes(long reservaId, int huespedes) {
    jdbc.update("UPDATE reservations SET huespedes=? WHERE id=?", huespedes, reservaId);
  }

  /** El precio se recalcula con la habitación nueva: la reserva no hereda importes ajenos. */
  public void actualizarPrecio(String codigo, Long totalCents, String moneda, Long ratePlanId) {
    jdbc.update("UPDATE reservations SET total_cents=?, moneda=?, rate_plan_id=? WHERE codigo=?",
      totalCents, moneda, ratePlanId, codigo);
  }

  private Reserva mapear(ResultSet rs) throws SQLException {
    // SQLite no convierte NULL con getObject(columna, Long.class): hay que leer y preguntar
    // enseguida, porque wasNull() siempre habla de la última lectura.
    long total = rs.getLong("total_cents");
    boolean sinTotal = rs.wasNull();
    long plan = rs.getLong("rate_plan_id");
    boolean sinPlan = rs.wasNull();
    return new Reserva(
      rs.getString("codigo"),
      rs.getString("email"),
      rs.getString("nombre"),
      LocalDate.parse(rs.getString("llegada")),
      LocalDate.parse(rs.getString("salida")),
      rs.getInt("huespedes"),
      EstadoReserva.valueOf(rs.getString("estado")),
      Origen.valueOf(rs.getString("origen")),
      rs.getString("creado_en"),
      sinTotal ? null : total,
      rs.getString("moneda"),
      sinPlan ? null : plan);
  }
}