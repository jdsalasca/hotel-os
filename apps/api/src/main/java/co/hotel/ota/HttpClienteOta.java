package co.hotel.ota;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Cliente HTTP para los conectores OTA. Sin reintentos a ciegas y sin reportar éxito cuando el
 * proveedor rechazó la operación.
 *
 * Política: se reintenta solo lo que tiene sentido reintentar (5xx y fallos de red). Un 4xx es una
 * decisión del proveedor —credenciales, validación— y repetirlo solo gasta cuota.
 */
public class HttpClienteOta {
  private final Duration timeout;
  private final int intentosMaximos;
  private final Duration esperaEntreIntentos;
  private final HttpClient http;

  public HttpClienteOta(Duration timeout, int intentosMaximos, Duration esperaEntreIntentos) {
    this.timeout = timeout;
    this.intentosMaximos = Math.max(1, intentosMaximos);
    this.esperaEntreIntentos = esperaEntreIntentos;
    this.http = HttpClient.newBuilder()
      .connectTimeout(timeout)
      .followRedirects(HttpClient.Redirect.NORMAL)
      .build();
  }

  public Respuesta post(String url, Map<String, String> cabeceras, String cuerpo) {
    return enviar("POST", url, cabeceras, cuerpo);
  }

  public Respuesta get(String url, Map<String, String> cabeceras) {
    return enviar("GET", url, cabeceras, null);
  }

  private Respuesta enviar(String metodo, String url, Map<String, String> cabeceras, String cuerpo) {
    Exception ultimoFallo = null;
    for (int intento = 1; intento <= intentosMaximos; intento++) {
      try {
        HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create(url))
          .timeout(timeout)
          .header("Accept", "application/json, application/xml");
        cabeceras.forEach(peticion::header);
        // El Content-Type lo manda quien llama: si se fuerza aquí otro, la petición sale con dos
        // valores y el proveedor la rechaza (así se rompía el canje OAuth2 de Gmail). Solo hay
        // valor por defecto cuando nadie lo fijó.
        if (cuerpo == null) {
          peticion.method(metodo, HttpRequest.BodyPublishers.noBody());
        } else {
          boolean sinTipo = cabeceras.keySet().stream().noneMatch(k -> k.equalsIgnoreCase("Content-Type"));
          if (sinTipo) peticion.header("Content-Type", "application/json");
          peticion.method(metodo, HttpRequest.BodyPublishers.ofString(cuerpo));
        }

        HttpResponse<String> respuesta = http.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
        Respuesta r = new Respuesta(respuesta.statusCode(), respuesta.body(),
          metodo + " " + url + " -> HTTP " + respuesta.statusCode(), esExito(respuesta.statusCode()));
        if (!r.exitosa() && !reintentable(respuesta.statusCode())) return r;
        if (r.exitosa()) return r;
        ultimoFallo = null;
      } catch (Exception e) {
        ultimoFallo = e;
        if (intento == intentosMaximos) break;
      }
      esperar();
    }
    String motivo = ultimoFallo == null ? "sin respuesta favorable del proveedor" : ultimoFallo.toString();
    return new Respuesta(0, "",
      metodo + " " + url + " -> " + motivo, false);
  }

  private static boolean esExito(int codigo) { return codigo >= 200 && codigo < 300; }

  /** 429 y 5xx son transitorios; el resto no se reintenta. */
  private static boolean reintentable(int codigo) { return codigo == 429 || codigo >= 500; }

  private void esperar() {
    if (esperaEntreIntentos.isZero()) return;
    try {
      Thread.sleep(esperaEntreIntentos.toMillis());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /** Respuesta del proveedor. El detalle ya viene saneado para poder guardarse en la bitácora. */
  public record Respuesta(int codigo, String cuerpo, String detalle, boolean exitosa) {

    /** Nunca incluye cabeceras de autenticación: solo URL, método y resultado. */
    public String detalleParaBitacora() { return Bitacora.sanear(detalle + " cuerpo=" + cuerpo); }
  }
}