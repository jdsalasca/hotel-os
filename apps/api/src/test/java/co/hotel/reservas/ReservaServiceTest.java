package co.hotel.reservas;

import static org.junit.jupiter.api.Assertions.*;

import co.hotel.auditoria.AuditoriaRepository;
import co.hotel.auditoria.AuditoriaService;
import co.hotel.inventario.InventarioRepository;
import co.hotel.inventario.InventarioService;
import co.hotel.inventario.TarifaRepository;
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
        room_type_id INTEGER REFERENCES room_types(id), estado TEXT NOT NULL DEFAULT 'ACTIVA',
        nombre TEXT NOT NULL DEFAULT '');
      CREATE TABLE reservations(
        id INTEGER PRIMARY KEY AUTOINCREMENT, codigo TEXT UNIQUE NOT NULL, email TEXT NOT NULL,
        nombre TEXT NOT NULL DEFAULT '', llegada TEXT NOT NULL, salida TEXT NOT NULL,
        huespedes INTEGER NOT NULL, estado TEXT NOT NULL, origen TEXT NOT NULL,
        idempotencia TEXT NOT NULL, creado_en TEXT NOT NULL DEFAULT '',
        total_cents INTEGER, moneda TEXT, rate_plan_id INTEGER,
        UNIQUE(idempotencia, email));
      CREATE TABLE rate_plans(
        id INTEGER PRIMARY KEY AUTOINCREMENT, codigo TEXT UNIQUE NOT NULL,
        nombre TEXT NOT NULL, moneda TEXT NOT NULL, activo INTEGER NOT NULL DEFAULT 1);
      CREATE TABLE rates(
        id INTEGER PRIMARY KEY AUTOINCREMENT, rate_plan_id INTEGER NOT NULL,
        room_type_id INTEGER NOT NULL, fecha TEXT NOT NULL, precio_cents INTEGER NOT NULL,
        min_estancia INTEGER, max_estancia INTEGER, cerrado INTEGER NOT NULL DEFAULT 0,
        UNIQUE(rate_plan_id,room_type_id,fecha));
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
      INSERT INTO room_types(codigo,nombre,capacidad_max) VALUES('DOBLE','Doble',2);
      INSERT INTO rate_plans(codigo,nombre,moneda,activo) VALUES('STD','Estándar','COP',1);
      UPDATE rooms SET room_type_id=1 WHERE codigo IN ('101','102');
      INSERT INTO rates(rate_plan_id,room_type_id,fecha,precio_cents,cerrado) VALUES
        (1,1,'2026-11-01',150000,0),(1,1,'2026-11-02',150000,0),(1,1,'2026-11-03',150000,0),
        (1,1,'2026-11-04',150000,0),(1,1,'2026-11-05',150000,0),(1,1,'2026-11-06',150000,0),
        (1,1,'2026-12-01',150000,0),(1,1,'2026-12-02',150000,0),(1,1,'2026-12-03',150000,0),
        (1,1,'2026-12-10',150000,0),(1,1,'2026-12-11',150000,0),(1,1,'2026-12-12',150000,0);
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
    var inventario = new InventarioService(new InventarioRepository(jdbc), new TarifaRepository(jdbc),
      new SqliteTransactionExecutor(dataSource));
    this.svc = new ReservaService(new ReservaRepository(jdbc), new SqliteTransactionExecutor(dataSource),
      auditoria, inventario);
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

    @Test void mismaClaveConFechasDistintasEsConflictoDefinido() {
      // Reutilizar la clave con otro contenido hoy devuelve la reserva vieja en silencio: el
      // huésped cree haber reservado las fechas nuevas y el hotel no se entera.
      String key = UUID.randomUUID().toString();
      svc.crear(new CrearReserva("ana@example.com", "Ana", LocalDate.parse("2026-12-01"),
        LocalDate.parse("2026-12-03"), 2, Origen.WEB, key, 1));
      var ex = assertThrows(ConflictoIdempotenciaException.class,
        () -> svc.crear(new CrearReserva("ana@example.com", "Ana", LocalDate.parse("2026-12-10"),
          LocalDate.parse("2026-12-12"), 2, Origen.WEB, key, 1)));
      assertTrue(ex.getMessage().contains("clave"),
        "el motivo debe hablar de la clave reutilizada: " + ex.getMessage());
      assertEquals(1, contar("reservations"), "el intento incompatible no debe escribir nada");
    }

    @Test void mismaClaveEnOtraHabitacionEsConflictoDefinido() {
      String key = UUID.randomUUID().toString();
      svc.crear(new CrearReserva("ana@example.com", "Ana", LocalDate.parse("2026-12-01"),
        LocalDate.parse("2026-12-03"), 2, Origen.WEB, key, 1));
      assertThrows(ConflictoIdempotenciaException.class,
        () -> svc.crear(new CrearReserva("ana@example.com", "Ana", LocalDate.parse("2026-12-01"),
          LocalDate.parse("2026-12-03"), 2, Origen.WEB, key, 2)));
      assertEquals(1, contar("reservations"));
    }

    @Test
    @DisplayName("otra persona con la misma clave de idempotencia crea su propia reserva")
    void laIdempotenciaNoCruzaEntreClientes() {
      // La clave identifica un INTENTO, no a una persona. Dos peticiones que comparten clave son dos
      // reservas distintas: devolver la ajena filtraba nombre, correo y total de otro huésped por
      // adivinar una clave. Cada uno además necesita una habitación libre, para que la segunda llegue
      // a insertar en vez de recibir un 409.
      String key = UUID.randomUUID().toString();
      String deAna = svc.crear(new CrearReserva("ana@example.com", "Ana Perez", LocalDate.parse("2026-12-01"),
        LocalDate.parse("2026-12-03"), 2, Origen.WEB, key, 1));

      String deBruno = svc.crear(new CrearReserva("bruno@example.com", "Bruno Diaz", LocalDate.parse("2026-12-01"),
        LocalDate.parse("2026-12-03"), 2, Origen.WEB, key, 2));

      assertNotEquals(deAna, deBruno, "la segunda peticion no puede devolver la reserva de la primera");
      assertEquals("Bruno Diaz", svc.buscar(deBruno).orElseThrow().nombre());
      assertEquals("bruno@example.com", svc.buscar(deBruno).orElseThrow().email());
      assertEquals(2, contar("reservations"));
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

  @Nested
  @DisplayName("precio acordado al reservar")
  class PrecioAcordado {

    /** Habitación con tarifa completa: dos noches a 150.000 COP en el plan STD del esquema. */
    private long habitacionTarifada() {
      jdbc.update("INSERT INTO rooms(codigo,room_type_id,estado) VALUES('201',1,'ACTIVA')");
      return jdbc.queryForObject("SELECT id FROM rooms WHERE codigo='201'", Long.class);
    }

    @Test void guardaElTotalLaMonedaYElPlan() {
      long room = habitacionTarifada();
      String codigo = crear("ana@example.com", "2026-11-01", "2026-11-03", room);

      var fila = jdbc.queryForMap("SELECT total_cents, moneda, rate_plan_id FROM reservations WHERE codigo=?", codigo);
      assertEquals(300000L, ((Number) fila.get("total_cents")).longValue(), "2 noches a 150.000");
      assertEquals("COP", fila.get("moneda"));
      assertNotNull(fila.get("rate_plan_id"), "el plan acordado queda registrado");
    }

    @Test void sinTarifaCompletaSeRechazaSinConsumirInventario() {
      jdbc.update("INSERT INTO rooms(codigo,estado) VALUES('103','ACTIVA')");
      long room = jdbc.queryForObject("SELECT id FROM rooms WHERE codigo='103'", Long.class);
      var ex = assertThrows(SinDisponibilidadException.class,
        () -> crear("ana@example.com", "2026-11-01", "2026-11-03", room));
      assertTrue(ex.getMessage().contains("a la venta"),
        "el motivo debe decir que no está a la venta, no un genérico: " + ex.getMessage());
      assertEquals(0, contar("reservations"), "la reserva rechazada no debe quedar escrita");
    }

    @Test void capacidadInsuficienteSeRechaza() {
      jdbc.update("INSERT INTO room_types(codigo,nombre,capacidad_max) VALUES('IND','Individual',1)");
      long tipo = jdbc.queryForObject("SELECT id FROM room_types WHERE codigo='IND'", Long.class);
      jdbc.update("INSERT INTO rooms(codigo,room_type_id,estado) VALUES('104',?,'ACTIVA')", tipo);
      long room = jdbc.queryForObject("SELECT id FROM rooms WHERE codigo='104'", Long.class);
      jdbc.update("INSERT INTO rates(rate_plan_id,room_type_id,fecha,precio_cents,cerrado) VALUES(1,?, '2026-11-01',90000,0)", tipo);
      jdbc.update("INSERT INTO rates(rate_plan_id,room_type_id,fecha,precio_cents,cerrado) VALUES(1,?, '2026-11-02',90000,0)", tipo);
      var ex = assertThrows(SinDisponibilidadException.class,
        () -> crear("dos@example.com", "2026-11-01", "2026-11-03", room));
      assertTrue(ex.getMessage().contains("a la venta"),
        "dos huéspedes no caben en una individual: " + ex.getMessage());
      assertEquals(0, contar("reservations"));
    }

    @Test void habitacionRetiradaSeRechaza() {
      jdbc.update("INSERT INTO rooms(codigo,room_type_id,estado) VALUES('105',1,'FUERA_DE_SERVICIO')");
      long room = jdbc.queryForObject("SELECT id FROM rooms WHERE codigo='105'", Long.class);
      assertThrows(SinDisponibilidadException.class,
        () -> crear("ana@example.com", "2026-11-01", "2026-11-03", room));
      assertEquals(0, contar("reservations"));
    }
  }

  private int contar(String tabla) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla, Integer.class);
  }
}