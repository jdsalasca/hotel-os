package co.hotel.reservas;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteConfig.TransactionMode;
import org.sqlite.SQLiteDataSource;

/**
 * Fábrica del DataSource de SQLite con el modo de concurrencia correcto para el nivel previsto.
 *
 * Acepta la ruta con o sin el prefijo "jdbc:sqlite:": JDBC_URL llega con prefijo en producción y
 * sin él en las pruebas, y duplicarlo hace que SQLite intente abrir un archivo llamado
 * "jdbc:sqlite:..." que no existe (SQLITE_CANTOPEN).
 *
 * Explica el resto de la arquitectura transaccional: SQLite abre transactions diferidas por
 * defecto, así que dos escrituras concurrentes pueden pasar ambas la lectura de disponibilidad y
 * fallar al insertar. Con TransactionMode.IMMEDIATE el BEGIN toma el candado de escritura desde
 * el inicio, que es lo que hace segura la comprobación de solapes. La instancia única con un solo
 * escritor es un límite asumido, no un detalle de configuración.
 */
public final class SqliteDataSources {
  private static final String PREFIJO = "jdbc:sqlite:";

  private SqliteDataSources() {}

  public static DataSource paraRuta(String rutaSqlite) {
    String ruta = sinPrefijo(rutaSqlite);
    if (ruta.isBlank())
      throw new IllegalArgumentException("falta la ruta del archivo SQLite (JDBC_URL)");

    crearDirectorios(ruta);

    SQLiteConfig config = new SQLiteConfig();
    config.setJournalMode(SQLiteConfig.JournalMode.WAL);
    config.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
    config.setBusyTimeout(5000);
    config.enforceForeignKeys(true);
    config.setTransactionMode(TransactionMode.IMMEDIATE);

    SQLiteDataSource ds = new SQLiteDataSource(config);
    ds.setUrl(PREFIJO + ruta);
    return ds;
  }

  /** "jdbc:sqlite:/data/x" y "/data/x" significan lo mismo. */
  static String sinPrefijo(String ruta) {
    if (ruta == null) return "";
    String v = ruta.trim();
    return v.startsWith(PREFIJO) ? v.substring(PREFIJO.length()) : v;
  }

  /** El volumen de Docker puede venir vacío: sin directorios, SQLite no puede crear el archivo. */
  private static void crearDirectorios(String ruta) {
    Path destino = Paths.get(ruta).toAbsolutePath();
    Path padre = destino.getParent();
    if (padre == null) return;
    try {
      Files.createDirectories(padre);
    } catch (IOException e) {
      throw new IllegalStateException("no se pudo crear el directorio " + padre + ": " + e.getMessage(), e);
    }
    exigirEscritura(padre);
  }

  /**
   * El proceso corre sin privilegios desde la ronda 19. Un volumen creado por una imagen anterior
   * (que corría como root) deja entonces de poder abrirse, y SQLite responde
   * SQLITE_READONLY_DIRECTORY dentro de un stack trace de Spring que no dice nada útil. Se comprueba
   * aquí, donde el mensaje todavía puede ser accionable.
   */
  private static void exigirEscritura(Path padre) {
    if (Files.isWritable(padre)) return;
    throw new IllegalStateException(
      "no se puede escribir en " + padre + ", así que SQLite no abrirá la base.\n"
        + "Si el volumen lo creó una imagen anterior que corría como root, hay que corregir el\n"
        + "propietario una vez antes de arrancar:\n"
        + "  docker run --rm -v NOMBRE_VOLUMEN:/data alpine chown -R 100:101 /data\n"
        + "Los números son los del usuario `hotel` de la imagen. Ver docs/operations/despliegue-respaldos.md.");
  }

  /** Configuración equivalente sobre una conexión ya abierta (herramientas y diagnóstico). */
  public static void configurar(Connection conexion) throws SQLException {
    try (var st = conexion.createStatement()) {
      st.execute("PRAGMA journal_mode=WAL");
      st.execute("PRAGMA busy_timeout=5000");
      st.execute("PRAGMA foreign_keys=ON");
    }
  }
}