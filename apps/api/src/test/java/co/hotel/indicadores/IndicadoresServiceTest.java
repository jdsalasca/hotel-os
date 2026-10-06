package co.hotel.indicadores;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Indicadores de las tres fases, calculados sobre datos reales de la base.
 *
 * La regla que más importa aquí: un indicador sin datos NO vale cero. El resultado queda en null y
 * se explica por qué; un cero real es un cero. Mezclarlos es como se reportan mejoras que no existen.
 */
@SpringBootTest
class IndicadoresServiceTest {

  @DynamicPropertySource
  static void propiedades(DynamicPropertyRegistry reg) {
    reg.add("hotel.jdbc-path", () -> {
      try {
        var p = java.nio.file.Files.createTempFile("hotel-ind-", ".sqlite3");
        java.nio.file.Files.delete(p);
        return p.toAbsolutePath().toString();
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    });
  }

  @Autowired IndicadoresService indicadores;
  @Autowired JdbcTemplate jdbc;

  private String periodo = "2026-11";

  @BeforeEach
  void limpiar() {
    jdbc.update("DELETE FROM reservations");
    jdbc.update("DELETE FROM reservation_items");
    jdbc.update("DELETE FROM rooms");
    jdbc.update("DELETE FROM adoption_activities");
    jdbc.update("DELETE FROM ota_syncs");
  }

  private long crearHabitacion(String codigo) {
    jdbc.update("INSERT INTO rooms(codigo, estado, nombre) VALUES(?,'ACTIVA',?)", codigo, "Habitación " + codigo);
    return jdbc.queryForObject("SELECT id FROM rooms WHERE codigo=?", Long.class, codigo);
  }

  private long crearReserva(String codigo, long roomId, String llegada, String salida, String estado, String origen) {
    jdbc.update("INSERT INTO reservations(codigo,email,nombre,llegada,salida,huespedes,estado,origen,"
      + "idempotencia,creado_en) VALUES(?,'ana@example.com','Ana',?,?,2,?,?,?,datetime('now'))",
      codigo, llegada, salida, estado, origen, "idem-" + codigo);
    long id = jdbc.queryForObject("SELECT id FROM reservations WHERE codigo=?", Long.class, codigo);
    jdbc.update("INSERT INTO reservation_items(reservation_id,room_id,desde,hasta) VALUES(?,?,?,?)",
      id, roomId, llegada, salida);
    return id;
  }

  @Nested
  @DisplayName("fase 3 — operación estabilizada")
  class Operacion {

    @Test void ocupacionSeCalculaSobreNochesOcupadasYDisponibles() {
      crearHabitacion("101");
      long h = crearHabitacion("102");
      // 2 habitaciones × 30 noches = 60 noches disponibles; 3 noches ocupadas.
      crearReserva("H-1", h, "2026-11-01", "2026-11-04", "CONFIRMADA", "WEB");

      var resultado = indicadores.calcular("f3_ocupacion", periodo);
      assertTrue(resultado.tieneResultado());
      assertEquals(5.0, resultado.valor(), 0.01, "3/60 × 100");
    }

    @Test void sinReservasLaOcupacionEsCeroRealNoFaltante() {
      crearHabitacion("101");
      var resultado = indicadores.calcular("f3_ocupacion", periodo);
      assertTrue(resultado.tieneResultado(), "con inventario cargado, cero es un dato real");
      assertEquals(0.0, resultado.valor(), 0.01);
    }

    @Test void sinInventarioCargadoLaOcupacionEsDatoFaltante() {
      var resultado = indicadores.calcular("f3_ocupacion", periodo);
      assertFalse(resultado.tieneResultado(), "sin habitaciones no hay denominador");
      assertNull(resultado.valor());
      assertTrue(resultado.motivoFaltante().contains("habitaciones"));
    }

    @Test void laTasaDeCancelacionMuestraNumeradorYDenominador() {
      long h = crearHabitacion("101");
      crearReserva("H-1", h, "2026-11-01", "2026-11-02", "CONFIRMADA", "WEB");
      crearReserva("H-2", h, "2026-11-05", "2026-11-06", "CANCELADA", "WEB");

      var resultado = indicadores.calcular("f3_tasa_cancelacion", periodo);
      assertTrue(resultado.tieneResultado());
      assertEquals(50.0, resultado.valor(), 0.01, "1 cancelada de 2 creadas");
      assertNotNull(resultado.numerador());
      assertNotNull(resultado.denominador());
      assertEquals(1, resultado.numerador());
      assertEquals(2, resultado.denominador());
    }

    @Test void lasReservasSeCuentanPorCanal() {
      long h = crearHabitacion("101");
      crearReserva("H-1", h, "2026-11-01", "2026-11-02", "CONFIRMADA", "WEB");
      crearReserva("H-2", h, "2026-11-03", "2026-11-04", "CONFIRMADA", "BOOKING");
      crearReserva("H-3", h, "2026-11-05", "2026-11-06", "CONFIRMADA", "BOOKING");

      var porCanal = indicadores.reservasPorCanal(periodo);
      assertEquals(1L, porCanal.get("WEB"));
      assertEquals(2L, porCanal.get("BOOKING"));
    }

    @Test void elPorcentajeDeSincronizacionesUsaIntentosYExitosos() {
      jdbc.update("INSERT INTO ota_syncs(channel_codigo,operacion,exitosa,detalle,en) "
        + "VALUES('BOOKING','reservas',1,'ok','2026-11-01')");
      jdbc.update("INSERT INTO ota_syncs(channel_codigo,operacion,exitosa,detalle,en) "
        + "VALUES('BOOKING','reservas',0,'HTTP 503','2026-11-02')");
      jdbc.update("INSERT INTO ota_syncs(channel_codigo,operacion,exitosa,detalle,en) "
        + "VALUES('BOOKING','reservas',1,'ok','2026-11-03')");

      var resultado = indicadores.calcular("f3_sync_exitosas", periodo);
      assertEquals(66.67, resultado.valor(), 0.01, "2 de 3");
    }
  }

  @Nested
  @DisplayName("fase 2 — adopción y difusión")
  class Adopcion {

    @Test void cuentaActividadesQueElHotelConfirma() {
      jdbc.update("INSERT INTO adoption_activities(tipo,descripcion,fecha,participantes,confirmada_por,"
        + "con_datos_de_origen) VALUES('CAPACITACION','Uso del panel','2026-11-03',3,'admin@hotel.test',0)");
      jdbc.update("INSERT INTO adoption_activities(tipo,descripcion,fecha,participantes,confirmada_por,"
        + "con_datos_de_origen) VALUES('DIFUSION','Publicación en redes','2026-11-05',NULL,'admin@hotel.test',0)");

      assertEquals(1.0, indicadores.calcular("f2_capacitaciones", periodo).valor(), 0.01);
      assertEquals(1.0, indicadores.calcular("f2_difusion", periodo).valor(), 0.01);
    }

    @Test void sinActividadesElConteoEsCeroReal() {
      var resultado = indicadores.calcular("f2_capacitaciones", periodo);
      assertTrue(resultado.tieneResultado());
      assertEquals(0.0, resultado.valor(), 0.01);
    }

    @Test void sinActividadesMediblesDeOrigenNoSeAtribuyenVentas() {
      // Las actividades sin datos de origen no permiten atribuir reservas a campañas.
      List<Actividad> actividades = indicadores.actividades(periodo);
      assertTrue(actividades.stream().allMatch(a -> !a.tieneDatosDeOrigen()));
      assertNotNull(indicadores.calcular("f2_difusion", periodo).nota());
    }
  }

  @Nested
  @DisplayName("fase 1 — implementación y migración")
  class Implementacion {

    @Test void elInventarioSeMideContraLoQueElHotelDefineComoEsperado() {
      crearHabitacion("101");
      crearHabitacion("102");
      indicadores.fijarInventarioEsperado(2);

      var resultado = indicadores.calcular("f1_inventario_cargado", "PUNTO");
      assertEquals(100.0, resultado.valor(), 0.01);
    }

    @Test void sinInventarioEsperadoNoHayPorcentaje() {
      crearHabitacion("101");
      var resultado = indicadores.calcular("f1_inventario_cargado", "PUNTO");
      assertFalse(resultado.tieneResultado());
      assertTrue(resultado.motivoFaltante().contains("esperado"));
    }
  }

  @Nested
  @DisplayName("informe")
  class Informe {

    @Test void cadaIndicadorMuestraDefinicionFormulaFuentePeriodoYUnidad() {
      var definicion = indicadores.definicion("f3_ocupacion");
      assertEquals(3, definicion.fase());
      assertTrue(definicion.formula().contains("noches ocupadas"));
      assertEquals("porcentaje", definicion.unidad());
      assertFalse( definicion.fuente().isBlank());
    }

    @Test void noSeInventanLineaBaseNiMeta() {
      var definicion = indicadores.definicion("f3_ocupacion");
      assertNull(definicion.lineaBase(), "sin dato del hotel, la línea base se queda vacía");
      assertNull(definicion.meta());
    }

    @Test void elInformeCubreLasTresFases() {
      var informe = indicadores.informe(periodo);
      assertTrue(informe.stream().anyMatch(i -> i.definicion().fase() == 1));
      assertTrue(informe.stream().anyMatch(i -> i.definicion().fase() == 2));
      assertTrue(informe.stream().anyMatch(i -> i.definicion().fase() == 3));
    }

    @Test void elInformeDistingueFaltanteDeCeroEnElCsv() {
      crearHabitacion("101");
      var csv = indicadores.exportarCsv(periodo);
      assertTrue(csv.contains("f3_ocupacion"));
      assertTrue(csv.contains("0.00"), "ocupación cero con inventario cargado");
      assertTrue(csv.contains("SIN_DATOS"), "un indicador sin denominador va marcado como faltante");
    }
  }
}