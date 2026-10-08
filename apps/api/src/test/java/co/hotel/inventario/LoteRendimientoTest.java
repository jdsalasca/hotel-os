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
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * El lote de tarifas pedía cada noche por separado y la resolvía dos veces: con 10 noches
 * eran 22 lecturas para previsualizar. Las noches del rango viajan una vez.
 */
class LoteRendimientoTest {

  private ContadorConsultas.Contador contador;
  private TarifaService tarifas;
  private long planId;
  private long tipoId;

  @BeforeEach
  void setUp() throws Exception {
    Path db = Files.createTempFile("hotel-lote-rend-", ".sqlite3");
    Files.delete(db);
    DataSource base = SqliteDataSources.paraRuta(db.toAbsolutePath().toString());
    try (Connection c = base.getConnection(); Statement s = c.createStatement()) {
      s.execute("CREATE TABLE room_types(id INTEGER PRIMARY KEY AUTOINCREMENT, codigo TEXT UNIQUE NOT NULL,"
        + " nombre TEXT NOT NULL, capacidad_max INTEGER NOT NULL)");
      s.execute("CREATE TABLE rate_plans(id INTEGER PRIMARY KEY AUTOINCREMENT, codigo TEXT UNIQUE NOT NULL,"
        + " nombre TEXT NOT NULL, moneda TEXT NOT NULL, activo INTEGER NOT NULL DEFAULT 1,"
        + " descuento_pct INTEGER NOT NULL DEFAULT 0)");
      s.execute("CREATE TABLE rates(id INTEGER PRIMARY KEY AUTOINCREMENT, rate_plan_id INTEGER NOT NULL,"
        + " room_type_id INTEGER NOT NULL, fecha TEXT NOT NULL, precio_cents INTEGER NOT NULL,"
        + " min_estancia INTEGER, max_estancia INTEGER, cerrado INTEGER NOT NULL DEFAULT 0,"
        + " UNIQUE(rate_plan_id,room_type_id,fecha))");
    }
    contador = ContadorConsultas.envolver(base);
    JdbcTemplate jdbc = new JdbcTemplate(contador.fuente());
    var tx = new SqliteTransactionExecutor(contador.fuente());
    var inventario = new InventarioService(new InventarioRepository(jdbc), new TarifaRepository(jdbc), tx);
    tarifas = new TarifaService(new TarifaRepository(jdbc), new InventarioRepository(jdbc), tx);
    tipoId = inventario.crearTipo("DOBLE", "Habitación doble", 2).id();
    planId = tarifas.crearPlan("PES", "Plan pesos", "COP").id();
    contador.reiniciar();
  }

  private List<TarifaService.CambioNoche> diezNoches() {
    List<TarifaService.CambioNoche> cambios = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      cambios.add(new TarifaService.CambioNoche(LocalDate.parse("2026-11-01").plusDays(i),
        100_000L + i, null, null, null));
    }
    return cambios;
  }

  @Test
  @DisplayName("previsualizar diez noches lee el rango una vez, no una por noche")
  void previaLeeElRangoUnaVez() {
    var previa = tarifas.previsualizarLote(planId, tipoId, diezNoches());

    assertTrue(previa.lista());
    assertEquals(10, previa.filas().size());
    assertTrue(contador.consultas() <= 4,
      "plan + tipo + rango, fue " + contador.consultas());
  }

  @Test
  @DisplayName("aplicar no resuelve dos veces ni relee lo que acaba de guardar")
  void aplicarSinDobleResolucion() {
    var guardadas = tarifas.aplicarLote(planId, tipoId, diezNoches());

    assertEquals(10, guardadas.size());
    assertEquals(100_000L, guardadas.get(0).precioCents());
    assertTrue(contador.consultas() <= 16,
      "3 lecturas + 10 escrituras, fue " + contador.consultas());
  }
}
