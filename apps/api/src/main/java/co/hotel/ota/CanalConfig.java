package co.hotel.ota;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuración de un canal derivada de variables de entorno. Objeto de valor de solo lectura:
 * el estado de la última sincronización vive en {@link CanalEstadoRegistry}, no aquí.
 * Cada canal declara sus propias variables; no se supone una API key compartida entre OTAs.
 */
public final class CanalConfig {
  private static final Map<String, List<String>> REQUISITOS = Map.of(
    "BOOKING", List.of("BOOKING_CLIENT_ID", "BOOKING_CLIENT_SECRET", "BOOKING_HOTEL_ID", "BOOKING_ENV"),
    "DESPEGAR", List.of("DESPEGAR_API_KEY", "DESPEGAR_HOTEL_CODE", "DESPEGAR_ENV"),
    "AIRBNB", List.of("AIRBNB_CLIENT_ID", "AIRBNB_CLIENT_SECRET", "AIRBNB_LISTING_IDS", "AIRBNB_ENV"));

  /** Variables opcionales que solo necesita el conector (endpoint base para sandbox o pruebas). */
  private static final List<String> OPCIONALES = List.of("API_BASE");

  private static final List<String> SUFIJOS_CREDENCIAL = List.of("CLIENT_ID", "CLIENT_SECRET", "API_KEY");
  private static final List<String> SUFIJOS_IDENTIFICADOR = List.of("HOTEL_ID", "HOTEL_CODE", "LISTING_IDS");
  private static final List<String> ENTORNOS = List.of("sandbox", "prod");

  private final Canal canal;
  private final Map<String, String> env;

  private CanalConfig(Canal canal, Map<String, String> env) {
    this.canal = canal;
    this.env = Map.copyOf(env);
    String entorno = valor("ENV");
    if (entorno != null && !ENTORNOS.contains(entorno.toLowerCase()))
      throw new IllegalArgumentException("entorno no soportado para " + canal + ": use sandbox o prod");
  }

  public static CanalConfig de(Canal canal, Map<String, String> env) { return new CanalConfig(canal, env); }

  public Canal canal() { return canal; }

  public List<String> variablesRequeridas() { return REQUISITOS.getOrDefault(canal.name(), List.of()); }

  public List<String> credencialesFaltantes() {
    return variablesRequeridas().stream().filter(v -> esCredencial(v) && valor(sufijoDe(v)) == null).toList();
  }

  public List<String> identificadoresFaltantes() {
    return variablesRequeridas().stream()
        .filter(v -> !esCredencial(v) && !v.endsWith("_ENV") && valor(sufijoDe(v)) == null).toList();
  }

  /** "BOOKING_CLIENT_ID" -> "CLIENT_ID": valor() siempre recibe el sufijo, no la variable completa. */
  private String sufijoDe(String variable) {
    return variable.startsWith(canal.name() + "_") ? variable.substring(canal.name().length() + 1) : variable;
  }

  private static boolean esCredencial(String variable) {
    return SUFIJOS_CREDENCIAL.stream().anyMatch(variable::endsWith);
  }

  /** Pendientes reales para poder declarar el canal operativo. */
  public List<String> requisitosPendientes() {
    List<String> pendientes = new ArrayList<>();
    if (!credencialesFaltantes().isEmpty())
      pendientes.add("credenciales faltantes: " + String.join(", ", credencialesFaltantes()));
    if (!identificadoresFaltantes().isEmpty())
      pendientes.add("identificadores del alojamiento faltantes: " + String.join(", ", identificadoresFaltantes()));
    pendientes.add("acceso de partner/onboarding vigente del proveedor y certificaciones aplicadas");
    if ("sandbox".equalsIgnoreCase(valor("ENV")))
      pendientes.add("entorno sandbox: no es conexión de producción");
    return pendientes;
  }

  /** Estado derivado solo de la configuración. Se queda en SANDBOX como techo: alcanzar CONECTADO
   * exige además una sincronización autorizada exitosa contra el proveedor en entorno prod. */
  public Estado estadoDeclarado() {
    if (!credencialesFaltantes().isEmpty()) return Estado.NO_CONFIGURADO;
    if (!identificadoresFaltantes().isEmpty()) return Estado.ACCESO_PENDIENTE;
    return Estado.SANDBOX;
  }

  public boolean esProduccion() { return "prod".equalsIgnoreCase(valor("ENV")); }

  /** Acceso del conector al valor de una variable de este canal por sufijo. */
  public String valor(String sufijoVariable) {
    String v = env.get(canal.name() + "_" + sufijoVariable);
    return (v == null || v.isBlank()) ? null : v.trim();
  }

  /** Vista para el panel: nombres y faltantes, nunca el valor de un secreto. */
  public Map<String, Object> panel(Estado estado, SyncResult ultimaSync) {
    Map<String, Object> panel = new LinkedHashMap<>();
    panel.put("canal", canal.name());
    panel.put("estado", estado.name());
    panel.put("entorno", valor("ENV"));
    panel.put("identificadores", identificadoresNoSecretos());
    panel.put("variablesRequeridas", variablesRequeridas());
    panel.put("variablesFaltantes", credencialesFaltantes());
    panel.put("requisitosPendientes", requisitosPendientes());
    panel.put("ultimaSync", ultimaSync == null ? null : ultimaSync.en());
    panel.put("ultimaSyncResultado", ultimaSync == null ? null : ultimaSync.resultado());
    return panel;
  }

  private Map<String, String> identificadoresNoSecretos() {
    Map<String, String> ids = new LinkedHashMap<>();
    for (String variable : variablesRequeridas())
      if (SUFIJOS_IDENTIFICADOR.stream().anyMatch(variable::endsWith)) ids.put(variable, valor(sufijoDe(variable)));
    return ids;
  }
}