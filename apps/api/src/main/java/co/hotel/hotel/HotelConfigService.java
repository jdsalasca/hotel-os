package co.hotel.hotel;

import co.hotel.reservas.SqliteTransactionExecutor;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Identidad operativa del hotel: nombre, contacto, horarios y política de cancelación. Nada de
 * esto decide precios, inventario ni reservas; solo dice quién opera el sitio y cómo contactarlo.
 */
@Service
public class HotelConfigService {
  private static final Pattern CORREO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[A-Za-z]{2,}$");
  private static final Pattern TELEFONO = Pattern.compile("^[+0-9][0-9 .()/-]{5,29}$");
  private static final Pattern HORA = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");

  /** Todo lo que el panel puede administrar. Fuera de esta lista, la base no recibe nada. */
  private static final Set<String> ADMINISTRABLES = Set.of(
    "nombre",
    "contacto_email",
    "contacto_telefono",
    "direccion",
    "hora_entrada",
    "hora_salida",
    "politica_cancelacion",
    "zona_horaria",
    "moneda",
    "latitud",
    "longitud");

  /** Lo único que la web pública puede leer. Las claves internas nunca salen por aquí. */
  private static final Set<String> PUBLICAS = ADMINISTRABLES;

  private final HotelConfigRepository repo;
  private final SqliteTransactionExecutor tx;

  public HotelConfigService(HotelConfigRepository repo, SqliteTransactionExecutor tx) {
    this.repo = repo;
    this.tx = tx;
  }

  public Map<String, String> administracion() {
    return filtrar(repo.valores(), ADMINISTRABLES);
  }

  public Map<String, String> publicos() {
    return filtrar(repo.valores(), PUBLICAS);
  }

  /** Para la web pública: si no hay nada vendible, lo dice en vez de mostrar llenos. */
  public boolean aLaVenta() { return repo.hayVenta(); }

  /**
   * Guarda la identidad en una sola transacción: o entran todos los valores válidos o no entra
   * ninguno. Un guardado a medias dejaría marca y contacto de hoteles distintos.
   */
  public Map<String, String> guardar(Map<String, String> valores) {
    if (valores == null || valores.isEmpty())
      throw new ConfiguracionInvalidaException("no hay datos del hotel para guardar");
    Map<String, String> normalizados = normalizar(valores);
    return tx.enTransaccion(estado -> {
      repo.guardarTodos(normalizados);
      return administracion();
    });
  }

  private Map<String, String> normalizar(Map<String, String> valores) {
    Map<String, String> normalizados = new LinkedHashMap<>();
    for (Map.Entry<String, String> entrada : valores.entrySet()) {
      String clave = entrada.getKey();
      if (!ADMINISTRABLES.contains(clave))
        throw new ConfiguracionInvalidaException("clave no administrable: " + clave);
      normalizados.put(clave, validar(clave, entrada.getValue()));
    }
    return normalizados;
  }

  private String validar(String clave, String valor) {
    String texto = valor == null ? "" : valor.trim();
    return switch (clave) {
      case "nombre" -> {
        if (texto.length() < 2 || texto.length() > 80)
          throw new ConfiguracionInvalidaException("el nombre necesita entre 2 y 80 caracteres");
        yield texto;
      }
      case "contacto_email" -> {
        if (!texto.isEmpty() && (!CORREO.matcher(texto).matches() || texto.length() > 254))
          throw new ConfiguracionInvalidaException("correo electrónico inválido");
        yield texto;
      }
      case "contacto_telefono" -> {
        if (!texto.isEmpty() && (!TELEFONO.matcher(texto).matches() || texto.length() > 30))
          throw new ConfiguracionInvalidaException("teléfono inválido");
        yield texto;
      }
      case "direccion" -> {
        if (texto.length() > 200)
          throw new ConfiguracionInvalidaException("la dirección no puede pasar de 200 caracteres");
        yield texto;
      }
      case "hora_entrada", "hora_salida" -> {
        if (!texto.isEmpty() && !HORA.matcher(texto).matches())
          throw new ConfiguracionInvalidaException("la hora debe tener formato HH:mm de 24 horas");
        yield texto;
      }
      case "politica_cancelacion" -> {
        if (texto.length() > 1000)
          throw new ConfiguracionInvalidaException("la política no puede pasar de 1000 caracteres");
        yield texto;
      }
      case "zona_horaria" -> {
        if (!texto.isEmpty()) {
          try {
            ZoneId.of(texto);
          } catch (DateTimeException e) {
            throw new ConfiguracionInvalidaException("zona horaria inválida: " + texto);
          }
        }
        yield texto;
      }
      case "moneda" -> {
        if (!texto.isEmpty() && !texto.matches("[A-Z]{3}"))
          throw new ConfiguracionInvalidaException("moneda inválida: use el código ISO 4217, p. ej. COP");
        yield texto;
      }
      case "latitud" -> {
        yield coordenada(texto, -90, 90, "latitud inválida (-90 a 90)");
      }
      case "longitud" -> {
        yield coordenada(texto, -180, 180, "longitud inválida (-180 a 180)");
      }
      default -> throw new ConfiguracionInvalidaException("clave no administrable: " + clave);
    };
  }

  /** Coordenada opcional para el mapa: vacía vale (hotel sin ubicar), el resto debe ser número. */
  private static String coordenada(String texto, double minimo, double maximo, String mensaje) {
    if (texto.isEmpty()) return texto;
    try {
      double valor = Double.parseDouble(texto);
      if (!Double.isFinite(valor) || valor < minimo || valor > maximo)
        throw new ConfiguracionInvalidaException(mensaje);
    } catch (NumberFormatException e) {
      throw new ConfiguracionInvalidaException(mensaje);
    }
    return texto;
  }

  private Map<String, String> filtrar(Map<String, String> valores, Set<String> claves) {    Map<String, String> filtrados = new LinkedHashMap<>();
    for (String clave : claves) {
      if (valores.containsKey(clave)) filtrados.put(clave, valores.get(clave));
    }
    return filtrados;
  }
}
