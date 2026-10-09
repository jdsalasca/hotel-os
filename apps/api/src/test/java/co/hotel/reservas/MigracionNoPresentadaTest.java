package co.hotel.reservas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

class MigracionNoPresentadaTest {

  @Test
  void alAmpliarElCheckConservaReservasYRegistrosRelacionados() throws Exception {
    Path archivo = Files.createTempFile("migracion-no-presentada", ".db");
    DataSource dataSource = SqliteDataSources.paraRuta(archivo.toString());
    try {
      Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .target(MigrationVersion.fromVersion("18"))
        .load()
        .migrate();

      int reservaId;
      try (Connection conexion = dataSource.getConnection()) {
        assertEquals(1, pragma(conexion, "foreign_keys"), "la conexión debe reflejar producción");
        conexion.createStatement().executeUpdate("""
          INSERT INTO reservations(codigo,email,nombre,llegada,salida,huespedes,estado,origen,
            idempotencia,creado_en,total_cents,moneda)
          VALUES('H-MIGRA','huesped@example.test','Huésped','2026-10-10','2026-10-12',1,
            'CONFIRMADA','WEB','clave-migra','2026-10-01',30000,'COP')
          """);
        reservaId = entero(conexion, "SELECT id FROM reservations WHERE codigo='H-MIGRA'");
        conexion.createStatement().executeUpdate(
          "INSERT INTO rooms(codigo,nombre) VALUES('101','Habitación 101')");
        int habitacionId = entero(conexion, "SELECT id FROM rooms WHERE codigo='101'");
        try (PreparedStatement item = conexion.prepareStatement(
            "INSERT INTO reservation_items(reservation_id,room_id,desde,hasta) VALUES(?,?,?,?)")) {
          item.setInt(1, reservaId);
          item.setInt(2, habitacionId);
          item.setString(3, "2026-10-10");
          item.setString(4, "2026-10-12");
          item.executeUpdate();
        }
        conexion.createStatement().executeUpdate("""
          INSERT INTO reservation_history(reservation_id,estado_ant,estado_nuevo,detalle,actor,en)
          VALUES(%d,'PENDIENTE','CONFIRMADA','llegada validada','recepcion','2026-10-01T10:00:00')
          """.formatted(reservaId));
        conexion.createStatement().executeUpdate("""
          INSERT INTO pagos(reservation_id,monto_cents,moneda,concepto,actor,creado_en)
          VALUES(%d,5000,'COP','anticipo','recepcion','2026-10-01T10:01:00')
          """.formatted(reservaId));
        conexion.createStatement().executeUpdate("""
          INSERT INTO pagos(reservation_id,monto_cents,moneda,concepto,actor,creado_en)
          VALUES(%d,1000,'COP','secuencia','recepcion','2026-10-01T10:01:30')
          """.formatted(reservaId));
        conexion.createStatement().executeUpdate("DELETE FROM pagos WHERE concepto='secuencia'");
        conexion.createStatement().executeUpdate("""
          INSERT INTO mensajes(reservation_id,autor,texto,creado_en)
          VALUES(%d,'HUESPED','Confirmo mi llegada','2026-10-01T10:02:00')
          """.formatted(reservaId));
      }

      Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .load()
        .migrate();

      try (Connection conexion = dataSource.getConnection()) {
        assertEquals(1, entero(conexion,
          "SELECT count(*) FROM reservations WHERE id=" + reservaId));
        assertEquals(1, contar(conexion, "reservation_items", reservaId));
        assertEquals(1, contar(conexion, "reservation_history", reservaId));
        assertEquals("llegada validada", texto(conexion,
          "SELECT detalle FROM reservation_history WHERE reservation_id=" + reservaId));
        assertEquals(1, contar(conexion, "pagos", reservaId));
        assertEquals(1, contar(conexion, "mensajes", reservaId));
        for (String indice : new String[] {"idx_reservations_llegada", "idx_reservations_usuario",
            "idx_reservation_items_solape", "idx_pagos_reserva", "idx_mensajes_reserva"}) {
          assertTrue(tieneIndice(conexion, indice), "se perdió el índice " + indice);
        }
        conexion.createStatement().executeUpdate("""
          INSERT INTO pagos(reservation_id,monto_cents,moneda,concepto,actor,creado_en)
          VALUES(%d,2000,'COP','posterior','recepcion','2026-10-02T10:00:00')
          """.formatted(reservaId));
        assertEquals(3, entero(conexion, "SELECT last_insert_rowid()"),
          "la migración no debe reducir la secuencia AUTOINCREMENT");
        try (ResultSet violaciones = conexion.createStatement().executeQuery("PRAGMA foreign_key_check")) {
          assertFalse(violaciones.next());
        }
      }
    } finally {
      Files.deleteIfExists(archivo);
    }
  }

  private static int pragma(Connection conexion, String nombre) throws Exception {
    try (ResultSet resultado = conexion.createStatement().executeQuery("PRAGMA " + nombre)) {
      return resultado.next() ? resultado.getInt(1) : -1;
    }
  }

  private static int entero(Connection conexion, String sql) throws Exception {
    try (ResultSet resultado = conexion.createStatement().executeQuery(sql)) {
      if (!resultado.next()) throw new AssertionError("sin fila: " + sql);
      return resultado.getInt(1);
    }
  }

  private static String texto(Connection conexion, String sql) throws Exception {
    try (ResultSet resultado = conexion.createStatement().executeQuery(sql)) {
      if (!resultado.next()) throw new AssertionError("sin fila: " + sql);
      return resultado.getString(1);
    }
  }

  private static int contar(Connection conexion, String tabla, int reservaId) throws Exception {
    try (PreparedStatement consulta = conexion.prepareStatement(
        "SELECT count(*) FROM " + tabla + " WHERE reservation_id=?")) {
      consulta.setInt(1, reservaId);
      try (ResultSet resultado = consulta.executeQuery()) {
        return resultado.next() ? resultado.getInt(1) : 0;
      }
    }
  }

  private static boolean tieneIndice(Connection conexion, String indice) throws Exception {
    try (PreparedStatement consulta = conexion.prepareStatement(
        "SELECT count(*) FROM sqlite_master WHERE type='index' AND name=?")) {
      consulta.setString(1, indice);
      try (ResultSet resultado = consulta.executeQuery()) {
        return resultado.next() && resultado.getInt(1) == 1;
      }
    }
  }
}
