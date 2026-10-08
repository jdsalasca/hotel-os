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
    jdbc.update("DELETE FROM blocks");
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

    @Test void ocupacionCuentaSoloLaInterseccionConElPeriodo() {
      long h = crearHabitacion("101");
      // 9 noches en total pero solo 2 caen en noviembre: la ocupación de noviembre es 2/30.
      crearReserva("H-1", h, "2026-10-25", "2026-11-03", "CONFIRMADA", "WEB");

      var resultado = indicadores.calcular("f3_ocupacion", periodo);
      assertTrue(resultado.tieneResultado());
      assertEquals(6.67, resultado.valor(), 0.01, "2 noches de noviembre, no 9");
      assertEquals(2, resultado.numerador());
      assertEquals(30, resultado.denominador());
    }

    @Test void bloqueoRestaNochesVendiblesDelDenominador() {
      long h = crearHabitacion("101");
      jdbc.update("INSERT INTO blocks(room_id,desde,hasta,motivo) VALUES(?, '2026-11-10', '2026-11-13', 'Mantenimiento')", h);

      var resultado = indicadores.calcular("f3_ocupacion", periodo);
      assertTrue(resultado.tieneResultado());
      assertEquals(27, resultado.denominador(), "30 noches menos 3 bloqueadas");
    }

    @Test void bloqueoDeTodoElHotelRestaTodasLasHabitaciones() {
      crearHabitacion("101");
      crearHabitacion("102");
      jdbc.update("INSERT INTO blocks(room_id,desde,hasta,motivo) VALUES(NULL, '2026-11-01', '2026-11-06', 'Cierre total')");

      var resultado = indicadores.calcular("f3_ocupacion", periodo);
      assertEquals(50, resultado.denominador(), "2 habitaciones × 30 menos 2 × 5 noches cerradas");
    }

    @Test void bloqueosSolapadosDeLaMismaHabitacionCuentanSuUnion() {
      // Habitación 101 bloqueada del día 1 al 3 y del 2 al 4 (de noviembre): la unión ocupa
      // las noches 01, 02 y 03. Sumar intervalos daría 4 y dejaría 56 vendibles; son 57.
      crearHabitacion("102");
      long h = crearHabitacion("101");
      jdbc.update("INSERT INTO blocks(room_id,desde,hasta,motivo) VALUES(?, '2026-11-01', '2026-11-03', 'Mantenimiento')", h);
      jdbc.update("INSERT INTO blocks(room_id,desde,hasta,motivo) VALUES(?, '2026-11-02', '2026-11-04', 'Mantenimiento')", h);

      var resultado = indicadores.calcular("f3_ocupacion", periodo);
      assertTrue(resultado.tieneResultado());
      assertEquals(57, resultado.denominador(), "2 × 30 menos 3 noches en unión, no 4 sumadas");
    }

    @Test void bloqueoGlobalMasIndividualSolapadosCuentanSuUnion() {
      // Cierre del hotel 01→03 (noches 01,02 en ambas) más bloqueo individual de la 101 del
      // 02→04 (noches 02,03): la 101 aporta {01,02,03} y la 102 {01,02}. Son 5, no 6.
      long h1 = crearHabitacion("101");
      crearHabitacion("102");
      jdbc.update("INSERT INTO blocks(room_id,desde,hasta,motivo) VALUES(NULL, '2026-11-01', '2026-11-03', 'Cierre total')");
      jdbc.update("INSERT INTO blocks(room_id,desde,hasta,motivo) VALUES(?, '2026-11-02', '2026-11-04', 'Mantenimiento')", h1);

      var resultado = indicadores.calcular("f3_ocupacion", periodo);
      assertTrue(resultado.tieneResultado());
      assertEquals(55, resultado.denominador(), "60 menos 5 en unión, no 6 sumadas");
    }

    @Test void bloqueosDuplicadosCuentanUnaSolaVez() {
      // El mismo intervalo registrado dos veces sigue siendo 3 noches, no 6.
      long h = crearHabitacion("101");
      jdbc.update("INSERT INTO blocks(room_id,desde,hasta,motivo) VALUES(?, '2026-11-10', '2026-11-13', 'Mantenimiento')", h);
      jdbc.update("INSERT INTO blocks(room_id,desde,hasta,motivo) VALUES(?, '2026-11-10', '2026-11-13', 'Mantenimiento')", h);

      var resultado = indicadores.calcular("f3_ocupacion", periodo);
      assertTrue(resultado.tieneResultado());
      assertEquals(27, resultado.denominador(), "30 menos 3 en unión, no 6 sumadas");
    }

    @Test void bloqueoQueCruzaElPeriodoSoloRestaSuInterseccion() {
      // Del 28 de octubre al 2 de noviembre: en noviembre solo cae la noche del día 1.
      long h = crearHabitacion("101");
      jdbc.update("INSERT INTO blocks(room_id,desde,hasta,motivo) VALUES(?, '2026-10-28', '2026-11-02', 'Mantenimiento')", h);

      var resultado = indicadores.calcular("f3_ocupacion", periodo);
      assertTrue(resultado.tieneResultado());
      assertEquals(29, resultado.denominador(), "solo la noche del 01 cae en noviembre");
    }

    @Test void bloqueoDeHabitacionFueraDeServicioNoResta() {
      // La 101 sale de la oferta: ni cuenta en el total ni su bloqueo resta.
      long h1 = crearHabitacion("101");
      crearHabitacion("102");
      jdbc.update("UPDATE rooms SET estado='FUERA_DE_SERVICIO' WHERE id=?", h1);
      jdbc.update("INSERT INTO blocks(room_id,desde,hasta,motivo) VALUES(?, '2026-11-10', '2026-11-13', 'Mantenimiento')", h1);

      var resultado = indicadores.calcular("f3_ocupacion", periodo);
      assertTrue(resultado.tieneResultado());
      assertEquals(30, resultado.denominador(), "solo la 102 activa aporta 30 noches");
    }

    @Test void sobreventaSinRegistroEsDatoFaltanteNoCero() {
      crearHabitacion("101");

      var resultado = indicadores.calcular("f3_sobreventa", periodo);
      assertFalse(resultado.tieneResultado(), "cero fijo finge que se midió y dio cero");
      assertNull(resultado.valor());
      assertTrue(resultado.motivoFaltante().contains("incidente")
        || resultado.motivoFaltante().contains("registro"),
        "el motivo debe decir que no hay registro: " + resultado.motivoFaltante());
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

    @Test void lasCreadasSeCuentanPorFechaDeCreacionNoDeLlegada() {
      // Creada en octubre para diciembre: en el informe de noviembre no existe, aunque llegue
      // en otro mes. Contarla por llegada la pondría en un periodo donde nadie la creó.
      long h = crearHabitacion("101");
      crearReserva("H-1", h, "2026-11-05", "2026-11-07", "CONFIRMADA", "WEB");
      jdbc.update("UPDATE reservations SET creado_en='2026-10-20 10:00:00' WHERE codigo='H-1'");

      var resultado = indicadores.calcular("f3_tasa_cancelacion", periodo);
      assertFalse(resultado.tieneResultado(), "en noviembre no se creó ninguna");
      assertEquals(0, indicadores.reservasPorCanal(periodo).values().stream().mapToLong(Long::longValue).sum());
    }

    @Test void laCanceladaCuentaEnLaCohorteDondeSeCreo() {
      // Creada y cancelada en noviembre para una estancia de diciembre: la cancelación es de la
      // cohorte de noviembre, no de diciembre. Por llegada se perdería del numerador.
      long h = crearHabitacion("101");
      crearReserva("H-1", h, "2026-12-05", "2026-12-07", "CANCELADA", "WEB");
      jdbc.update("UPDATE reservations SET creado_en='2026-11-20 10:00:00'");

      var resultado = indicadores.calcular("f3_tasa_cancelacion", periodo);
      assertTrue(resultado.tieneResultado());
      assertEquals(100.0, resultado.valor(), 0.01, "la única creada en noviembre está cancelada");
      assertEquals(1, resultado.numerador());
      assertEquals(1, resultado.denominador());
    }

    @Test void laTasaDeCancelacionMuestraNumeradorYDenominador() {
      long h = crearHabitacion("101");
      crearReserva("H-1", h, "2026-11-01", "2026-11-02", "CONFIRMADA", "WEB");
      crearReserva("H-2", h, "2026-11-05", "2026-11-06", "CANCELADA", "WEB");
      jdbc.update("UPDATE reservations SET creado_en='2026-11-15 10:00:00'");

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
      jdbc.update("UPDATE reservations SET creado_en='2026-11-15 10:00:00'");

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

    private int actividadesRegistradas() {
      return jdbc.queryForObject("SELECT COUNT(*) FROM adoption_activities", Integer.class);
    }

    @Test void actividadConFechaInvalidaNoSeGuarda() {
      assertThrows(IllegalArgumentException.class, () -> indicadores.registrarActividad(
        "CAPACITACION", "Uso del panel", "ayer", 3, "admin@hotel.test", false));
      assertEquals(0, actividadesRegistradas(),
        "una fecha inválida se rechaza antes de insertar: un POST inválido no deja datos");
    }

    @Test void actividadSinTipoODescripcionSeRechaza() {
      assertThrows(IllegalArgumentException.class, () -> indicadores.registrarActividad(
        "   ", "Uso del panel", "2026-11-03", 3, "admin@hotel.test", false));
      assertThrows(IllegalArgumentException.class, () -> indicadores.registrarActividad(
        "CAPACITACION", null, "2026-11-03", 3, "admin@hotel.test", false));
      assertEquals(0, actividadesRegistradas());
    }

    @Test void actividadConParticipantesNegativosSeRechaza() {
      assertThrows(IllegalArgumentException.class, () -> indicadores.registrarActividad(
        "CAPACITACION", "Uso del panel", "2026-11-03", -1, "admin@hotel.test", false));
      assertEquals(0, actividadesRegistradas());
    }

    @Test void actividadValidaSeGuardaConFechaReal() {
      indicadores.registrarActividad("CAPACITACION", "Uso del panel", "2026-11-03", 3,
        "admin@hotel.test", false);
      assertEquals(1, actividadesRegistradas());
      assertEquals(LocalDate.parse("2026-11-03"), indicadores.actividades(periodo).get(0).fecha());
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

    private void sync(String canal, boolean exitosa, String fecha) {
      jdbc.update("INSERT INTO ota_syncs(channel_codigo,operacion,exitosa,detalle,en) "
        + "VALUES(?, 'reservas', ?, 'ok', ?)", canal, exitosa ? 1 : 0, fecha);
    }

    @Test void canalesConectadosCuentanCanalesNoIntentos() {
      sync("BOOKING", true, "2026-11-03");
      sync("BOOKING", false, "2026-11-04");
      sync("BOOKING", true, "2026-11-05");

      var resultado = indicadores.calcular("f1_canales_conectados", periodo);
      assertFalse(resultado.tieneResultado(),
        "BOOKING no está configurado y WEB no tiene sync: nada conectado");
    }

    @Test void canalConfiguradoConSyncDaCien() {
      sync("WEB", true, "2026-11-03");

      var resultado = indicadores.calcular("f1_canales_conectados", periodo);
      assertTrue(resultado.tieneResultado());
      assertEquals(100.0, resultado.valor(), 0.01);
      assertEquals(1, resultado.numerador());
      assertEquals(1, resultado.denominador());
    }

    @Test void dosConfiguradosUnoConectadoDaCincuenta() {
      jdbc.update("UPDATE channels SET activo=1 WHERE codigo='BOOKING'");
      sync("BOOKING", true, "2026-11-03");

      var resultado = indicadores.calcular("f1_canales_conectados", periodo);
      assertTrue(resultado.tieneResultado());
      assertEquals(50.0, resultado.valor(), 0.01);
    }
  }

  @Nested
  @DisplayName("informe")
  class Informe {

    @Test void cadaIndicadorMuestraDefinicionFormulaFuentePeriodoYUnidad() {
      var definicion = indicadores.definicion("f3_ocupacion");
      assertEquals(3, definicion.fase());
      assertTrue(definicion.formula().contains("noches comprometidas"));
      assertEquals("porcentaje", definicion.unidad());
      assertFalse( definicion.fuente().isBlank());
    }

    @Test void ocupacionSeNombraPorLoQueMide() {
      // Incluye PENDIENTE y CONFIRMADA: sin recepción no hay estancia efectiva que medir,
      // así que el nombre no puede prometer "ocupación".
      var definicion = indicadores.definicion("f3_ocupacion");
      assertEquals("Noches comprometidas", definicion.nombre());
      assertTrue(definicion.definicion().contains("reserva vigente"));
    }

    @Test void noSeInventanLineaBaseNiMeta() {
      var definicion = indicadores.definicion("f3_ocupacion");
      assertNull(definicion.lineaBase(), "sin dato del hotel, la línea base se queda vacía");
      assertNull(definicion.meta());
    }

    @Test void elHotelFijaMetaLineaBaseYResponsable() {
      try {
        var d = indicadores.fijarReferencia("f3_ocupacion", "60% en temporada baja", "75%", "Gerencia");
        assertEquals("60% en temporada baja", d.lineaBase());
        assertEquals("75%", d.meta());
        assertEquals("Gerencia", d.responsable());
        var enInforme = indicadores.informe(periodo).stream()
          .filter(i -> i.definicion().clave().equals("f3_ocupacion")).findFirst().orElseThrow();
        assertEquals("75%", enInforme.definicion().meta(), "el informe muestra lo fijado");
      } finally {
        // La base es compartida por la clase: se deja como estaba (vacío = sin definir).
        indicadores.fijarReferencia("f3_ocupacion", "", "", "");
      }
    }

    @Test void referenciaDeClaveInexistenteSeRechaza() {
      assertThrows(IllegalArgumentException.class,
        () -> indicadores.fijarReferencia("f9_no_existe", null, "75%", null));
    }

    @Test void referenciaMuyLargaSeRechaza() {
      assertThrows(IllegalArgumentException.class,
        () -> indicadores.fijarReferencia("f3_ocupacion", "x".repeat(201), null, null));
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

    @Test void elCsvNeutralizaFormulasInyectadasEnLosTextos() {
      // Entrecomillar no basta: Excel ejecuta "=..." aunque vaya entre comillas. Si un texto
      // empieza por = + - @, se prefija con comilla simple para que quede como texto.
      jdbc.update("INSERT INTO indicator_definitions(clave,fase,nombre,definicion,formula,fuente,"
        + "unidad,periodo_por_defecto) VALUES('f9_mal',3,'=HYPERLINK(\"http://mal\",\"clic\")',"
        + "'+cmd',\"@mal\",\"fuente\",\"u\",\"2026-11\")");
      var csv = indicadores.exportarCsv(periodo);
      assertTrue(csv.contains("\"'=HYPERLINK"),
        "la fórmula debe salir neutralizada, no ejecutable");
      assertFalse(csv.lines().anyMatch(l -> l.contains(",\"=HYPERLINK") || l.contains(",\"+cmd")
        || l.contains(",\"@mal")), "ninguna celda puede empezar por = + - @:\n" + csv);
    }
  }
}