package co.hotel.inventario;

import static org.junit.jupiter.api.Assertions.*;

import co.hotel.pruebas.ContadorConsultas;
import co.hotel.reservas.SqliteDataSources;
import co.hotel.reservas.SqliteTransactionExecutor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.YearMonth;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * La búsqueda recorre habitaciones y planes y el calendario repite la búsqueda por día: una
 * sola petición HTTP nunca implicó una sola consulta SQL. Estos tests fijan cotas para que un
 * N+1 reintroducido truene en vez de degradar en silencio.
 */
class RendimientoInventarioTest {

  private ContadorConsultas.Contador contador;
  private InventarioService svc;
  private TarifaService tarifas;
  private org.springframework.jdbc.core.JdbcTemplate jdbc;
  private long habitacionId;

  @BeforeEach
  void setUp() throws Exception {
    Path db = Files.createTempFile("hotel-rend-", ".sqlite3");
    Files.delete(db);
    DataSource base = SqliteDataSources.paraRuta(db.toAbsolutePath().toString());
    try (Connection c = base.getConnection(); Statement s = c.createStatement()) {
      s.execute("CREATE TABLE room_types(id INTEGER PRIMARY KEY AUTOINCREMENT, codigo TEXT UNIQUE NOT NULL,"
        + " nombre TEXT NOT NULL, capacidad_max INTEGER NOT NULL)");
      s.execute("CREATE TABLE rooms(id INTEGER PRIMARY KEY AUTOINCREMENT, codigo TEXT UNIQUE NOT NULL,"
        + " room_type_id INTEGER REFERENCES room_types(id), estado TEXT NOT NULL DEFAULT 'ACTIVA',"
        + " nombre TEXT NOT NULL DEFAULT '')");
      s.execute("CREATE TABLE rate_plans(id INTEGER PRIMARY KEY AUTOINCREMENT, codigo TEXT UNIQUE NOT NULL,"
        + " nombre TEXT NOT NULL, moneda TEXT NOT NULL, activo INTEGER NOT NULL DEFAULT 1,"
        + " descuento_pct INTEGER NOT NULL DEFAULT 0)");
      s.execute("CREATE TABLE rates(id INTEGER PRIMARY KEY AUTOINCREMENT, rate_plan_id INTEGER NOT NULL,"
        + " room_type_id INTEGER NOT NULL, fecha TEXT NOT NULL, precio_cents INTEGER NOT NULL,"
        + " min_estancia INTEGER, max_estancia INTEGER, cerrado INTEGER NOT NULL DEFAULT 0,"
        + " UNIQUE(rate_plan_id,room_type_id,fecha))");
      s.execute("CREATE TABLE blocks(id INTEGER PRIMARY KEY AUTOINCREMENT, room_id INTEGER,"
        + " desde TEXT NOT NULL, hasta TEXT NOT NULL, motivo TEXT NOT NULL DEFAULT '')");
      s.execute("CREATE TABLE reservations(id INTEGER PRIMARY KEY AUTOINCREMENT, codigo TEXT UNIQUE NOT NULL,"
        + " email TEXT NOT NULL, nombre TEXT NOT NULL DEFAULT '', llegada TEXT NOT NULL, salida TEXT NOT NULL,"
        + " huespedes INTEGER NOT NULL, estado TEXT NOT NULL, origen TEXT NOT NULL,"
        + " idempotencia TEXT UNIQUE NOT NULL, creado_en TEXT NOT NULL DEFAULT '')");
      s.execute("CREATE TABLE reservation_items(id INTEGER PRIMARY KEY AUTOINCREMENT, reservation_id INTEGER NOT NULL,"
        + " room_id INTEGER NOT NULL, desde TEXT NOT NULL, hasta TEXT NOT NULL)");
    }
    contador = ContadorConsultas.envolver(base);
    jdbc = new org.springframework.jdbc.core.JdbcTemplate(contador.fuente());
    var tx = new SqliteTransactionExecutor(contador.fuente());
    svc = new InventarioService(new InventarioRepository(jdbc), new TarifaRepository(jdbc), tx);
    tarifas = new TarifaService(new TarifaRepository(jdbc), new InventarioRepository(jdbc), tx);

    RoomType tipo = svc.crearTipo("DOBLE", "Habitación doble", 2);
    long tipoId = tipo.id();
    PlanTarifario cop = tarifas.crearPlan("PES", "Plan pesos", "COP");
    PlanTarifario flex = tarifas.crearPlan("FLEX", "Flexible", "COP");
    LocalDate desde = LocalDate.parse("2026-11-01");
    for (int i = 0; i < 3; i++) {
      tarifas.fijarPrecio(cop, tipoId, desde.plusDays(i), 150_000);
      tarifas.fijarPrecio(flex, tipoId, desde.plusDays(i), 140_000);
    }
    for (String codigo : new String[] { "101", "102", "103", "104" }) {
      Habitacion h = svc.crearHabitacion(codigo, tipoId, "Habitación " + codigo);
      if (habitacionId == 0) habitacionId = h.id();
    }
    contador.reiniciar();
  }

