package co.hotel.config;

import java.sql.SQLException;
import javax.sql.DataSource;

/** Comprueba las relaciones SQLite después de aplicar todas las migraciones Flyway. */
public final class VerificarIntegridadForanea {

  private VerificarIntegridadForanea() {}

  public static void verificar(DataSource dataSource) {
    try (var conexion = dataSource.getConnection();
        var consulta = conexion.createStatement();
        var violaciones = consulta.executeQuery("PRAGMA foreign_key_check")) {
      if (violaciones.next()) {
        throw new IllegalStateException(
          "SQLite encontró una relación huérfana después de Flyway: tabla="
            + violaciones.getString("table") + ", fila=" + violaciones.getString("rowid")
            + ", tabla referenciada=" + violaciones.getString("parent")
            + ", clave=" + violaciones.getString("fkid"));
      }
    } catch (SQLException e) {
      throw new IllegalStateException(
        "no se pudo comprobar la integridad de las claves foráneas después de Flyway", e);
    }
  }
}
