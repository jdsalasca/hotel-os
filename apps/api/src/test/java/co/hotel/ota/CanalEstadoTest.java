package co.hotel.ota;

import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Contrato de estados OTA. Nada se declara conectado sin una llamada autorizada real:
 * la configuración sola nunca alcanza CONECTADO.
 */
class CanalEstadoTest {

  private static CanalConfig booking(Map<String, String> env) { return CanalConfig.de(Canal.BOOKING, env); }

  private static Map<String, String> envBooking(String entorno) {
    return Map.of("BOOKING_CLIENT_ID", "id", "BOOKING_CLIENT_SECRET", "s",
      "BOOKING_HOTEL_ID", "8135188", "BOOKING_ENV", entorno);
  }

  private static CanalEstadoRegistry registro(CanalConfig... cfgs) {
    var map = new EnumMap<Canal, CanalConfig>(Canal.class);
    for (CanalConfig c : cfgs) map.put(c.canal(), c);
    return new CanalEstadoRegistry(map);
  }

  @Nested
  @DisplayName("estado efectivo según última sincronización")
  class EstadoEfectivo {

    @Test void sinCredencialesEsNoConfiguradoAunqueHuboSync() {
      CanalEstadoRegistry reg = registro(booking(Map.of()));
      reg.registrarResultadoDeSync(Canal.BOOKING, true, "HTTP 200");
      assertEquals(Estado.NO_CONFIGURADO, reg.estadoDe(Canal.BOOKING));
    }

    @Test void configCompletaSinLlamarEsAccesoPendiente() {
      CanalEstadoRegistry reg = registro(booking(envBooking("sandbox")));
      assertEquals(Estado.ACCESO_PENDIENTE, reg.estadoDe(Canal.BOOKING));
    }

    @Test void syncExitosaEnSandboxEsSandbox() {
      CanalEstadoRegistry reg = registro(booking(envBooking("sandbox")));
      reg.registrarResultadoDeSync(Canal.BOOKING, true, "HTTP 200");
      assertEquals(Estado.SANDBOX, reg.estadoDe(Canal.BOOKING));
    }

    @Test void syncExitosaEnProdEsConectado() {
      CanalEstadoRegistry reg = registro(booking(envBooking("prod")));
      reg.registrarResultadoDeSync(Canal.BOOKING, true, "HTTP 200");
      assertEquals(Estado.CONECTADO, reg.estadoDe(Canal.BOOKING));
    }

    @Test void syncFallidaEsErrorYConservaElMotivoParaDiagnosticar() {
      CanalEstadoRegistry reg = registro(booking(envBooking("prod")));
      reg.registrarResultadoDeSync(Canal.BOOKING, false, "HTTP 401 unauthorized");
      assertEquals(Estado.ERROR, reg.estadoDe(Canal.BOOKING));
      assertEquals("HTTP 401 unauthorized", reg.ultimaSyncDe(Canal.BOOKING).resultado());
    }

    @Test void desconectarPrevaleceSobreSyncExitosa() {
      CanalEstadoRegistry reg = registro(booking(envBooking("prod")));
      reg.registrarResultadoDeSync(Canal.BOOKING, true, "HTTP 200");
      reg.marcarDesconectado(Canal.BOOKING);
      assertEquals(Estado.DESCONECTADO, reg.estadoDe(Canal.BOOKING));
    }

    @Test void canalSinRegistrarEsNoConfigurado() {
      assertEquals(Estado.NO_CONFIGURADO, new CanalEstadoRegistry(Map.of()).estadoDe(Canal.AIRBNB));
    }
  }

  @Nested
  @DisplayName("configuración por canal")
  class Config {

    @Test void cadaCanalDeclaraSusPropiasCredenciales() {
      assertEquals(List.of("BOOKING_CLIENT_ID", "BOOKING_CLIENT_SECRET", "BOOKING_HOTEL_ID", "BOOKING_ENV"),
        booking(Map.of()).variablesRequeridas());
      assertEquals(List.of("DESPEGAR_API_KEY", "DESPEGAR_HOTEL_CODE", "DESPEGAR_ENV"),
        CanalConfig.de(Canal.DESPEGAR, Map.of()).variablesRequeridas());
      assertEquals(List.of("AIRBNB_CLIENT_ID", "AIRBNB_CLIENT_SECRET", "AIRBNB_LISTING_IDS", "AIRBNB_ENV"),
        CanalConfig.de(Canal.AIRBNB, Map.of()).variablesRequeridas());
    }

    @Test void noSeSuponeUnaSolaApiKeyCompartida() {
      var bookingVars = booking(Map.of()).variablesRequeridas();
      var despegarVars = CanalConfig.de(Canal.DESPEGAR, Map.of()).variablesRequeridas();
      assertTrue(bookingVars.contains("BOOKING_CLIENT_ID"));
      assertFalse(despegarVars.contains("BOOKING_CLIENT_ID"));
      assertTrue(despegarVars.contains("DESPEGAR_API_KEY"));
    }

    @Test void credencialesFaltantesBloqueanAntesQueIdentificadores() {
      CanalConfig cfg = booking(Map.of("BOOKING_CLIENT_ID", "i", "BOOKING_ENV", "sandbox"));
      assertEquals(Estado.NO_CONFIGURADO, cfg.estadoDeclarado());
      assertTrue(cfg.requisitosPendientes().stream().anyMatch(s -> s.contains("BOOKING_CLIENT_SECRET")));
    }

    @Test void identificadoresFaltantesDejanAccesoPendiente() {
      CanalConfig cfg = booking(Map.of("BOOKING_CLIENT_ID", "i", "BOOKING_CLIENT_SECRET", "s", "BOOKING_ENV", "sandbox"));
      assertEquals(Estado.ACCESO_PENDIENTE, cfg.estadoDeclarado());
      assertTrue(cfg.requisitosPendientes().stream().anyMatch(s -> s.contains("BOOKING_HOTEL_ID")));
    }

    @Test void entornoNoSoportadoFalla() {
      assertThrows(IllegalArgumentException.class, () -> booking(envBooking("staging")));
    }

    @Test void laConfiguracionSolaNuncaDeclaraConectado() {
      // Aunque todo esté completo y sea prod, sin llamada real el techo es SANDBOX.
      assertEquals(Estado.SANDBOX, booking(envBooking("prod")).estadoDeclarado());
    }

    @Test void elPanelNuncaExponeElValorDeLosSecretos() {
      CanalEstadoRegistry reg = registro(booking(Map.of(
        "BOOKING_CLIENT_ID", "id-publico", "BOOKING_CLIENT_SECRET", "super-secreto",
        "BOOKING_HOTEL_ID", "8135188", "BOOKING_ENV", "sandbox")));
      String panel = reg.panel().toString();
      assertFalse(panel.contains("super-secreto"), "el panel no debe filtrar el secret");
      assertTrue(panel.contains("8135188"), "los identificadores no secretos sí se muestran");
    }

    @Test void elPanelInformaSandboxComoNoProduccion() {
      CanalEstadoRegistry reg = registro(booking(envBooking("sandbox")));
      reg.registrarResultadoDeSync(Canal.BOOKING, true, "HTTP 200");
      assertTrue(reg.panel().toString().contains("no es conexión de producción"));
    }
  }
}