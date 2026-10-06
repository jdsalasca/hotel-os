package co.hotel.inventario;

import static org.junit.jupiter.api.Assertions.*;

import co.hotel.reservas.SqliteDataSources;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Inventario y tarifas contra un SQLite real. El hotel registra lo suyo; aquí no hay nombres,
 * precios ni capacidades inventados: cada valor viene del test.
 */
class InventarioServiceTest {

  private Path db;
  private DataSource dataSource;
  private JdbcTemplate jdbc;
  private InventarioService svc;
  private TarifaService tarifas;

  @BeforeEach
  void setUp() throws Exception {
    db = Files.createTempFile("hotel-inv-", ".sqlite3");
    Files.delete(db);
    dataSource = SqliteDataSources.paraRuta(db.toAbsolutePath().toString());
    try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
      s.execute("CREATE TABLE room_types(id INTEGER PRIMARY KEY AUTOINCREMENT, codigo TEXT UNIQUE NOT NULL,"
        + " nombre TEXT NOT NULL, capacidad_max INTEGER NOT NULL)");
      s.execute("CREATE TABLE rooms(id INTEGER PRIMARY KEY AUTOINCREMENT, codigo TEXT UNIQUE NOT NULL,"
        + " room_type_id INTEGER REFERENCES room_types(id), estado TEXT NOT NULL DEFAULT 'ACTIVA',"
        + " nombre TEXT NOT NULL DEFAULT '')");
      s.execute("CREATE TABLE rate_plans(id INTEGER PRIMARY KEY AUTOINCREMENT, codigo TEXT UNIQUE NOT NULL,"
        + " nombre TEXT NOT NULL, moneda TEXT NOT NULL, activo INTEGER NOT NULL DEFAULT 1)");
      s.execute("CREATE TABLE rates(id INTEGER PRIMARY KEY AUTOINCREMENT, rate_plan_id INTEGER NOT NULL,"
        + " room_type_id INTEGER NOT NULL, fecha TEXT NOT NULL, precio_cents INTEGER NOT NULL,"
        + " min_estancia INTEGER, max_estancia INTEGER, cerrado INTEGER NOT NULL DEFAULT 0,"
        + " UNIQUE(rate_plan_id,room_type_id,fecha))");
      s.execute("CREATE TABLE blocks(id INTEGER PRIMARY KEY AUTOINCREMENT, room_id INTEGER,"
        + " desde TEXT NOT NULL, hasta TEXT NOT NULL, motivo TEXT NOT NULL DEFAULT '')");
      // La búsqueda de disponibilidad consulta reservas y bloqueos; sin estas tablas, la consulta real falla.
      s.execute("CREATE TABLE reservations(id INTEGER PRIMARY KEY AUTOINCREMENT, codigo TEXT UNIQUE NOT NULL,"
        + " email TEXT NOT NULL, nombre TEXT NOT NULL DEFAULT '', llegada TEXT NOT NULL, salida TEXT NOT NULL,"
        + " huespedes INTEGER NOT NULL, estado TEXT NOT NULL, origen TEXT NOT NULL,"
        + " idempotencia TEXT UNIQUE NOT NULL, creado_en TEXT NOT NULL DEFAULT '')");
      s.execute("CREATE TABLE reservation_items(id INTEGER PRIMARY KEY AUTOINCREMENT, reservation_id INTEGER NOT NULL,"
        + " room_id INTEGER NOT NULL, desde TEXT NOT NULL, hasta TEXT NOT NULL)");
    }
    jdbc = new JdbcTemplate(dataSource);
    svc = new InventarioService(new InventarioRepository(jdbc), new TarifaRepository(jdbc),
      new co.hotel.reservas.SqliteTransactionExecutor(dataSource));
    tarifas = new TarifaService(new TarifaRepository(jdbc));
  }

  @Test
  @DisplayName("el hotel registra un tipo de habitación y sus habitaciones")
  void registraTipoYHabitaciones() {
    RoomType tipo = svc.crearTipo("DOBLE", "Habitación doble", 3);
    Habitacion h1 = svc.crearHabitacion("101", tipo.id(), "Habitación 101");
    Habitacion h2 = svc.crearHabitacion("102", tipo.id(), "Habitación 102");

    assertEquals(3, tipo.capacidadMax());
    assertEquals("101", h1.codigo());
    assertEquals(EstadoHabitacion.ACTIVA, h1.estado());
    assertEquals(2, svc.listarHabitaciones().size());
  }

  @Test
  @DisplayName("un código de habitación repetido se rechaza: el inventario no puede duplicarse")
  void codigoDeHabitacionRepetidoSeRechaza() {
    RoomType tipo = svc.crearTipo("DOBLE", "Habitación doble", 2);
    svc.crearHabitacion("101", tipo.id(), "Habitación 101");
    var ex = assertThrows(DatosInvalidosException.class,
      () -> svc.crearHabitacion("101", tipo.id(), "Otra"));
    assertTrue(ex.getMessage().contains("101"));
  }

  @Test
  @DisplayName("una capacidad menor que 1 se rechaza")
  void capacidadInvalidaSeRechaza() {
    assertThrows(DatosInvalidosException.class, () -> svc.crearTipo("X", "X", 0));
  }

  @Test
  @DisplayName("una habitación fuera de servicio desaparece de la oferta pero no del inventario")
  void habitacionFueraDeServicioNoSeOfrece() {
    RoomType tipo = svc.crearTipo("DOBLE", "Habitación doble", 2);
    Habitacion h = svc.crearHabitacion("101", tipo.id(), "Habitación 101");
    svc.crearHabitacion("102", tipo.id(), "Habitación 102");
    svc.cambiarEstado(h.id(), EstadoHabitacion.FUERA_DE_SERVICIO);

    assertEquals(2, svc.listarHabitaciones().size(), "sigue en el inventario");
    List<Habitacion> ofrecidas = svc.disponibles(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-03"));
    assertEquals(1, ofrecidas.size());
    assertEquals("102", ofrecidas.get(0).codigo());
  }

  @Test
  @DisplayName("un bloqueo de mantenimiento saca la habitación de la disponibilidad")
  void bloqueoSacaDeDisponibilidad() {
    RoomType tipo = svc.crearTipo("DOBLE", "Habitación doble", 2);
    Habitacion h1 = svc.crearHabitacion("101", tipo.id(), "Habitación 101");
    svc.crearHabitacion("102", tipo.id(), "Habitación 102");
    svc.bloquear(h1.id(), LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-05"), "Mantenimiento");

    List<Habitacion> libres = svc.disponibles(LocalDate.parse("2026-11-02"), LocalDate.parse("2026-11-04"));
    assertEquals(1, libres.size());
    assertEquals(h1.id() + 1, libres.get(0).id());
  }

  @Test
  @DisplayName("un bloqueo global (room_id nulo) saca todas las habitaciones")
  void bloqueoGlobalAfectaATodas() {
    RoomType tipo = svc.crearTipo("DOBLE", "Habitación doble", 2);
    svc.crearHabitacion("101", tipo.id(), "Habitación 101");
    svc.bloquearTodo(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-05"), "Cierre del hotel");

    assertTrue(svc.disponibles(LocalDate.parse("2026-11-02"), LocalDate.parse("2026-11-04")).isEmpty());
  }

  @Test
  @DisplayName("un bloqueo con fechas invertidas se rechaza")
  void bloqueoConFechasInvertidasSeRechaza() {
    RoomType tipo = svc.crearTipo("DOBLE", "Habitación doble", 2);
    Habitacion h = svc.crearHabitacion("101", tipo.id(), "Habitación 101");
    assertThrows(DatosInvalidosException.class, () -> svc.bloquear(h.id(),
      LocalDate.parse("2026-11-05"), LocalDate.parse("2026-11-01"), "x"));
  }

  @Test
  @DisplayName("las tarifas se registran por fecha y el total se calcula con la moneda del plan")
  void registraTarifasYCalculaTotal() {
    RoomType tipo = svc.crearTipo("DOBLE", "Habitación doble", 2);
    PlanTarifario plan = tarifas.crearPlan("PES", "Plan pesos", "COP");
    LocalDate desde = LocalDate.parse("2026-11-01");
    for (int i = 0; i < 3; i++) tarifas.fijarPrecio(plan, tipo.id(), desde.plusDays(i), 150_000);
    svc.crearHabitacion("101", tipo.id(), "Habitación 101");

    List<OpcionOferta> ofertas = svc.disponiblesConPrecio(desde, desde.plusDays(3), 2);
    assertEquals(1, ofertas.size());
    assertEquals(450_000, ofertas.get(0).totalCents(), "3 noches a 150.000");
    assertEquals("COP", ofertas.get(0).moneda());
  }

  @Test
  @DisplayName("sin tarifa configurada, no se ofrece la habitación: el hotel no ha fijado precios")
  void sinTarifaNoSeOfrece() {
    RoomType tipo = svc.crearTipo("DOBLE", "Habitación doble", 2);
    svc.crearHabitacion("101", tipo.id(), "Habitación 101");

    assertTrue(svc.disponiblesConPrecio(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-03"), 2).isEmpty(),
      "no se puede ofrecer un precio que nadie ha configurado");
    assertEquals(1, svc.disponibles(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-03")).size(),
      "pero la habitación sí está libre");
  }

  @Test
  @DisplayName("una noche sin tarifa deja la habitación fuera de la oferta de ese periodo")
  void unaNocheSinTarifaExcluyeLaOferta() {
    RoomType tipo = svc.crearTipo("DOBLE", "Habitación doble", 2);
    PlanTarifario plan = tarifas.crearPlan("PES", "Plan pesos", "COP");
    svc.crearHabitacion("101", tipo.id(), "Habitación 101");
    tarifas.fijarPrecio(plan, tipo.id(), LocalDate.parse("2026-11-01"), 150_000);

    assertTrue(svc.disponiblesConPrecio(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-03"), 2).isEmpty(),
      "falta la noche del 2 y la oferta no puede totalizarse");
    assertEquals(1, svc.disponiblesConPrecio(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-02"), 2).size());
  }

  @Test
  @DisplayName("más huéspedes que la capacidad del tipo no se ofrece")
  void capacidadDeHuespedesSeRespeta() {
    RoomType tipo = svc.crearTipo("DOBLE", "Habitación doble", 2);
    PlanTarifario plan = tarifas.crearPlan("PES", "Plan pesos", "COP");
    svc.crearHabitacion("101", tipo.id(), "Habitación 101");
    tarifas.fijarPrecio(plan, tipo.id(), LocalDate.parse("2026-11-01"), 150_000);

    assertTrue(svc.disponiblesConPrecio(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-03"), 5).isEmpty());
  }

  @Test
  @DisplayName("una restricción de estancia mínima se respeta")
  void restriccionEstanciaMinimaSeRespeta() {
    RoomType tipo = svc.crearTipo("DOBLE", "Habitación doble", 2);
    PlanTarifario plan = tarifas.crearPlan("PES", "Plan pesos", "COP");
    svc.crearHabitacion("101", tipo.id(), "Habitación 101");
    for (int i = 0; i < 4; i++) tarifas.fijarPrecio(plan, tipo.id(), LocalDate.parse("2026-11-01").plusDays(i), 150_000);
    tarifas.fijarMinimoEstancia(plan, tipo.id(), LocalDate.parse("2026-11-01"), 3);

    assertTrue(svc.disponiblesConPrecio(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-03"), 2).isEmpty(),
      "2 noches incumplen el mínimo de 3");
    assertEquals(1, svc.disponiblesConPrecio(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-04"), 2).size());
  }

  @Test
  @DisplayName("una noche cerrada por el hotel no se ofrece")
  void nocheCerradaNoSeOfrece() {
    RoomType tipo = svc.crearTipo("DOBLE", "Habitación doble", 2);
    PlanTarifario plan = tarifas.crearPlan("PES", "Plan pesos", "COP");
    svc.crearHabitacion("101", tipo.id(), "Habitación 101");
    tarifas.fijarPrecio(plan, tipo.id(), LocalDate.parse("2026-11-01"), 150_000);
    tarifas.fijarPrecio(plan, tipo.id(), LocalDate.parse("2026-11-02"), 150_000);
    tarifas.cerrarNoche(plan, tipo.id(), LocalDate.parse("2026-11-02"));

    assertTrue(svc.disponiblesConPrecio(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-03"), 2).isEmpty());
  }

  @Test
  @DisplayName("el hotel ve sus planes y las noches con precio del periodo que elija")
  void listaPlanesYNochesDelPeriodo() {
    RoomType tipo = svc.crearTipo("DOBLE", "Habitación doble", 2);
    PlanTarifario plan = tarifas.crearPlan("PES", "Plan pesos", "COP");
    svc.crearHabitacion("101", tipo.id(), "Habitación 101");
    tarifas.fijarPrecio(plan, tipo.id(), LocalDate.parse("2026-11-01"), 150_000);
    tarifas.fijarPrecio(plan, tipo.id(), LocalDate.parse("2026-11-02"), 180_000);
    tarifas.fijarPrecio(plan, tipo.id(), LocalDate.parse("2026-12-20"), 220_000);

    assertEquals(1, tarifas.listarPlanes().size());
    assertEquals("COP", tarifas.listarPlanes().get(0).moneda());

    List<TarifaRepository.TarifaNoche> noches = tarifas.nochesDe(plan.id(), tipo.id(),
      LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-05"));
    assertEquals(2, noches.size(), "solo las noches del periodo pedido");
    assertEquals(180_000, noches.get(1).precioCents());
  }

  @Test
  @DisplayName("una noche cerrada se devuelve marcada como cerrada, no como si no existiera")
  void nocheCerradaSeDevuelveMarcada() {
    RoomType tipo = svc.crearTipo("DOBLE", "Habitación doble", 2);
    PlanTarifario plan = tarifas.crearPlan("PES", "Plan pesos", "COP");
    tarifas.fijarPrecio(plan, tipo.id(), LocalDate.parse("2026-11-01"), 150_000);
    tarifas.cerrarNoche(plan, tipo.id(), LocalDate.parse("2026-11-01"));

    assertTrue(tarifas.nochesDe(plan.id(), tipo.id(),
      LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-02")).get(0).cerrado());
  }

  @Test
  @DisplayName("un periodo con las fechas invertidas se rechaza: el hotel vería una lista vacía sin explicación")
  void periodoInvertidoSeRechaza() {
    RoomType tipo = svc.crearTipo("DOBLE", "Habitación doble", 2);
    PlanTarifario plan = tarifas.crearPlan("PES", "Plan pesos", "COP");
    assertThrows(DatosInvalidosException.class, () -> tarifas.nochesDe(plan.id(), tipo.id(),
      LocalDate.parse("2026-11-05"), LocalDate.parse("2026-11-01")));
  }

  @Test
  @DisplayName("un periodo desmedido se rechaza en vez de recorrer toda la tabla")
  void periodoDesmedidoSeRechaza() {
    RoomType tipo = svc.crearTipo("DOBLE", "Habitación doble", 2);
    PlanTarifario plan = tarifas.crearPlan("PES", "Plan pesos", "COP");
    assertThrows(DatosInvalidosException.class, () -> tarifas.nochesDe(plan.id(), tipo.id(),
      LocalDate.parse("2026-01-01"), LocalDate.parse("2036-01-01")));
  }

  @Test
  @DisplayName("el calendario dice ocupada, libre, bloqueada y fuera de servicio, noche por noche")
  void calendarioDeOcupacionNocheAPorNoche() {
    RoomType tipo = svc.crearTipo("DOBLE", "Habitación doble", 2);
    Habitacion libre = svc.crearHabitacion("101", tipo.id(), "Habitación 101");
    Habitacion reservada = svc.crearHabitacion("102", tipo.id(), "Habitación 102");
    Habitacion bloqueada = svc.crearHabitacion("103", tipo.id(), "Habitación 103");
    Habitacion fuera = svc.crearHabitacion("104", tipo.id(), "Habitación 104");
    Habitacion cancelada = svc.crearHabitacion("105", tipo.id(), "Habitación 105");

    // Reserva confirmada en 102: del 1 al 3 ocupa las noches 1 y 2 (el 3 es el día de salida).
    reservar("H-CONFIRMADA", reservada.id(), "2026-12-01", "2026-12-03", "CONFIRMADA");
    // Una reserva cancelada no ocupa nada: es la trampa que hace que un calendario mienta.
    reservar("H-CANCELADA", cancelada.id(), "2026-12-01", "2026-12-03", "CANCELADA");
    // Bloqueo de mantenimiento en 103 del 2 al 3.
    svc.bloquear(bloqueada.id(), LocalDate.parse("2026-12-02"), LocalDate.parse("2026-12-03"),
      "Mantenimiento");
    svc.cambiarEstado(fuera.id(), EstadoHabitacion.FUERA_DE_SERVICIO);

    var calendario = svc.ocupacion(LocalDate.parse("2026-12-01"), LocalDate.parse("2026-12-04"));
    assertEquals(5, calendario.size(), "una fila por habitación del hotel");
    assertEquals(3, calendario.get(0).noches().size(), "noche 1, 2 y 3 del periodo");

    var libre101 = porCodigo(calendario, libre.codigo());
    assertEquals("LIBRE", libre101.noches().get(0).estado());
    assertEquals("LIBRE", libre101.noches().get(1).estado());

    var ocupada102 = porCodigo(calendario, reservada.codigo());
    assertEquals("OCUPADA", ocupada102.noches().get(0).estado());
    assertEquals("H-CONFIRMADA", ocupada102.noches().get(0).codigoReserva());
    assertEquals("OCUPADA", ocupada102.noches().get(1).estado(), "la noche 2 sigue reservada");
    assertEquals("LIBRE", ocupada102.noches().get(2).estado(), "el 3 es el día de salida: sale y queda libre");

    var bloqueada103 = porCodigo(calendario, bloqueada.codigo());
    assertEquals("LIBRE", bloqueada103.noches().get(0).estado());
    assertEquals("BLOQUEADA", bloqueada103.noches().get(1).estado());

    assertEquals("FUERA_DE_SERVICIO", porCodigo(calendario, fuera.codigo()).noches().get(0).estado());

    var cancelada105 = porCodigo(calendario, cancelada.codigo());
    assertEquals("LIBRE", cancelada105.noches().get(0).estado(),
      "una reserva cancelada no puede retener la habitación");
    assertNull(cancelada105.noches().get(0).codigoReserva());
  }

  @Test
  @DisplayName("un bloqueo del hotel entero (room_id nulo) aparece en todas las habitaciones")
  void bloqueoGlobalApareceEnTodas() {
    RoomType tipo = svc.crearTipo("DOBLE", "Habitación doble", 2);
    svc.crearHabitacion("101", tipo.id(), "Habitación 101");
    Habitacion otra = svc.crearHabitacion("102", tipo.id(), "Habitación 102");
    svc.bloquearTodo(LocalDate.parse("2026-12-01"), LocalDate.parse("2026-12-02"), "Cierre");

    var calendario = svc.ocupacion(LocalDate.parse("2026-12-01"), LocalDate.parse("2026-12-03"));
    assertEquals("BLOQUEADA", porCodigo(calendario, "101").noches().get(0).estado());
    assertEquals("BLOQUEADA", porCodigo(calendario, otra.codigo()).noches().get(0).estado());
    assertEquals("LIBRE", porCodigo(calendario, "101").noches().get(1).estado(),
      "el bloqueo termina el día 2");
  }

  @Test
  @DisplayName("un calendario con las fechas invertidas o desmedido se rechaza")
  void calendarioInvalidoSeRechaza() {
    assertThrows(DatosInvalidosException.class,
      () -> svc.ocupacion(LocalDate.parse("2026-12-04"), LocalDate.parse("2026-12-01")));
    assertThrows(DatosInvalidosException.class,
      () -> svc.ocupacion(LocalDate.parse("2026-01-01"), LocalDate.parse("2030-01-01")));
  }

  private InventarioService.OcupacionHabitacion porCodigo(
      java.util.List<InventarioService.OcupacionHabitacion> calendario, String codigo) {
    return calendario.stream().filter(c -> codigo.equals(c.codigo())).findFirst().orElseThrow();
  }

  private void reservar(String codigo, long roomId, String desde, String hasta, String estado) {
    jdbc.update("INSERT INTO reservations(codigo,email,nombre,llegada,salida,huespedes,estado,"
        + "origen,idempotencia,creado_en) VALUES(?,?,?,?,?,2,?,?,?,datetime('now'))",
      codigo, "hotel@ejemplo.com", "Huésped", desde, hasta, estado, "WEB", codigo);
    Long id = jdbc.queryForObject("SELECT id FROM reservations WHERE codigo=?", Long.class, codigo);
    jdbc.update("INSERT INTO reservation_items(reservation_id,room_id,desde,hasta) VALUES(?,?,?,?)",
      id, roomId, desde, hasta);
  }
}