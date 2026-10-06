package co.hotel.reservas;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * La fábrica del DataSource tiene que entender las dos formas en que llega la ruta: con y sin el
 * prefijo jdbc:sqlite:.
 *
 * El bug que fija este test: JDBC_URL llega como "jdbc:sqlite:/data/hotel.sqlite3" y, si la
 * fábrica añade el prefijo otra vez, SQLite intenta abrir "jdbc:sqlite:jdbc:sqlite:/data/..." y
 * falla con SQLITE_CANTOPEN. En desarrollo funcionaba porque las pruebas pasaban una ruta limpia.
 */
class SqliteDataSourcesTest {

  @ParameterizedTest(name = "ruta \"{0}\" abre la base")
  @ValueSource(strings = { "/tmp/directa.sqlite3", "jdbc:sqlite:/tmp/directa.sqlite3" })
  @DisplayName("acepta la ruta con o sin prefijo jdbc:sqlite:")
  void aceptaLasDosFormasDeRuta(String ruta) throws Exception {
    Path destino = Files.createTempDirectory("sqliteds").resolve("db.sqlite3");
    try (var c = SqliteDataSources.paraRuta(ruta.replace("/tmp/directa.sqlite3",
      destino.toAbsolutePath().toString())).getConnection()) {
      c.createStatement().execute("CREATE TABLE prueba(id INTEGER)");
    }
    assertTrue(Files.exists(destino), "debe crear el archivo en la ruta indicada");
    Files.deleteIfExists(destino);
  }

  @Test
  @DisplayName("crea los directorios intermedios si no existen")
  void creaDirectoriosIntermedios() throws Exception {
    Path base = Files.createTempDirectory("sqliteds-nested");
    Path destino = base.resolve("a/b/c/db.sqlite3");
    try (var c = SqliteDataSources.paraRuta(destino.toAbsolutePath().toString()).getConnection()) {
      c.createStatement().execute("CREATE TABLE prueba(id INTEGER)");
    }
    assertTrue(Files.exists(destino));
  }

  @Test
  @DisplayName("la conexión usa WAL y busy_timeout: los dos ajustes que sostienen la concurrencia")
  void aplicaWalYBusyTimeout() throws Exception {
    Path destino = Files.createTempFile("sqliteds-wal", ".sqlite3");
    try (var c = SqliteDataSources.paraRuta(destino.toAbsolutePath().toString()).getConnection();
        var st = c.createStatement()) {
      try (var rs = st.executeQuery("PRAGMA journal_mode")) {
        assertTrue(rs.next());
        assertEquals("wal", rs.getString(1).toLowerCase());
      }
      try (var rs = st.executeQuery("PRAGMA busy_timeout")) {
        assertTrue(rs.next());
        assertEquals(5000, rs.getInt(1));
      }
    }
  }

  @Test
  @DisplayName("una ruta vacía falla con un mensaje claro, no con un error de bajo nivel")
  void rutaVaciaFallaConMensajeClaro() {
    assertThrows(IllegalArgumentException.class, () -> SqliteDataSources.paraRuta(""));
  }

  @Test
  @DisplayName("la ruta relativa se resuelve contra el directorio de trabajo")
  void aceptaRutaRelativa() {
    assertDoesNotThrow(() -> SqliteDataSources.paraRuta("./data/hotel.sqlite3"));
  }

  @Test
  @DisplayName("el nombre del archivo no cambia: el de la ruta indicada")
  void respetaElNombreDelArchivo() throws Exception {
    Path destino = Files.createTempDirectory("sqliteds-nombre").resolve("hotel.sqlite3");
    try (var c = SqliteDataSources.paraRuta(destino.toAbsolutePath().toString()).getConnection()) {
      c.createStatement().execute("CREATE TABLE t(id INTEGER)");
    }
    assertEquals(List.of("hotel.sqlite3"), Files.list(destino.getParent())
      .filter(p -> p.getFileName().toString().startsWith("hotel")).map(p -> p.getFileName().toString()).toList());
  }
}