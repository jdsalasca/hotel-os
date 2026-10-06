package co.hotel.ota;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Cliente HTTP de los conectores, probado contra un servidor HTTP real en localhost.
 *
 * Sin mocks: lo que importa de un conector es cómo se comporta contra un servidor que responde
 * de verdad, incluidos los códigos de error y los tiempos de espera.
 */
class HttpClienteOtaTest {

  private HttpServer servidor;
  private String base;
  private final AtomicInteger peticiones = new AtomicInteger();
  private volatile int responderCodigo = 200;
  private volatile String responderCuerpo = "{}";
  private final List<String> cabecerasRecibidas = new ArrayList<>();

  @BeforeEach
  void arrancarServidor() throws IOException {
    servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    servidor.createContext("/", exchange -> {
      peticiones.incrementAndGet();
      cabecerasRecibidas.add(exchange.getRequestHeaders().getFirst("Authorization"));
      byte[] cuerpo = responderCuerpo.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(responderCodigo, cuerpo.length);
      exchange.getResponseBody().write(cuerpo);
      exchange.close();
    });
    servidor.start();
    base = "http://127.0.0.1:" + servidor.getAddress().getPort();
  }

  @AfterEach
  void apagarServidor() {
    servidor.stop(0);
  }

  private HttpClienteOta cliente(int intentos) {
    return new HttpClienteOta(Duration.ofSeconds(3), intentos, Duration.ZERO);
  }

  @Test
  @DisplayName("una respuesta 2xx es un éxito y devuelve el cuerpo")
  void respuestaExitosaDevuelveCuerpo() {
    responderCuerpo = "{\"ok\":true}";
    var r = cliente(1).post(base + "/x", java.util.Map.of(), "{}");
    assertTrue(r.exitosa());
    assertEquals(200, r.codigo());
    assertEquals("{\"ok\":true}", r.cuerpo());
  }

  @Test
  @DisplayName("un 401 no se reintenta: repetir un rechazo de credenciales no lo arregla")
  void noReintentaErroresDeAutenticacion() {
    responderCodigo = 401;
    responderCuerpo = "{\"error\":\"invalid client_id\"}";
    var r = cliente(3).post(base + "/x", java.util.Map.of(), "{}");
    assertFalse(r.exitosa());
    assertEquals(401, r.codigo());
    assertEquals(1, peticiones.get(), "un 401 debe gastar un solo intento");
  }

  @Test
  @DisplayName("un 4xx del cliente no se reintenta, un 5xx sí")
  void reintentaSoloErroresDelServidor() {
    responderCodigo = 404;
    cliente(3).post(base + "/x", java.util.Map.of(), "{}");
    assertEquals(1, peticiones.get(), "404 no se reintenta");

    peticiones.set(0);
    responderCodigo = 503;
    cliente(3).post(base + "/x", java.util.Map.of(), "{}");
    assertEquals(3, peticiones.get(), "503 se reintenta hasta el límite");
  }

  @Test
  @DisplayName("un 503 que se recupera devuelve éxito y no error")
  void reintentaHastaRecuperar() {
    responderCodigo = 503;
    responderCuerpo = "{\"error\":\"temporal\"}";
    var cliente = cliente(3);
    // El servidor responde 503 siempre: se agota. Con un segundo intento yafunciona.
    assertFalse(cliente.post(base + "/x", java.util.Map.of(), "{}").exitosa());

    peticiones.set(0);
    responderCodigo = 200;
    assertTrue(cliente.post(base + "/x", java.util.Map.of(), "{}").exitosa());
    assertEquals(1, peticiones.get());
  }

  @Test
  @DisplayName("la cabecera Authorization viaja tal cual")
  void enviaCabeceras() {
    cliente(1).post(base + "/x", java.util.Map.of("Authorization", "Bearer abc.def.ghi"), "{}");
    assertEquals("Bearer abc.def.ghi", cabecerasRecibidas.get(0));
  }

  @Test
  @DisplayName("un tiempo de espera agotado se reporta como fallo, no como éxito")
  void tiempoDeEsperaAgotadoEsFallo() {
    var lento = new HttpClienteOta(Duration.ofMillis(200), 1, Duration.ZERO);
    HttpServer colgado = null;
    try {
      colgado = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      colgado.createContext("/", exchange -> {
        try { Thread.sleep(3000); } catch (InterruptedException ignored) { }
      });
      colgado.start();
      String urlColgado = "http://127.0.0.1:" + colgado.getAddress().getPort() + "/";
      var r = lento.post(urlColgado, java.util.Map.of(), "{}");
      assertFalse(r.exitosa(), "un timeout jamás se reporta como éxito");
    } catch (IOException e) {
      fail("no se pudo arrancar el servidor lento: " + e.getMessage());
    } finally {
      if (colgado != null) colgado.stop(0);
    }
  }

  @Test
  @DisplayName("el mensaje de error no filtra la cabecera de autorización")
  void elErrorNoFiltraSecretos() {
    responderCodigo = 401;
    responderCuerpo = "{\"error\":\"invalid client_secret: s3cr3t-value\"}";
    var r = cliente(1).post(base + "/x", java.util.Map.of("Authorization", "Bearer token-secreto"), "{}");
    String detalle = r.detalleParaBitacora();
    assertFalse(detalle.contains("token-secreto"));
    assertFalse(detalle.contains("s3cr3t-value"));
    assertTrue(detalle.contains("[REDACTADO]"));
  }
}