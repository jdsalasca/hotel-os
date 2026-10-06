package co.hotel.reservas;

import java.sql.*;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/** Inventario centralizado single-writer: valida solape en transacción IMMEDIATE. Sin JPA, SQL explícito. */
public class ReservaService {
  private final String url;
  private static final Set<String> ORIGENES = Set.of("WEB", "BOOKING", "DESPEGAR", "AIRBNB", "OTRO");

  public ReservaService(String url) { this.url = url; }

  private Connection conectar() throws SQLException {
    Connection c = DriverManager.getConnection(url);
    try (Statement s = c.createStatement()) {
      s.execute("PRAGMA journal_mode=WAL");
      s.execute("PRAGMA busy_timeout=5000");
      s.execute("PRAGMA foreign_keys=ON");
    }
    return c;
  }

  /** Crea reserva en estado PENDIENTE (sin pago/confirmación automática configurada). Devuelve código. */
  public String crear(String email, String nombre, String llegada, String salida, int huespedes, String origen, String idempotencia, long roomId) {
    LocalDate ini = LocalDate.parse(llegada);
    LocalDate fin = LocalDate.parse(salida);
    if (!ini.isBefore(fin)) throw new IllegalArgumentException("salida debe ser posterior a llegada");
    if (huespedes < 1) throw new IllegalArgumentException("huespedes inválido");
    if (email == null || !email.contains("@")) throw new IllegalArgumentException("email inválido");
    String org = ORIGENES.contains(origen) ? origen : "OTRO";
    String key = (idempotencia == null || idempotencia.isBlank()) ? UUID.randomUUID().toString() : idempotencia;
    try (Connection c = conectar()) {
      try {
        try (PreparedStatement ps = c.prepareStatement("SELECT codigo FROM reservations WHERE idempotencia=?")) {
          ps.setString(1, key);
          try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) { return rs.getString(1); }
          }
        }
        // Causa raíz: setAutoCommit(false) ya abre txn; BEGIN IMMEDIATE anidado falla en SQLite. Se usa autoCommit=true + BEGIN IMMEDIATE explícito (single-writer).
        c.createStatement().execute("BEGIN IMMEDIATE");
        if (existeSolape(c, roomId, llegada, salida)) {
          try { c.createStatement().execute("ROLLBACK"); } catch (SQLException ignored) {}
          throw new IllegalStateException("sin disponibilidad para esas fechas");
        }
        String codigo = "H-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        long resId;
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO reservations(codigo,email,nombre,llegada,salida,huespedes,estado,origen,idempotencia) VALUES(?,?,?,?,?,?,'PENDIENTE',?,?)", Statement.RETURN_GENERATED_KEYS)) {
          ps.setString(1, codigo); ps.setString(2, email); ps.setString(3, nombre == null ? "" : nombre);
          ps.setString(4, llegada); ps.setString(5, salida); ps.setInt(6, huespedes);
          ps.setString(7, org); ps.setString(8, key);
          ps.executeUpdate();
          try (ResultSet rs = ps.getGeneratedKeys()) { rs.next(); resId = rs.getLong(1); }
        }
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO reservation_items(reservation_id,room_id,desde,hasta) VALUES(?,?,?,?)")) {
          ps.setLong(1, resId); ps.setLong(2, roomId); ps.setString(3, llegada); ps.setString(4, salida);
          ps.executeUpdate();
        }
        c.createStatement().execute("COMMIT");
        return codigo;
      } catch (SQLException | RuntimeException e) {
        try { c.createStatement().execute("ROLLBACK"); } catch (SQLException ignored) {}
        if (e instanceof IllegalStateException) throw (IllegalStateException) e;
        if (e instanceof IllegalArgumentException) throw (IllegalArgumentException) e;
        // Idempotencia ante carrera: si otro insertó la misma key, devolver su código
        try (PreparedStatement ps = c.prepareStatement("SELECT codigo FROM reservations WHERE idempotencia=?")) {
          ps.setString(1, key);
          try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getString(1);
          }
        } catch (SQLException ignored) {}
        throw new IllegalStateException(e.getMessage(), e);
      }
    } catch (SQLException e) {
      throw new IllegalStateException(e.getMessage(), e);
    }
  }

  private boolean existeSolape(Connection c, long roomId, String llegada, String salida) throws SQLException {
    String q = "SELECT 1 FROM reservation_items ri JOIN reservations r ON r.id=ri.reservation_id "
      + "WHERE ri.room_id=? AND r.estado!='CANCELADA' AND ri.desde < ? AND ? < ri.hasta LIMIT 1";
    try (PreparedStatement ps = c.prepareStatement(q)) {
      ps.setLong(1, roomId); ps.setString(2, salida); ps.setString(3, llegada);
      try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return true; }
    }
    try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM blocks WHERE (room_id=? OR room_id IS NULL) AND desde < ? AND ? < hasta LIMIT 1")) {
      ps.setLong(1, roomId); ps.setString(2, salida); ps.setString(3, llegada);
      try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
    }
  }
}