  @Test
  @DisplayName("la búsqueda agrupa lecturas: 4 habitaciones y 2 planes en un puñado de consultas")
  void busquedaAgrupaLecturas() {
    var ofertas = svc.disponiblesConPrecio(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-04"), 2);

    assertEquals(8, ofertas.size(), "4 habitaciones × 2 planes, el resultado no cambia");
    assertTrue(contador.consultas() <= 8,
      "1 libres + 1 tipos + 1 planes + 1 tipo × 2 noches, fue " + contador.consultas());
  }

  @Test
  @DisplayName("el detalle cuesta un puñado fijo, no una lista completa de habitaciones")
  void detalleCuestaPunadoFijo() {
    var detalle = svc.detalleOferta(habitacionId, LocalDate.parse("2026-11-01"),
      LocalDate.parse("2026-11-04"), 2);

    assertTrue(detalle.isPresent());
    assertTrue(contador.consultas() <= 7,
      "habitación + tipo + libre + planes + 2 noches, fue " + contador.consultas());
  }

  @Test
  @DisplayName("el calendario de un mes trae las tarifas una vez, no una por día")
  void calendarioMensualAcotado() {
    var dias = svc.calendarioMensual(YearMonth.parse("2026-11"), 2);

    assertEquals(30, dias.size());
    assertTrue(contador.consultas() <= 12,
      "1 habitaciones + 1 reservas + 1 bloqueos + 1 tipos + 1 planes + 1 tipo × 2 "
        + "meses-tarifa, fue " + contador.consultas());
  }

  @Test
  @DisplayName("los libres en memoria coinciden con la consulta por día")
  void libresEnMemoriaConParidadSql() {
    RoomType tipo = svc.crearTipo("SUITE", "Habitación suite", 2);
    PlanTarifario cop = tarifas.crearPlan("PES2", "Plan pesos 2", "COP");
    LocalDate d1 = LocalDate.parse("2026-11-01");
    for (int i = 0; i < 3; i++) tarifas.fijarPrecio(cop, tipo.id(), d1.plusDays(i), 150_000);
    svc.crearHabitacion("201", tipo.id(), "Libre");
    Habitacion ocupada = svc.crearHabitacion("202", tipo.id(), "Ocupada");
    Habitacion bloqueada = svc.crearHabitacion("203", tipo.id(), "Bloqueada");
    Habitacion retirada = svc.crearHabitacion("204", tipo.id(), "Retirada");
    svc.cambiarEstado(retirada.id(), EstadoHabitacion.FUERA_DE_SERVICIO);
    reservar(ocupada.id(), "2026-11-01", "2026-11-03");
    svc.bloquear(bloqueada.id(), LocalDate.parse("2026-11-02"), LocalDate.parse("2026-11-04"), "Obra");

    for (int i = 0; i < 3; i++) {
      LocalDate dia = d1.plusDays(i);
      var memoria = svc.calendarioMensual(YearMonth.parse("2026-11"), 2).stream()
        .filter(d -> d.fecha().equals(dia)).findFirst().orElseThrow();
      long esperadas = svc.disponibles(dia, dia.plusDays(1)).size();
      assertEquals(esperadas, memoria.disponibles(),
        "el día " + dia + " coincide con la consulta por día");
    }
    assertEquals(5, svc.calendarioMensual(YearMonth.parse("2026-11"), 2).stream()
      .filter(d -> d.fecha().equals(LocalDate.parse("2026-11-02"))).findFirst().orElseThrow()
      .disponibles(), "el día 2 quedan las 4 del setup + la 201 (ocupada, bloqueada y retirada, fuera)");
  }

  private void reservar(long roomId, String desde, String hasta) {
    jdbc.update("INSERT INTO reservations(codigo,email,nombre,llegada,salida,huespedes,estado,"
      + "origen,idempotencia,creado_en) VALUES(?,?,?,?,?,2,?,?,?,datetime('now'))",
      "R-" + roomId + desde, "hotel@ejemplo.com", "Huésped", desde, hasta, "CONFIRMADA", "WEB",
      "idem-" + roomId + desde);
    Long id = jdbc.queryForObject("SELECT id FROM reservations WHERE codigo=?", Long.class,
      "R-" + roomId + desde);
    jdbc.update("INSERT INTO reservation_items(reservation_id,room_id,desde,hasta) VALUES(?,?,?,?)",
      id, roomId, desde, hasta);
  }
}
