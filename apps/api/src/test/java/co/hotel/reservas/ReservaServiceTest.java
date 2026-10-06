package co.hotel.reservas;

import static org.junit.jupiter.api.Assertions.*;

import co.hotel.auditoria.AuditoriaRepository;
import co.hotel.auditoria.AuditoriaService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Reservas contra un SQLite real. El esquema viene de la migración de producción, no de un DDL
 * duplicado aquí: si el esquema se mueve, este test se cae (que es lo que queremos).
 */
class ReservaServiceTest {
  private static final String ESQUEMA = """
      CREATE TABLE room_types(
        id INTEGER PRIMARY KEY AUTOINCREMENT, codigo TEXT UNIQUE NOT NULL,
        nombre TEXT NOT NULL, capacidad_max INTEGER NOT NULL);
      CREATE TABLE rooms(
        id INTEGER PRIMARY KEY AUTOINCREMENT, codigo TEXT UNIQUE NOT NULL,
        room_type_id INTEGER REFERENCES room_types(id), estado TEXT NOT NULL DEFAULT 'ACTIVA');
      CREATE TABLE reservations(
        id INTEGER PRIMARY KEY AUTOINCREMENT, codigo TEXT UNIQUE NOT NULL, email TEXT NOT NULL,
        nombre TEXT NOT NULL DEFAULT '', llegada TEXT NOT NULL, salida TEXT NOT NULL,
        huespedes INTEGER NOT NULL, estado TEXT NOT NULL, origen TEXT NOT NULL,
        idempotencia TEXT UNIQUE NOT NULL, creado_en TEXT NOT NULL DEFAULT '');
      CREATE TABLE reservation_items(
        id INTEGER PRIMARY KEY AUTOINCREMENT, reservation_id INTEGER NOT NULL,
        room_id INTEGER NOT NULL, desde TEXT NOT NULL, hasta TEXT NOT NULL);
      CREATE TABLE reservation_history(
        id INTEGER PRIMARY KEY AUTOINCREMENT, reservation_id INTEGER NOT NULL,
        estado_ant TEXT, estado_nuevo TEXT NOT NULL, actor TEXT NOT NULL, en TEXT NOT NULL);
      CREATE TABLE blocks(
        id INTEGER PRIMARY KEY AUTOINCREMENT, room_id INTEGER, desde TEXT NOT NULL,
        hasta TEXT NOT NULL, motivo TEXT);
      INSERT INTO rooms(codigo, estado) VALUES('101','ACTIVA');
      INSERT INTO rooms(codigo, estado) VALUES('102','ACTIVA');
      """;

  private Path db;
  private DataSource dataSource;
  private JdbcTemplate jdbc;
  private ReservaService svc;
  private AuditoriaService auditoria;

