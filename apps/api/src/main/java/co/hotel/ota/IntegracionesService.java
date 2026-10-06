package co.hotel.ota;

import co.hotel.inventario.DatosInvalidosException;
import co.hotel.inventario.InventarioRepository;
import co.hotel.inventario.TarifaRepository;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
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
  private final InventarioRepository inventario;
  private final TarifaRepository tarifas;

  public IntegracionesService(OtaSyncRepository bitacora, InventarioRepository inventario,
                              TarifaRepository tarifas) {
    this.bitacora = bitacora;
    this.inventario = inventario;
    this.tarifas = tarifas;
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

  /** Canales proveedores con mapeos. La web propia no necesita un identificador externo. */
  private static final Set<Canal> CANALES_MAPEABLES =
    EnumSet.of(Canal.BOOKING, Canal.DESPEGAR, Canal.AIRBNB);

  /**
   * Registra qué recurso local corresponde a un identificador del proveedor. No publica nada por
   * sí solo: solo deja constancia del enlace que el hotel declara, para que una sincronización
   * futura no tenga que adivinarlo.
   */
  public OtaSyncRepository.Mapeo crearMapeo(Canal canal, Long roomId, Long roomTypeId,
                                            Long ratePlanId, String externalId) {
    if (canal == null || !CANALES_MAPEABLES.contains(canal))
      throw new MapeoInvalidoException("canal no admite mapeos: " + canal);
    if ((roomId == null) == (roomTypeId == null))
      throw new MapeoInvalidoException("el mapeo necesita exactamente una habitación o un tipo");
    String externo = externalId == null ? "" : externalId.trim();
    if (externo.isEmpty() || externo.length() > 120)
      throw new MapeoInvalidoException("el identificador externo es obligatorio y no puede pasar de 120 caracteres");

    if (roomId != null && inventario.habitacionPorId(roomId).isEmpty())
      throw new MapeoInvalidoException("habitación no encontrada: " + roomId);
    if (roomTypeId != null && inventario.tipoPorId(roomTypeId).isEmpty())
      throw new MapeoInvalidoException("tipo de habitación no encontrado: " + roomTypeId);
    if (ratePlanId != null) {
      try {
        tarifas.planPorId(ratePlanId);
      } catch (DatosInvalidosException e) {
        throw new MapeoInvalidoException("plan tarifario no encontrado: " + ratePlanId);
      }
    }
    if (bitacora.existeMapeo(canal.name(), externo))
      throw new MapeoInvalidoException("ese identificador externo ya está mapeado en " + canal.name());
    try {
      long id = bitacora.insertarMapeo(canal.name(), roomId, roomTypeId, ratePlanId, externo);
      OtaSyncRepository.Mapeo creado = bitacora.mapeoPorId(id);
      if (creado == null) throw new MapeoInvalidoException("no se pudo leer el mapeo recién creado");
      return creado;
    } catch (DuplicateKeyException e) {
      throw new MapeoInvalidoException("ese identificador externo ya está mapeado en " + canal.name());
    }
  }

  /** Retira un mapeo. Lo que se borra deja de publicarse; no toca inventario, tarifas ni reservas. */
  public boolean eliminarMapeo(long id) {
    if (id <= 0) throw new MapeoInvalidoException("identificador de mapeo inválido: " + id);
    return bitacora.eliminarMapeo(id) > 0;
  }

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