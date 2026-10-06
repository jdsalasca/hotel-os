package co.hotel.ota;

import java.util.EnumMap;
import java.util.Map;

/**
 * Estado mutable de los canales, separado de la configuración. Única fuente de verdad para
 * decidir si un canal es CONECTADO: una llamada autorizada exitosa en entorno de producción.
 */
public class CanalEstadoRegistry {
  private final Map<Canal, SyncResult> ultimaSync = new EnumMap<>(Canal.class);
  private final Map<Canal, Boolean> desconectado = new EnumMap<>(Canal.class);
  private final Map<Canal, CanalConfig> configs = new EnumMap<>(Canal.class);

  public CanalEstadoRegistry(Map<Canal, CanalConfig> configs) { this.configs.putAll(configs); }

  /** Estado efectivo: NO_CONFIGURADO siempre gana; luego desconexión, luego última sync real. */
  public Estado estadoDe(Canal canal) {
    CanalConfig cfg = configs.get(canal);
    if (cfg == null) return Estado.NO_CONFIGURADO;
    Estado declarado = cfg.estadoDeclarado();
    if (declarado == Estado.NO_CONFIGURADO) return Estado.NO_CONFIGURADO;
    if (Boolean.TRUE.equals(desconectado.get(canal))) return Estado.DESCONECTADO;
    SyncResult sync = ultimaSync.get(canal);
    if (sync == null) return Estado.ACCESO_PENDIENTE;
    if (!sync.exitosa()) return Estado.ERROR;
    return cfg.esProduccion() ? Estado.CONECTADO : Estado.SANDBOX;
  }

  public SyncResult ultimaSyncDe(Canal canal) { return ultimaSync.get(canal); }

  /** Registra el resultado autoritativo de una llamada al proveedor. El conector decide, no el panel. */
  public void registrarResultadoDeSync(Canal canal, boolean exitosa, String detalle) {
    ultimaSync.put(canal, exitosa ? SyncResult.ok(detalle) : SyncResult.fallo(detalle));
  }

  public void marcarDesconectado(Canal canal) { desconectado.put(canal, true); }

  public CanalConfig config(Canal canal) { return configs.get(canal); }

  public Map<String, Object> panel() {
    Map<String, Object> panel = new java.util.LinkedHashMap<>();
    for (Canal canal : Canal.values()) {
      CanalConfig cfg = configs.get(canal);
      if (cfg == null) continue;
      panel.put(canal.name(), cfg.panel(estadoDe(canal), ultimaSync.get(canal)));
    }
    return panel;
  }
}