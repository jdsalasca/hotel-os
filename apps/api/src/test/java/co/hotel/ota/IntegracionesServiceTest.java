package co.hotel.ota;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Orquestación de los canales: quién está configurado, qué respondió el proveedor y qué se
 * muestra en el panel. La regla que más importa: un canal nunca aparece conectado sin una
 * sincronización autorizada exitosa en producción.
 */
class IntegracionesServiceTest {

  private CanalEstadoRegistry registro;
  private OtaSyncLog log;

  @BeforeEach
  void setUp() {
    registro = new CanalEstadoRegistry(configuraciones());
    log = new OtaSyncLog();
  }

  /** Credenciales presentes y sandbox: es el escenario en el que un canal puede tener estado real. */
  private static EnumMap<Canal, CanalConfig> configuraciones() {
    var configs = new EnumMap<Canal, CanalConfig>(Canal.class);
    configs.put(Canal.BOOKING, CanalConfig.de(Canal.BOOKING, Map.of(
      "BOOKING_CLIENT_ID", "i", "BOOKING_CLIENT_SECRET", "s", "BOOKING_HOTEL_ID", "1",
      "BOOKING_ENV", "sandbox")));
    configs.put(Canal.DESPEGAR, CanalConfig.de(Canal.DESPEGAR, Map.of(
      "DESPEGAR_API_KEY", "k", "DESPEGAR_HOTEL_CODE", "H1", "DESPEGAR_ENV", "sandbox")));
    configs.put(Canal.AIRBNB, CanalConfig.de(Canal.AIRBNB, Map.of(
      "AIRBNB_CLIENT_ID", "i", "AIRBNB_CLIENT_SECRET", "s", "AIRBNB_LISTING_IDS", "1",
      "AIRBNB_ENV", "sandbox")));
    return configs;
  }

  @Test
  @DisplayName("sin credenciales ningún canal se declara conectado")
  void sinCredencialesNadaEstaConectado() {
    var vacio = new CanalEstadoRegistry(Map.of(Canal.BOOKING, CanalConfig.de(Canal.BOOKING, Map.of())));
    assertEquals(Estado.NO_CONFIGURADO, vacio.estadoDe(Canal.BOOKING));
    assertEquals(Estado.NO_CONFIGURADO, vacio.estadoDe(Canal.DESPEGAR));
    assertEquals(Estado.NO_CONFIGURADO, vacio.estadoDe(Canal.AIRBNB));
  }

  @Test
  @DisplayName("una sincronización no puede fabricar un estado si faltan credenciales")
  void sinCredencialesLaSyncNoCambiaElEstado() {
    var vacio = new CanalEstadoRegistry(Map.of(Canal.BOOKING, CanalConfig.de(Canal.BOOKING, Map.of())));
    vacio.registrarResultadoDeSync(Canal.BOOKING, true, "HTTP 200");
    assertEquals(Estado.NO_CONFIGURADO, vacio.estadoDe(Canal.BOOKING),
      "sin credenciales no hay conexión que proclamar, aunque alguien registre un éxito");
  }

  @Test
  @DisplayName("una sincronización exitosa en sandbox deja el canal en SANDBOX, no en CONECTADO")
  void sandboxNuncaEsConectado() {
    registro.registrarResultadoDeSync(Canal.BOOKING, true, "HTTP 200");
    assertEquals(Estado.SANDBOX, registro.estadoDe(Canal.BOOKING));
  }

  @Test
  @DisplayName("la bitácora guarda cada intento con su resultado y sin secretos")
  void laBitacoraGuardaIntentos() {
    log.registrar(Canal.BOOKING, "reservas", true, "HTTP 200");
    log.registrar(Canal.AIRBNB, "reservas", false, "HTTP 401 invalid client_secret=abc123");
    assertEquals(2, log.intentos().size());
    assertFalse(log.intentos().get(1).detalle().contains("abc123"));
  }

  @Test
  @DisplayName("el panel lista los tres canales con su estado y requisitos pendientes")
  void elPanelListaLosCanales() {
    Map<String, Object> panel = registro.panel();
    assertTrue(panel.containsKey("BOOKING"));
    assertTrue(panel.containsKey("DESPEGAR"));
    assertTrue(panel.containsKey("AIRBNB"));
    assertTrue(panel.get("BOOKING").toString().contains("ACCESO_PENDIENTE"),
      "con credenciales pero sin llamada, el estado es acceso pendiente");
    assertTrue(panel.get("BOOKING").toString().contains("no es conexión de producción"),
      "el panel recuerda que sandbox no es producción");
  }

  @Test
  @DisplayName("un intento fallido posterior a uno exitoso deja el canal en ERROR")
  void unFalloPosteriorPasaElCanalAError() {
    registro.registrarResultadoDeSync(Canal.DESPEGAR, true, "HTTP 200");
    registro.registrarResultadoDeSync(Canal.DESPEGAR, false, "HTTP 503");
    assertEquals(Estado.ERROR, registro.estadoDe(Canal.DESPEGAR));
  }

  @Test
  @DisplayName("la web propia y el panel siguen funcionando si un proveedor falla")
  void unProveedorCaidoNoRompeElLocal() {
    log.registrar(Canal.BOOKING, "reservas", false, "timeout tras 20s");
    registro.registrarResultadoDeSync(Canal.BOOKING, false, "timeout tras 20s");

    assertEquals(Estado.ERROR, registro.estadoDe(Canal.BOOKING), "el fallo queda visible, no oculto");
    assertEquals(Estado.ACCESO_PENDIENTE, registro.estadoDe(Canal.DESPEGAR),
      "los demás canales no se ven afectados");
    assertEquals(1, log.intentos().size(), "la bitácora conserva el fallo para poder reintentar");
  }

  /** Bitácora en memoria para esta ronda; la tabla ota_syncs ya está en el esquema. */
  static class OtaSyncLog {
    private final List<Registro> registros = new java.util.ArrayList<>();

    void registrar(Canal canal, String operacion, boolean exitosa, String detalle) {
      registros.add(new Registro(canal, operacion, exitosa, Bitacora.sanear(detalle)));
    }

    List<Registro> intentos() { return registros; }

    record Registro(Canal canal, String operacion, boolean exitosa, String detalle) {}
  }
}