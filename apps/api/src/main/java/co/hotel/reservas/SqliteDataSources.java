package co.hotel.reservas;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import org.sqlite.SQLiteConfig.TransactionMode;

/**
 * Fábrica del DataSource de SQLite con el modo de concurrencia correcto para el nivel previsto.
 *
 * Explica el resto de la arquitectura transaccional: SQLite abre transactions diferidas por
 * defecto, así que dos escrituras concurrentes pueden pasar la lectura de disponibilidad y
 * fallar al insertar. Con TransactionMode.IMMEDIATE el BEGIN toma el candado de escritura desde
 * el inicio, que es lo que hace segura la comprobación de solapes. La instancia única con un solo
 * escritor es un límite asumido, no un detalle de configuración.
 */
public final class SqliteDataSources {

  private SqliteDataSources() {}

  public static DataSource paraRuta(String rutaSqlite) {
    SQLiteConfig config = new SQLiteConfig();
    config.setJournalMode(SQLiteConfig.JournalMode.WAL);
    config.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
    config.setBusyTimeout(5000);
    config.enforceForeignKeys(true);
    config.setTransactionMode(TransactionMode.IMMEDIATE);
    SQLiteDataSource ds = new SQLiteDataSource(config);
    ds.setUrl("jdbc:sqlite:" + rutaSqlite);
    return ds;
  }

  /** Configuración equivalente sobre una conexión ya abierta (tests y herramientas). */
  public static void configurar(Connection conexion) throws SQLException {
    try (var st = conexion.createStatement()) {
      st.execute("PRAGMA journal_mode=WAL");
      st.execute("PRAGMA busy_timeout=5000");
      st.execute("PRAGMA foreign_keys=ON");
    }
  }
}