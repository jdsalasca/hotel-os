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
        + " nombre TEXT NOT NULL, moneda TEXT NOT NULL, activo INTEGER NOT NULL DEFAULT 1,"
        + " descuento_pct INTEGER NOT NULL DEFAULT 0)");
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
    var tx = new co.hotel.reservas.SqliteTransactionExecutor(dataSource);
    svc = new InventarioService(new InventarioRepository(jdbc), new TarifaRepository(jdbc), tx);
    tarifas = new TarifaService(new TarifaRepository(jdbc), new InventarioRepository(jdbc), tx);
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
  @DisplayName("el detalle de una habitación libre trae sus noches con el plan")
  void detalleDeHabitacionLibreExiste() {
    RoomType tipo = svc.crearTipo("DOBLE", "Habitación doble", 2);
    PlanTarifario plan = tarifas.crearPlan("PES", "Plan pesos", "COP");
    LocalDate desde = LocalDate.parse("2026-11-01");
    for (int i = 0; i < 2; i++) tarifas.fijarPrecio(plan, tipo.id(), desde.plusDays(i), 150_000);
    Habitacion h = svc.crearHabitacion("101", tipo.id(), "Habitación 101");

    var detalle = svc.detalleOferta(h.id(), desde, desde.plusDays(2), 2);
    assertTrue(detalle.isPresent(), "libre y tarifada: hay detalle");
    assertEquals(2, detalle.orElseThrow().noches().size());
    assertEquals(300_000, detalle.orElseThrow().totalCents());
  }

  @Test
  @DisplayName("el detalle de una habitación ocupada no existe: es oferta vendible, no precio informativo")
  void detalleDeHabitacionOcupadaNoExiste() {
    RoomType tipo = svc.crearTipo("DOBLE", "Habitación doble", 2);
    PlanTarifario plan = tarifas.crearPlan("PES", "Plan pesos", "COP");
    LocalDate desde = LocalDate.parse("2026-11-01");
    for (int i = 0; i < 2; i++) tarifas.fijarPrecio(plan, tipo.id(), desde.plusDays(i), 150_000);
    Habitacion h = svc.crearHabitacion("101", tipo.id(), "Habitación 101");
    reservar("H-OCUPADA", h.id(), "2026-11-01", "2026-11-03", "CONFIRMADA");

    assertTrue(svc.detalleOferta(h.id(), desde, desde.plusDays(2), 2).isEmpty(),
      "con reserva vigente encima, el detalle no puede venderse");
  }

  @Test
  @DisplayName("el detalle de una habitación bloqueada no existe")
  void detalleDeHabitacionBloqueadaNoExiste() {
    RoomType tipo = svc.crearTipo("DOBLE", "Habitación doble", 2);
    PlanTarifario plan = tarifas.crearPlan("PES", "Plan pesos", "COP");
    LocalDate desde = LocalDate.parse("2026-11-01");
    for (int i = 0; i < 2; i++) tarifas.fijarPrecio(plan, tipo.id(), desde.plusDays(i), 150_000);
    Habitacion h = svc.crearHabitacion("101", tipo.id(), "Habitación 101");
    svc.bloquear(h.id(), LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-03"), "Obra");

    assertTrue(svc.detalleOferta(h.id(), desde, desde.plusDays(2), 2).isEmpty(),
      "bloqueada: no hay detalle aunque haya tarifa");
  }

  @Test
  @DisplayName("el calendario cuenta habitaciones distintas, no ofertas por plan")
  void calendarioCuentaHabitaciones() {
    RoomType tipo = svc.crearTipo("DOBLE", "Habitación doble", 2);
    PlanTarifario cop = tarifas.crearPlan("PES", "Plan pesos", "COP");
    PlanTarifario flex = tarifas.crearPlan("FLEX", "Flexible", "COP");
    LocalDate dia = LocalDate.parse("2026-11-01");
    tarifas.fijarPrecio(cop, tipo.id(), dia, 150_000);
    tarifas.fijarPrecio(flex, tipo.id(), dia, 140_000);
    svc.crearHabitacion("101", tipo.id(), "Habitación 101");

    var dias = svc.calendarioMensual(java.time.YearMonth.parse("2026-11"), 2);
    var noche = dias.stream().filter(d -> d.fecha().equals(dia)).findFirst().orElseThrow();
    assertEquals(1, noche.disponibles(), "una habitación con dos planes sigue siendo una");
  }

  @Test
  @DisplayName("el calendario agrupa el mínimo por moneda sin mezclarlas")
  void calendarioAgrupaMinimoPorMoneda() {
    RoomType tipo = svc.crearTipo("DOBLE", "Habitación doble", 2);
    PlanTarifario cop = tarifas.crearPlan("PES", "Plan pesos", "COP");
    PlanTarifario usd = tarifas.crearPlan("USD", "Plan dólares", "USD");
    LocalDate dia = LocalDate.parse("2026-11-01");
    tarifas.fijarPrecio(cop, tipo.id(), dia, 100_000);
    tarifas.fijarPrecio(usd, tipo.id(), dia, 5_000);
    svc.crearHabitacion("101", tipo.id(), "Habitación 101");

    var dias = svc.calendarioMensual(java.time.YearMonth.parse("2026-11"), 2);
    var noche = dias.stream().filter(d -> d.fecha().equals(dia)).findFirst().orElseThrow();
    assertEquals(1, noche.disponibles());
    assertEquals(2, noche.precios().size());
    assertEquals("COP", noche.precios().get(0).moneda());
    assertEquals(100_000, noche.precios().get(0).desdeCents());
    assertEquals("USD", noche.precios().get(1).moneda());
    assertEquals(5_000, noche.precios().get(1).desdeCents());
  }

  @Test
  @DisplayName("un plan con 20% de descuento rebaja el total y dice el original")
  void planConDescuentoRebajaElTotal() {
    RoomType tipo = svc.crearTipo("DOBLE", "Habitación doble", 2);
    PlanTarifario plan = tarifas.crearPlan("OFE", "Oferta", "COP", 20);
    assertEquals(20, plan.descuentoPct());
    LocalDate desde = LocalDate.parse("2026-11-01");
    for (int i = 0; i < 2; i++) tarifas.fijarPrecio(plan, tipo.id(), desde.plusDays(i), 150_000);
    svc.crearHabitacion("101", tipo.id(), "Habitación 101");

    List<OpcionOferta> ofertas = svc.disponiblesConPrecio(desde, desde.plusDays(2), 2);
    assertEquals(1, ofertas.size());
    assertEquals(240_000, ofertas.get(0).totalCents(), "300.000 menos el 20%");
    assertEquals(300_000, ofertas.get(0).totalSinDescuentoCents());
    assertEquals(20, ofertas.get(0).descuentoPct());
  }

  @Test
  @DisplayName("el descuento se redondea al céntimo sobre el total, no por noche")
  void descuentoSeRedondeaAlCentimo() {
    RoomType tipo = svc.crearTipo("DOBLE", "Habitación doble", 2);
    PlanTarifario plan = tarifas.crearPlan("OFE", "Oferta", "COP", 10);
    LocalDate desde = LocalDate.parse("2026-11-01");
    tarifas.fijarPrecio(plan, tipo.id(), desde, 199);
    svc.crearHabitacion("101", tipo.id(), "Habitación 101");

    List<OpcionOferta> ofertas = svc.disponiblesConPrecio(desde, desde.plusDays(1), 2);
    assertEquals(1, ofertas.size());
    assertEquals(179, ofertas.get(0).totalCents(), "199 menos el 10% = 179.1, al céntimo 179");
    assertEquals(199, ofertas.get(0).totalSinDescuentoCents());
  }

  @Test
  @DisplayName("descuento fuera de 0-100 se rechaza con motivo, al crear y al modificar")
  void descuentoInvalidoSeRechaza() {
    assertThrows(DatosInvalidosException.class, () -> tarifas.crearPlan("MAL", "Malo", "COP", 150));
    PlanTarifario plan = tarifas.crearPlan("OK", "Correcto", "COP");
    assertEquals(0, plan.descuentoPct(), "sin descuento declarado no hay rebaja");
    assertThrows(DatosInvalidosException.class, () -> tarifas.fijarDescuento(plan.id(), -5));
    tarifas.fijarDescuento(plan.id(), 15);
    assertEquals(15, tarifas.planPorId(plan.id()).descuentoPct());
  }

  @Test
  @DisplayName("precio válido con mínimo inválido: error y precio intacto, nada a medias")
  void operacionRechazadaNoCambiaNada() {
    RoomType tipo = svc.crearTipo("DOBLE", "Habitación doble", 2);
    PlanTarifario plan = tarifas.crearPlan("PES", "Plan pesos", "COP");
    LocalDate fecha = LocalDate.parse("2026-11-01");
    tarifas.fijarPrecio(plan, tipo.id(), fecha, 150_000);

    assertThrows(DatosInvalidosException.class, () ->
      tarifas.fijarNoche(plan.id(), tipo.id(), fecha, 200_000L, 0, null, null));
    var noche = tarifas.nochesDe(plan.id(), tipo.id(), fecha, fecha.plusDays(1)).get(0);
    assertEquals(150_000, noche.precioCents(), "el precio anterior sigue intacto");
  }

  @Test
  @DisplayName("precio negativo: error sin escribir nada")
  void precioNegativoEsError() {
    RoomType tipo = svc.crearTipo("DOBLE", "Habitación doble", 2);
    PlanTarifario plan = tarifas.crearPlan("PES", "Plan pesos", "COP");

    assertThrows(DatosInvalidosException.class, () ->
      tarifas.fijarNoche(plan.id(), tipo.id(), LocalDate.parse("2026-11-01"), -100L, null, null, null));
    assertTrue(tarifas.nochesDe(plan.id(), tipo.id(),
      LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-02")).isEmpty());
  }

  @Test
  @DisplayName("mínimo mayor que máximo se rechaza antes de tocar nada")
  void minimoMayorQueMaximoSeRechaza() {
    RoomType tipo = svc.crearTipo("DOBLE", "Habitación doble", 2);
    PlanTarifario plan = tarifas.crearPlan("PES", "Plan pesos", "COP");

    assertThrows(DatosInvalidosException.class, () ->
      tarifas.fijarNoche(plan.id(), tipo.id(), LocalDate.parse("2026-11-01"), 150_000L, 5, 3, null));
  }

  @Test
  @DisplayName("restricción sobre noche inexistente sin precio: error, no éxito silencioso")
  void restriccionSinNocheEsError() {
    RoomType tipo = svc.crearTipo("DOBLE", "Habitación doble", 2);
    PlanTarifario plan = tarifas.crearPlan("PES", "Plan pesos", "COP");

    assertThrows(DatosInvalidosException.class, () ->
      tarifas.fijarNoche(plan.id(), tipo.id(), LocalDate.parse("2026-11-01"), null, 2, null, null));
  }

  @Test
  @DisplayName("editar el precio de una noche cerrada no la reabre")
  void editarPrecioNoReabreNocheCerrada() {
    RoomType tipo = svc.crearTipo("DOBLE", "Habitación doble", 2);
    PlanTarifario plan = tarifas.crearPlan("PES", "Plan pesos", "COP");
    LocalDate fecha = LocalDate.parse("2026-11-01");
    tarifas.fijarPrecio(plan, tipo.id(), fecha, 150_000);
    tarifas.cerrarNoche(plan, tipo.id(), fecha);

    var guardada = tarifas.fijarNoche(plan.id(), tipo.id(), fecha, 180_000L, null, null, null);
    assertEquals(180_000, guardada.precioCents());
    assertTrue(guardada.cerrado(), "el cerrado se conserva si no se dice nada");
  }

  @Test
  @DisplayName("tipo o plan inexistente: error sin escribir")
  void referenciasInexistentesSonError() {
    assertThrows(DatosInvalidosException.class, () ->
      tarifas.fijarNoche(9999L, 1L, LocalDate.parse("2026-11-01"), 150_000L, null, null, null));
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
  @DisplayName("con dos planes con tarifa completa se ofrecen los dos, no solo el primero")
  void dosPlanesCompletosDanDosOfertas() {
    RoomType tipo = svc.crearTipo("DOBLE", "Habitación doble", 2);
    PlanTarifario flexible = tarifas.crearPlan("FLEX", "Flexible", "COP");
    PlanTarifario promo = tarifas.crearPlan("PROMO", "Promo", "COP", 10);
    LocalDate desde = LocalDate.parse("2026-11-01");
    for (int i = 0; i < 2; i++) {
      tarifas.fijarPrecio(flexible, tipo.id(), desde.plusDays(i), 150_000);
      tarifas.fijarPrecio(promo, tipo.id(), desde.plusDays(i), 150_000);
    }
    svc.crearHabitacion("101", tipo.id(), "Habitación 101");

    List<OpcionOferta> ofertas = svc.disponiblesConPrecio(desde, desde.plusDays(2), 2);
    assertEquals(2, ofertas.size(), "una oferta por plan válido, para que el huésped elija");
    assertTrue(ofertas.stream().anyMatch(o -> o.plan().codigo().equals("FLEX")
      && o.totalCents() == 300_000));
    assertTrue(ofertas.stream().anyMatch(o -> o.plan().codigo().equals("PROMO")
      && o.totalCents() == 270_000));
  }

  @Test
  @DisplayName("el plan sin tarifa completa no se ofrece aunque otro sí la tenga")
  void planIncompletoNoSeOfrece() {
    RoomType tipo = svc.crearTipo("DOBLE", "Habitación doble", 2);
    PlanTarifario completo = tarifas.crearPlan("FULL", "Completo", "COP");
    PlanTarifario cojo = tarifas.crearPlan("COJO", "Cojo", "COP");
    LocalDate desde = LocalDate.parse("2026-11-01");
    for (int i = 0; i < 2; i++) tarifas.fijarPrecio(completo, tipo.id(), desde.plusDays(i), 150_000);
    tarifas.fijarPrecio(cojo, tipo.id(), desde, 100_000);
    svc.crearHabitacion("101", tipo.id(), "Habitación 101");

    List<OpcionOferta> ofertas = svc.disponiblesConPrecio(desde, desde.plusDays(2), 2);
    assertEquals(1, ofertas.size());
    assertEquals("FULL", ofertas.get(0).plan().codigo());
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

  @Test
  @DisplayName("un tipo sin nombre se rechaza con 400, no con NPE")
  void tipoSinNombreSeRechaza() {
    assertThrows(DatosInvalidosException.class, () -> svc.crearTipo("DOBLE", null, 2));
    assertThrows(DatosInvalidosException.class, () -> svc.crearTipo("DOBLE", "   ", 2));
  }

  @Test
  @DisplayName("un código de tipo repetido se rechaza con el código en el mensaje")
  void codigoDeTipoRepetidoSeRechaza() {
    svc.crearTipo("DOBLE", "Habitación doble", 2);
    var ex = assertThrows(DatosInvalidosException.class,
      () -> svc.crearTipo("DOBLE", "Otra doble", 2));
    assertTrue(ex.getMessage().contains("DOBLE"));
  }

  @Test
  @DisplayName("una habitación con tipo inexistente se rechaza, no deja huérfana ni 500")
  void habitacionConTipoInexistenteSeRechaza() {
    var ex = assertThrows(DatosInvalidosException.class,
      () -> svc.crearHabitacion("101", 999999L, "Habitación 101"));
    assertTrue(ex.getMessage().contains("tipo"));
  }

  @Test
  @DisplayName("la moneda en minúsculas se normaliza a ISO mayúsculas")
  void monedaEnMinusculasSeNormaliza() {
    PlanTarifario plan = tarifas.crearPlan("FLEX", "Flexible", "cop");
    assertEquals("COP", plan.moneda());
  }

  @Test
  @DisplayName("una moneda que no es ISO se rechaza aunque tenga 3 letras")
  void monedaNoIsoSeRechaza() {
    assertThrows(DatosInvalidosException.class,
      () -> tarifas.crearPlan("FLEX", "Flexible", "COL"));
  }

  @Test
  @DisplayName("un código de plan repetido se rechaza con el código en el mensaje")
  void codigoDePlanRepetidoSeRechaza() {
    tarifas.crearPlan("FLEX", "Flexible", "COP");
    var ex = assertThrows(DatosInvalidosException.class,
      () -> tarifas.crearPlan("FLEX", "Otro flexible", "COP"));
    assertTrue(ex.getMessage().contains("FLEX"));
  }

  @Test
  @DisplayName("un lote con una fila inválida no escribe nada: ni la válida se guarda")
  void loteConFilaInvalidaNoEscribeNada() {
    RoomType tipo = svc.crearTipo("DOBLE", "Habitación doble", 2);
    PlanTarifario plan = tarifas.crearPlan("FLEX", "Flexible", "COP");
    var cambios = java.util.List.of(
      new TarifaService.CambioNoche(LocalDate.parse("2026-11-01"), 10000L, null, null, null),
      new TarifaService.CambioNoche(LocalDate.parse("2026-11-02"), 10000L, 0, null, null));
    assertThrows(DatosInvalidosException.class, () -> tarifas.aplicarLote(plan.id(), tipo.id(), cambios));
    assertTrue(tarifas.nocheDe(plan.id(), tipo.id(), LocalDate.parse("2026-11-01")).isEmpty(),
      "la fila válida tampoco se guardó: el lote es atómico");
  }

  @Test
  @DisplayName("un lote válido guarda todas las noches en una sola operación")
  void loteValidoGuardaTodo() {
    RoomType tipo = svc.crearTipo("DOBLE", "Habitación doble", 2);
    PlanTarifario plan = tarifas.crearPlan("FLEX", "Flexible", "COP");
    var guardadas = tarifas.aplicarLote(plan.id(), tipo.id(), java.util.List.of(
      new TarifaService.CambioNoche(LocalDate.parse("2026-11-01"), 10000L, null, null, null),
      new TarifaService.CambioNoche(LocalDate.parse("2026-11-02"), 12000L, 2, null, null),
      new TarifaService.CambioNoche(LocalDate.parse("2026-11-03"), 12000L, null, null, true)));
    assertEquals(3, guardadas.size());
    assertEquals(12000L, tarifas.nocheDe(plan.id(), tipo.id(), LocalDate.parse("2026-11-02")).orElseThrow().precioCents());
    assertTrue(tarifas.nocheDe(plan.id(), tipo.id(), LocalDate.parse("2026-11-03")).orElseThrow().cerrado());
  }

  @Test
  @DisplayName("la previa no escribe: valida y reporta por fila")
  void previaNoEscribe() {
    RoomType tipo = svc.crearTipo("DOBLE", "Habitación doble", 2);
    PlanTarifario plan = tarifas.crearPlan("FLEX", "Flexible", "COP");
    var previa = tarifas.previsualizarLote(plan.id(), tipo.id(), java.util.List.of(
      new TarifaService.CambioNoche(LocalDate.parse("2026-11-01"), 10000L, null, null, null),
      new TarifaService.CambioNoche(LocalDate.parse("2026-11-02"), 10000L, 0, null, null)));
    assertFalse(previa.lista());
    assertEquals(2, previa.filas().size());
    assertTrue(previa.filas().get(0).valida());
    assertFalse(previa.filas().get(1).valida());
    assertTrue(previa.filas().get(1).motivo().contains("mínima"));
    assertTrue(tarifas.nocheDe(plan.id(), tipo.id(), LocalDate.parse("2026-11-01")).isEmpty(),
      "la previa no guarda nada");
  }

  @Test
  @DisplayName("un lote vacío o desmedido se rechaza sin tocar nada")
  void loteVacioODesmedidoSeRechaza() {
    RoomType tipo = svc.crearTipo("DOBLE", "Habitación doble", 2);
    PlanTarifario plan = tarifas.crearPlan("FLEX", "Flexible", "COP");
    assertThrows(DatosInvalidosException.class,
      () -> tarifas.aplicarLote(plan.id(), tipo.id(), java.util.List.of()));
    var enorme = new java.util.ArrayList<TarifaService.CambioNoche>();
    for (int i = 0; i < 400; i++)
      enorme.add(new TarifaService.CambioNoche(LocalDate.parse("2026-01-01").plusDays(i), 10000L, null, null, null));
    assertThrows(DatosInvalidosException.class, () -> tarifas.aplicarLote(plan.id(), tipo.id(), enorme));
  }

  @Test
  @DisplayName("el lote conserva el cierre al cambiar solo el precio de una noche cerrada")
  void loteConservaCierreAlCambiarPrecio() {
    RoomType tipo = svc.crearTipo("DOBLE", "Habitación doble", 2);
    PlanTarifario plan = tarifas.crearPlan("FLEX", "Flexible", "COP");
    tarifas.fijarNoche(plan.id(), tipo.id(), LocalDate.parse("2026-11-01"), 10000L, null, null, true);
    tarifas.aplicarLote(plan.id(), tipo.id(), java.util.List.of(
      new TarifaService.CambioNoche(LocalDate.parse("2026-11-01"), 15000L, null, null, null)));
    var noche = tarifas.nocheDe(plan.id(), tipo.id(), LocalDate.parse("2026-11-01")).orElseThrow();
    assertEquals(15000L, noche.precioCents());
    assertTrue(noche.cerrado(), "cambiar el precio no reabre la noche");
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