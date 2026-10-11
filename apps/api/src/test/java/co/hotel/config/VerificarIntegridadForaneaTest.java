package co.hotel.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.hotel.reservas.SqliteDataSources;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class VerificarIntegridadForaneaTest {

  @Test
  void laConexionDeMigracionesNoAplicaCascadasYLaConexionDeLaApiDetectaHuerfanos()
      throws Exception {
    Path archivo = Files.createTempFile("integridad-foranea", ".db");
    DataSource api = SqliteDataSources.paraRuta(archivo.toString());
    DataSource migraciones = SqliteDataSources.paraMigraciones(archivo.toString());
    try {
      try (Connection conexion = api.getConnection(); var sentencia = conexion.createStatement()) {
        assertEquals(1, pragma(conexion, "foreign_keys"));
        sentencia.executeUpdate("CREATE TABLE parent (id INTEGER PRIMARY KEY)");
        sentencia.executeUpdate(
          "CREATE TABLE child (id INTEGER PRIMARY KEY, parent_id INTEGER REFERENCES parent(id))");
      }

      try (Connection conexion = migraciones.getConnection();
          var sentencia = conexion.createStatement()) {
        assertEquals(0, pragma(conexion, "foreign_keys"));
        assertEquals(1, sentencia.executeUpdate("INSERT INTO child(id,parent_id) VALUES(1,99)"));
      }

      IllegalStateException error = assertThrows(
        IllegalStateException.class, () -> VerificarIntegridadForanea.verificar(api));
      assertTrue(error.getMessage().contains("tabla=child"));
      assertTrue(error.getMessage().contains("tabla referenciada=parent"));
    } finally {
      Files.deleteIfExists(archivo);
      Files.deleteIfExists(Path.of(archivo + "-wal"));
      Files.deleteIfExists(Path.of(archivo + "-shm"));
    }
  }

  private static int pragma(Connection conexion, String nombre) throws Exception {
    try (var consulta = conexion.createStatement().executeQuery("PRAGMA " + nombre)) {
      if (!consulta.next()) throw new AssertionError("PRAGMA sin resultado: " + nombre);
      return consulta.getInt(1);
    }
  }
}
