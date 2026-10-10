package co.hotel.reservas;

import static org.junit.jupiter.api.Assertions.*;

import co.hotel.auditoria.AuditoriaRepository;
import co.hotel.auditoria.AuditoriaService;
import co.hotel.pruebas.HotelDePrueba;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * ¿Sirve SQLite para el volumen real de este hotel? La respuesta honesta sale de medir, no de
 * repetir una cifra de manual.
 *
 * Lo que se mide:
 * 1. Que N hilos compitiendo por la MISMA habitación no producen sobreventa ni pérdida de
 *    reservas (integridad).
 * 2. Cuántas reservas por segundo aguanta el archivo en el hardware de esta máquina (rendimiento).
 * 3. Que el tamaño de base y el de un hotel con años de historial siguen siendo manejables.
 *
 * El rendimiento de (2) es de esta máquina, no del hotel: sirve para descartar el orders of
 * magnitude equivocados, no para prometer cifras de producción.
 */
class EscaladoSqliteTest {

  private static final String ESQUEMA = """
      CREATE TABLE room_types(id INTEGER PRIMARY KEY AUTOINCREMENT, codigo TEXT UNIQUE NOT NULL,
        nombre TEXT NOT NULL, capacidad_max INTEGER NOT NULL);
      CREATE TABLE rooms(id INTEGER PRIMARY KEY AUTOINCREMENT, codigo TEXT UNIQUE NOT NULL,
        room_type_id INTEGER REFERENCES room_types(id), estado TEXT NOT NULL DEFAULT 'ACTIVA',
        nombre TEXT NOT NULL DEFAULT '');
      CREATE TABLE users(id INTEGER PRIMARY KEY AUTOINCREMENT, email TEXT UNIQUE NOT NULL,
        hash TEXT NOT NULL, rol TEXT NOT NULL, activo INTEGER NOT NULL DEFAULT 1, creado_en TEXT NOT NULL);
      CREATE TABLE reservations(id INTEGER PRIMARY KEY AUTOINCREMENT, codigo TEXT UNIQUE NOT NULL,
        email TEXT NOT NULL, nombre TEXT NOT NULL DEFAULT '', llegada TEXT NOT NULL, salida TEXT NOT NULL,
        huespedes INTEGER NOT NULL, estado TEXT NOT NULL, origen TEXT NOT NULL,
        idempotencia TEXT UNIQUE NOT NULL, creado_en TEXT NOT NULL DEFAULT '',
        total_cents INTEGER, moneda TEXT, rate_plan_id INTEGER,
        hora_entrada TEXT, hora_salida TEXT, politica_cancelacion TEXT);
      CREATE TABLE hotel_config(
        clave TEXT PRIMARY KEY, valor TEXT NOT NULL,
        actualizado_en TEXT NOT NULL DEFAULT '');
      CREATE TABLE rate_plans(id INTEGER PRIMARY KEY AUTOINCREMENT, codigo TEXT UNIQUE NOT NULL,
        nombre TEXT NOT NULL, moneda TEXT NOT NULL, activo INTEGER NOT NULL DEFAULT 1,
        descuento_pct INTEGER NOT NULL DEFAULT 0);
      CREATE TABLE rates(id INTEGER PRIMARY KEY AUTOINCREMENT, rate_plan_id INTEGER NOT NULL,
        room_type_id INTEGER NOT NULL, fecha TEXT NOT NULL, precio_cents INTEGER NOT NULL,
        min_estancia INTEGER, max_estancia INTEGER, cerrado INTEGER NOT NULL DEFAULT 0,
        UNIQUE(rate_plan_id,room_type_id,fecha));
      CREATE TABLE reservation_items(id INTEGER PRIMARY KEY AUTOINCREMENT, reservation_id INTEGER NOT NULL,
        room_id INTEGER NOT NULL, desde TEXT NOT NULL, hasta TEXT NOT NULL);
      CREATE TABLE reservation_history(id INTEGER PRIMARY KEY AUTOINCREMENT, reservation_id INTEGER NOT NULL,
        estado_ant TEXT, estado_nuevo TEXT NOT NULL, detalle TEXT, actor TEXT NOT NULL, en TEXT NOT NULL);
      CREATE TABLE blocks(id INTEGER PRIMARY KEY AUTOINCREMENT, room_id INTEGER, desde TEXT NOT NULL,
        hasta TEXT NOT NULL, motivo TEXT NOT NULL DEFAULT '');
      INSERT INTO rooms(codigo,estado,nombre) VALUES('101','ACTIVA','Habitación 101');
      """;

