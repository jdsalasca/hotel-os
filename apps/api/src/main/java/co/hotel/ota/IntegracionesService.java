package co.hotel.ota;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Servicio de integraciones: construye los conectores desde el entorno, ejecuta la sincronización
 * a pedido y refleja el estado real de cada canal.
 *
 * Una caída de un proveedor no afecta la web ni el panel: el fallo queda registrado y se puede
 * reintentar. Nunca se declara éxito de un proveedor que rechazó la operación.
 */
@Service
public class IntegracionesService {

  private final CanalEstadoRegistry registro;
  private final OtaSyncRepository bitacora;

  public IntegracionesService(OtaSyncRepository bitacora) {
    this.bitacora = bitacora;
    this.registro = new CanalEstadoRegistry(configuracionesDesdeEntorno());
  }

  /** Solo lectura de variables de entorno. Ningún secreto sale de aquí. */
  static EnumMap<Canal, CanalConfig> configuracionesDesdeEntorno() {
    Map<String, String> env = System.getenv();
    EnumMap<Canal, CanalConfig> configs = new EnumMap<>(Canal.class);
    for (Canal c : List.of(Canal.BOOKING, Canal.DESPEGAR, Canal.AIRBNB))
      configs.put(c, CanalConfig.de(c, env));
    return configs;
  }

  public CanalEstadoRegistry registro() { return registro; }

  /**
   * Sincroniza un canal contra su proveedor. El estado se actualiza con lo que realmente respondió:
   * si el proveedor rechaza, el canal queda en ERROR y se registra el motivo en la bitácora.
   */
  public ResultadoSync sincronizar(Canal canal) {
    CanalConfig cfg = registro.config(canal);
    if (cfg == null) return ResultadoSync.fallo("canal no soportado: " + canal);

    ResultadoSync resultado = conector(canal).sincronizarReservas();
    registro.registrarResultadoDeSync(canal, resultado.exitosa(), resultado.detalle());
    bitacora.registrar(canal.name(), "reservas", resultado.exitosa(), resultado.detalle());
    return resultado;
  }

  /** Vista para la pantalla de integraciones: estado, requisitos y últimos errores, sin secretos. */
  public Map<String, Object> panel() {
    Map<String, Object> panel = new LinkedHashMap<>();
    Map<String, Object> estados = registro.panel();
    Map<String, List<Map<String, Object>>> ultimas = bitacora.ultimasPorTodosLosCanales(5);
    for (Canal c : List.of(Canal.BOOKING, Canal.DESPEGAR, Canal.AIRBNB)) {
      Map<String, Object> fila = new LinkedHashMap<>((Map<String, Object>) estados.get(c.name()));
      fila.put("ultimasSincronizaciones", ultimas.get(c.name()));
      fila.put("mapeos", bitacora.mapeosDe(c.name()));
      panel.put(c.name(), fila);
    }
    return panel;
  }

  private ConectorOta conector(Canal canal) {
    CanalConfig cfg = registro.config(canal);
    return switch (canal) {
      case BOOKING -> new BookingConector(cfg, BookingConector.clientePorDefecto());
      case DESPEGAR -> new DespegarConector(cfg, DespegarConector.clientePorDefecto());
      case AIRBNB -> new AirbnbConector(cfg, AirbnbConector.clientePorDefecto());
      default -> throw new IllegalArgumentException("canal sin conector: " + canal);
    };
  }
}