package co.hotel.reservas;

import java.nio.file.*;
import java.sql.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class ReservaServiceTest {
  Path db;
  ReservaService svc;

  @BeforeEach void setup() throws Exception {
    db = Files.createTempFile("hotel-test-", ".sqlite3");
    Files.deleteIfExists(db);
    String url = "jdbc:sqlite:" + db.toAbsolutePath();
    try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement()) {
      s.execute("CREATE TABLE rooms(id INTEGER PRIMARY KEY AUTOINCREMENT, codigo TEXT UNIQUE NOT NULL)");
      s.execute("CREATE TABLE reservations(id INTEGER PRIMARY KEY AUTOINCREMENT, codigo TEXT UNIQUE NOT NULL, email TEXT NOT NULL, nombre TEXT NOT NULL DEFAULT '', llegada TEXT NOT NULL, salida TEXT NOT NULL, huespedes INTEGER NOT NULL, estado TEXT NOT NULL, origen TEXT NOT NULL, idempotencia TEXT UNIQUE NOT NULL)");
      s.execute("CREATE TABLE reservation_items(id INTEGER PRIMARY KEY AUTOINCREMENT, reservation_id INTEGER NOT NULL, room_id INTEGER NOT NULL, desde TEXT NOT NULL, hasta TEXT NOT NULL)");
      s.execute("CREATE TABLE blocks(id INTEGER PRIMARY KEY AUTOINCREMENT, room_id INTEGER, desde TEXT NOT NULL, hasta TEXT NOT NULL, motivo TEXT)");
      s.execute("INSERT INTO rooms(codigo) VALUES('101')");
    }
    svc = new ReservaService(url);
  }

  @Test void rechazaSuperpuestaQueExcedeInventario() {
    String c1 = svc.crear("ana@example.com", "Ana", "2026-11-01", "2026-11-05", 2, "WEB", UUID.randomUUID().toString(), 1);
    assertNotNull(c1);
    IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
      svc.crear("otro@example.com", "Otro", "2026-11-04", "2026-11-06", 2, "WEB", UUID.randomUUID().toString(), 1));
    assertTrue(ex.getMessage().contains("disponibilidad"));
  }

  @Test void idempotenciaDevuelveMismoCodigoSinDuplicar() throws Exception {
    String key = UUID.randomUUID().toString();
    String c1 = svc.crear("ana@example.com", "Ana", "2026-12-01", "2026-12-03", 2, "WEB", key, 1);
    String c2 = svc.crear("ana@example.com", "Ana", "2026-12-01", "2026-12-03", 2, "WEB", key, 1);
    assertEquals(c1, c2);
    try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
         Statement s = c.createStatement();
         ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM reservations")) {
      rs.next();
      assertEquals(1, rs.getInt(1));
    }
  }

  @Test void permiteSalidaIgualLlegada() {
    svc.crear("a@example.com", "A", "2026-11-01", "2026-11-05", 2, "WEB", UUID.randomUUID().toString(), 1);
    String c2 = svc.crear("b@example.com", "B", "2026-11-05", "2026-11-07", 2, "WEB", UUID.randomUUID().toString(), 1);
    assertNotNull(c2);
  }
}
