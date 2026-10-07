package co.hotel.seguridad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

/**
 * El endpoint público de reservas escribe en la base sin sesión. Sin límite, un script puede
 * agotar el inventario del hotel: todas las habitaciones quedan "reservadas" por correos que no
 * existen y los huéspedes reales ven que no hay disponibilidad.
 *
 * Cada prueba usa su propia IP y su propio tramo de fechas: la base y el contador son los mismos,
 * así que lo único que las separa es no pedir exactamente lo mismo dos veces.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LimiteReservasTest {

  private static final Path DB = crearBase();
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final AtomicInteger TRAMO = new AtomicInteger();

  /** El que usa LimiteReservas. Si cambia, el bucle de esta prueba deja de ser exacto. */
  private static final int LIMITE = LimiteReservas.MAX_POR_IP;

  private static Path crearBase() {
    try {
      Path p = Files.createTempFile("hotel-lim-", ".sqlite3");
      Files.delete(p);
      return p;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @DynamicPropertySource
  static void propiedades(DynamicPropertyRegistry reg) {
    reg.add("hotel.jdbc-path", () -> DB.toAbsolutePath().toString());
  }

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void habitaciones() {
    if (jdbc.queryForObject("SELECT COUNT(*) FROM rooms", Integer.class) == 0) {
      jdbc.update("INSERT INTO rooms(codigo, estado, nombre) VALUES('101','ACTIVA','Habitación 101')");
    }
  }

  /** Fechas distintas por intento: si no, el bloqueo podría deberse al inventario y no al límite. */
  private static String cuerpo(int numero) {
    int mes = 1 + (numero % 12);
    int dia = 1 + (numero / 12) % 27;
    return """
        {"email":"huesped%d@example.com","llegada":"2030-%02d-%02d","salida":"2030-%02d-%02d",\
        "huespedes":1,"roomId":1}""".formatted(numero, mes, dia, mes, dia + 1);
  }

  private MockHttpServletRequestBuilder intento(String ip, int numero) throws Exception {
    return post("/api/reservas").with(csrf())
      .with(req -> { req.setRemoteAddr(ip); return req; })
      .contentType(MediaType.APPLICATION_JSON).content(cuerpo(numero));
  }

  private int estados(String ip, int desde, int cuantos) throws Exception {
    int ultimo = 0;
    for (int i = 0; i < cuantos; i++) {
      ultimo = mvc.perform(intento(ip, desde + i)).andReturn().getResponse().getStatus();
    }
    return ultimo;
  }

  @Test
  @DisplayName("pasado el límite desde una IP, la reserva responde 429 y no escribe nada")
  void pasadoElLimiteSeBloquea() throws Exception {
    int tramo = TRAMO.incrementAndGet() * 1000;
    assertEquals(201, estados("10.0.0.1", tramo, LIMITE), "los primeros intentos deben entrar");

    int antes = jdbc.queryForObject("SELECT COUNT(*) FROM reservations", Integer.class);
    mvc.perform(intento("10.0.0.1", tramo + 500))
      .andExpect(status().isTooManyRequests())
      .andExpect(jsonPath("$.error").isNotEmpty());
    assertEquals(antes, jdbc.queryForObject("SELECT COUNT(*) FROM reservations", Integer.class),
      "una petición bloqueada no debe escribir en la base");
  }

  @Test
  @DisplayName("el límite es por IP: el bloqueo de una no cierra la web a las demás")
  void elLimiteEsPorIp() throws Exception {
    int tramo = TRAMO.incrementAndGet() * 1000;
    estados("10.0.0.2", tramo, LIMITE + 1);

    mvc.perform(intento("10.0.0.3", tramo + 500)).andExpect(status().isCreated());
  }
}