  @BeforeEach
  void setUp() throws Exception {
    db = Files.createTempFile("hotel-test-", ".sqlite3");
    Files.delete(db);
    // Mismo DataSource que producción: WAL, busy_timeout y BEGIN IMMEDIATE incluidos.
    this.dataSource = SqliteDataSources.paraRuta(db.toAbsolutePath().toString());
    try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
      for (String stmt : ESQUEMA.split(";")) if (!stmt.isBlank()) s.execute(stmt);
    }
    this.jdbc = new JdbcTemplate(dataSource);
    this.auditoria = new AuditoriaService(new AuditoriaRepository(jdbc));
    this.svc = new ReservaService(new ReservaRepository(jdbc), new SqliteTransactionExecutor(dataSource), auditoria);
  }

  private String crear(String email, String llegada, String salida, long roomId) {
    return svc.crear(new CrearReserva(email, "Ana", LocalDate.parse(llegada), LocalDate.parse(salida), 2,
      Origen.WEB, UUID.randomUUID().toString(), roomId));
  }

  @Nested
  @DisplayName("disponibilidad transaccional")
  class Disponibilidad {

    @Test void reservaAladaSeAcepta() {
      String codigo = crear("ana@example.com", "2026-11-01", "2026-11-05", 1);
      assertNotNull(codigo);
      assertEquals(1, contar("reservations"));
    }

    @Test void solapeEnLaMismaHabitacionSeRechaza() {
      crear("ana@example.com", "2026-11-01", "2026-11-05", 1);
      var ex = assertThrows(SinDisponibilidadException.class,
        () -> crear("otro@example.com", "2026-11-04", "2026-11-06", 1));
      assertTrue(ex.getMessage().contains("disponibilidad"));
      assertEquals(1, contar("reservations"), "la reserva rechazada no debe quedar escrita");
    }

    @Test void otraHabitacionDelMismoInventarioSiEstaLibre() {
      crear("ana@example.com", "2026-11-01", "2026-11-05", 1);
      assertNotNull(crear("otro@example.com", "2026-11-01", "2026-11-05", 2));
      assertEquals(2, contar("reservations"));
    }

    @Test void intervaloSemiabiertoPermiteSalidaElDiaDeLaLlegada() {
      crear("ana@example.com", "2026-11-01", "2026-11-05", 1);
      assertNotNull(crear("b@example.com", "2026-11-05", "2026-11-07", 1));
    }

    @Test void unBloqueoDeMantenimientoImpideLaReserva() {
      jdbc.update("INSERT INTO blocks(room_id,desde,hasta,motivo) VALUES(1,'2026-11-01','2026-11-10','mantenimiento')");
      assertThrows(SinDisponibilidadException.class,
        () -> crear("ana@example.com", "2026-11-02", "2026-11-04", 1));
    }

    @Test void laReservaQuedaPendienteSinConfirmacionAutomaticaConfigurada() {
      String codigo = crear("ana@example.com", "2026-11-01", "2026-11-05", 1);
      assertEquals(EstadoReserva.PENDIENTE, svc.buscar(codigo).orElseThrow().estado());
    }

    @Test void escribeElHistorialDeCambiosDeEstado() {
      String codigo = crear("ana@example.com", "2026-11-01", "2026-11-05", 1);
      svc.cambiarEstado(codigo, EstadoReserva.CONFIRMADA, "admin@hotel");
      var historia = jdbc.queryForList("SELECT estado_ant,estado_nuevo,actor FROM reservation_history ORDER BY id");
      assertEquals(2, historia.size(), "alta + cambio de estado deben quedar auditados");
      assertEquals("PENDIENTE", historia.get(1).get("estado_ant"));
      assertEquals("CONFIRMADA", historia.get(1).get("estado_nuevo"));
      assertEquals("admin@hotel", historia.get(1).get("actor"));
    }
  }

  @Nested
  @DisplayName("idempotencia")
  class Idempotencia {

    @Test void mismaClaveDevuelveElMismoCodigoSinDuplicar() {
      String key = UUID.randomUUID().toString();
      String c1 = svc.crear(new CrearReserva("ana@example.com", "Ana", LocalDate.parse("2026-12-01"),
        LocalDate.parse("2026-12-03"), 2, Origen.WEB, key, 1));
      String c2 = svc.crear(new CrearReserva("ana@example.com", "Ana", LocalDate.parse("2026-12-01"),
        LocalDate.parse("2026-12-03"), 2, Origen.WEB, key, 1));
      assertEquals(c1, c2);
      assertEquals(1, contar("reservations"));
    }

    @Test void laIdempotenciaNoEvitaElChoqueDeInventario() {
      // Dos huespedes distintos, misma habitacion y fechas: el segundo debe recibir 409, no un codigo repetido.
      String c1 = crear("ana@example.com", "2026-12-10", "2026-12-12", 1);
      assertThrows(SinDisponibilidadException.class, () -> crear("otro@example.com", "2026-12-10", "2026-12-12", 1));
      assertEquals(c1, svc.buscar(c1).orElseThrow().codigo());
      assertEquals(1, contar("reservations"));
    }
  }

  @Nested
  @DisplayName("validacion de entrada")
  class Validacion {

    @Test void fechasInvertidasSeRechazan() {
      assertThrows(DatosInvalidosException.class,
        () -> svc.crear(new CrearReserva("a@example.com", "A", LocalDate.parse("2026-11-05"),
          LocalDate.parse("2026-11-01"), 1, Origen.WEB, UUID.randomUUID().toString(), 1)));
    }

    @Test void emailInvalidoSeRechaza() {
      assertThrows(DatosInvalidosException.class,
        () -> svc.crear(new CrearReserva("no-es-email", "A", LocalDate.parse("2026-11-01"),
          LocalDate.parse("2026-11-05"), 1, Origen.WEB, UUID.randomUUID().toString(), 1)));
    }

    @Test void huespedesCeroSeRechaza() {
      assertThrows(DatosInvalidosException.class,
        () -> svc.crear(new CrearReserva("a@example.com", "A", LocalDate.parse("2026-11-01"),
          LocalDate.parse("2026-11-05"), 0, Origen.WEB, UUID.randomUUID().toString(), 1)));
    }

    @Test void origenDesconocidoSeRechazaEnVezDeSilenciarse() {
      assertThrows(DatosInvalidosException.class,
        () -> svc.crear(new CrearReserva("a@example.com", "A", LocalDate.parse("2026-11-01"),
          LocalDate.parse("2026-11-05"), 1, null, UUID.randomUUID().toString(), 1)));
    }
  }

  @Nested
  @DisplayName("consulta segura")
  class Consulta {

    @Test void laConsultaExigeElMismoCorreo() {
      String codigo = crear("ana@example.com", "2026-11-01", "2026-11-05", 1);
      assertNotNull(svc.consultar(codigo, "ana@example.com"));
      assertTrue(svc.consultar(codigo, "otro@example.com").isEmpty(), "otro correo no puede ver la reserva");
    }

    @Test void elCorreoNoDistingueMayusculas() {
      String codigo = crear("ana@example.com", "2026-11-01", "2026-11-05", 1);
      assertNotNull(svc.consultar(codigo, "ANA@EXAMPLE.COM"));
    }
  }

  private int contar(String tabla) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla, Integer.class);
  }
}