  private DataSource dataSource(String nombre) throws Exception {
    Path db = Files.createTempFile("hotel-carga-", ".sqlite3");
    Files.delete(db);
    DataSource ds = SqliteDataSources.paraRuta(db.toAbsolutePath().toString());
    try (var c = ds.getConnection(); var s = c.createStatement()) {
      for (String stmt : ESQUEMA.split(";")) if (!stmt.isBlank()) s.execute(stmt);
    }
    // La reserva pública exige precio acordado; aquí se mide concurrencia y rendimiento.
    var jdbc = new JdbcTemplate(ds);
    HotelDePrueba.tarifarTodo(jdbc, java.time.LocalDate.parse("2026-01-01"), java.time.LocalDate.parse("2026-04-05"));
    HotelDePrueba.tarifarTodo(jdbc, java.time.LocalDate.parse("2026-11-01"), java.time.LocalDate.parse("2026-11-06"));
    return ds;
  }

  private ReservaService servicio(DataSource ds) {
    JdbcTemplate jdbc = new JdbcTemplate(ds);
    var tx = new SqliteTransactionExecutor(ds);
    var inventario = new co.hotel.inventario.InventarioService(new co.hotel.inventario.InventarioRepository(jdbc),
      new co.hotel.inventario.TarifaRepository(jdbc), tx);
    return new ReservaService(new ReservaRepository(jdbc), tx,
      new AuditoriaService(new AuditoriaRepository(jdbc)), inventario,
      new co.hotel.huespedes.ReservaServiceHuesped(jdbc,
        new co.hotel.huespedes.UsuariosHuespedRepository(jdbc)),
      new co.hotel.hotel.HotelConfigService(new co.hotel.hotel.HotelConfigRepository(jdbc), tx));
  }

  @Test
  @DisplayName("20 hilos compitiendo por la misma habitación: uno gana, los demás reciben 409, cero sobreventa")
  void concurrenciaNoProduceSobreventa() throws Exception {
    DataSource ds = dataSource("concurrencia");
    ReservaService svc = servicio(ds);

    int hilos = 20;
    ExecutorService pool = Executors.newFixedThreadPool(hilos);
    CountDownLatch todosListos = new CountDownLatch(hilos);
    CountDownLatch arrancar = new CountDownLatch(1);
    AtomicInteger aceptadas = new AtomicInteger();
    AtomicInteger rechazadas = new AtomicInteger();

    List<Callable<Void>> tareas = java.util.stream.IntStream.range(0, hilos).mapToObj(i -> (Callable<Void>) () -> {
      todosListos.countDown();
      arrancar.await(10, TimeUnit.SECONDS);
      try {
        svc.crear(new CrearReserva("huesped" + i + "@example.com", "Huésped " + i,
          LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-05"), 2, Origen.WEB,
          UUID.randomUUID().toString(), 1));
        aceptadas.incrementAndGet();
      } catch (SinDisponibilidadException e) {
        rechazadas.incrementAndGet();
      }
      return null;
    }).toList();

    List<Future<Void>> futuros = tareas.stream().map(pool::submit).toList();
    todosListos.await(10, TimeUnit.SECONDS);
    arrancar.countDown();
    for (Future<Void> f : futuros) f.get(30, TimeUnit.SECONDS);
    pool.shutdown();

    JdbcTemplate jdbc = new JdbcTemplate(ds);
    Integer enBase = jdbc.queryForObject("SELECT COUNT(*) FROM reservations", Integer.class);
    Integer lineas = jdbc.queryForObject("SELECT COUNT(*) FROM reservation_items", Integer.class);

    assertEquals(1, enBase, "una sola habitación disponible, una sola reserva");
    assertEquals(1, lineas, "ni una línea de reserva de más");
    assertEquals(1, aceptadas.get());
    assertEquals(hilos - 1, rechazadas.get(), "el resto debe recibir 409, no un error raro");
    System.out.printf("CONCURRENCIA: %d hilos -> aceptadas=%d rechazadas=%d registros=%d%n",
      hilos, aceptadas.get(), rechazadas.get(), enBase);
  }

  @Test
  @DisplayName("300 reservas sobre 10 habitaciones: mide el rendimiento real del archivo")
  void rendimientoDeReservas() throws Exception {
    DataSource ds = dataSource("rendimiento");
    ReservaService svc = servicio(ds);

    JdbcTemplate jdbc = new JdbcTemplate(ds);
    for (int i = 2; i <= 10; i++)
      jdbc.update("INSERT INTO rooms(codigo,estado,nombre) VALUES(?,'ACTIVA',?)", String.valueOf(100 + i), "H" + i);
    // Fechas futuras a propósito: desde la R141 el alta rechaza llegadas pasadas y este
    // test mide caudal, no historia (para historia con pasado está tamañoDeArchivoTrasHistorial).
    HotelDePrueba.tarifarTodo(jdbc, java.time.LocalDate.parse("2026-11-01"), java.time.LocalDate.parse("2027-02-10"));

    int reservas = 300;
    long inicio = System.nanoTime();
    for (int i = 0; i < reservas; i++) {
      long habitacion = (i % 10) + 1;
      // Cada tanda del hotel avanza 3 días: con 2 noches ocupadas y separación de 3, las
      // reservas de una misma habitación nunca se solapan entre sí.
      int dia = (i / 10) * 3 + 1;
      svc.crear(new CrearReserva("h" + i + "@example.com", "Huésped " + i,
        LocalDate.parse("2026-11-01").plusDays(dia), LocalDate.parse("2026-11-01").plusDays(dia + 2),
        2, Origen.WEB, UUID.randomUUID().toString(), habitacion));
    }
    double segundos = (System.nanoTime() - inicio) / 1_000_000_000.0;
    double porSegundo = reservas / segundos;
    System.out.printf("RENDIMIENTO: %d reservas en %.2f s -> %.0f reservas/segundo%n",
      reservas, segundos, porSegundo);

    assertEquals(reservas, jdbc.queryForObject("SELECT COUNT(*) FROM reservations", Integer.class));
    // Un hotel de 10 habitaciones no llega a 10 reservas por segundo ni en temporada alta.
    assertTrue(porSegundo > 5, "por debajo de 5 reservas/s el motor sería un problema real");
  }

  @Test
  @DisplayName("un archivo con años de historial sigue siendo pequeño y rápido de respaldar")
  void tamañoDeArchivoTrasHistorial() throws Exception {
    DataSource ds = dataSource("historial");
    JdbcTemplate jdbc = new JdbcTemplate(ds);

    // 50 000 reservas históricas, simuladas por inserción directa: es el orden de magnitud de
    // un hotel pequeño acumulando años, no una cifra inventada de un hotel imaginary.
    jdbc.update("""
      WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n+1 FROM seq WHERE n < 50000)
      INSERT INTO reservations(codigo,email,nombre,llegada,salida,huespedes,estado,origen,
                              idempotencia,creado_en)
      SELECT 'H-' || n, 'h' || n || '@example.com', 'Huésped', '2026-01-01', '2026-01-03', 2,
             'CONFIRMADA', 'WEB', 'idem-' || n, '2026-01-01T10:00:00' FROM seq
      """);

    Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM reservations", Integer.class);
    assertEquals(50_000, total);

    Path archivo = ((org.sqlite.SQLiteDataSource) ds).getUrl().startsWith("jdbc:sqlite:")
      ? Path.of(((org.sqlite.SQLiteDataSource) ds).getUrl().substring("jdbc:sqlite:".length()))
      : null;
    if (archivo != null && Files.exists(archivo)) {
      long kb = Files.size(archivo) / 1024;
      System.out.printf("HISTORIAL: %d reservas -> %d KB de archivo SQLite%n", total, kb);
      assertTrue(kb < 50_000, "50 000 reservas deberían ocupar bastante menos de 50 MB");
    }
  }
}