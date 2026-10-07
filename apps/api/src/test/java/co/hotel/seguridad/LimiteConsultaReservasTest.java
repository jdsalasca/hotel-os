package co.hotel.seguridad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
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
import tools.jackson.databind.ObjectMapper;

/**
 * Consultar una reserva exige su código y el correo. Las claves se generan con
 * {@code H-} más ocho caracteres hexadecimales, o sea 32 bits: el espacio de 4 300 millones se
 * agota mirando códigos, y el correo se comprueba en mayúsculas y minúsculas. Por eso una
 * consulta pública necesita el mismo tope por IP que una escritura, o el endpoint se convierte en
 * un oráculo de "qué correos están en el sistema".
 */
@SpringBootTest
@AutoConfigureMockMvc
class LimiteConsultaReservasTest {

  private static final Path DB = crearBase();
  private static final ObjectMapper JSON = new ObjectMapper();

  private static Path crearBase() {
    try {
      Path p = Files.createTempFile("hotel-cons-", ".sqlite3");
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
  void inventario() {
    if (jdbc.queryForObject("SELECT COUNT(*) FROM rooms", Integer.class) == 0) {
      jdbc.update("INSERT INTO rooms(codigo, estado, nombre) VALUES('101','ACTIVA','Habitación 101')");
    }
  }

  /** static porque JUnit crea una instancia por prueba: con un campo de instancia cada una arrancaba
   *  en el mismo tramo y la segunda recibía 409 por inventario, no por el motivo que se mide. */
  private static int sec = 1000;

  /**
   * Cada reserva usa fechas propias: la base es compartida dentro de la clase y una habitación solo
   * puede estar ocupada una vez por tramo, así que repetir fechas daría 409 por inventario.
   */
  /**
   * Cada llamada usa un tramo distinto de un año, así que ninguna reserva choca con otra por
   * inventario. Con una sola habitación en la base, repetir fechas daría 409 y la prueba fallaría
   * por un motivo que no es el que está midiendo.
   */
  private String crearReserva() throws Exception {
    sec += 1;
    int mes = 1 + (sec % 12);
    int dia = 1 + (sec / 12) % 27;
    String cuerpo = mvc.perform(post("/api/reservas").with(csrf())
        .with(req -> { req.setRemoteAddr("10.1.0.1"); return req; })
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("email", "huesped@example.com", "nombre", "Huesped",
          "llegada", "2035-%02d-%02d".formatted(mes, dia),
          "salida", "2035-%02d-%02d".formatted(mes, dia + 1), "huespedes", 1, "roomId", 1))))
      .andExpect(status().isCreated())
      .andReturn().getResponse().getContentAsString();
    return JSON.readTree(cuerpo).get("codigo").asText();
  }

  

  private int intentos(String ip, String codigo, int cuantos) throws Exception {
    int ultimo = 0;
    for (int i = 0; i < cuantos; i++) {
      ultimo = mvc.perform(get("/api/reservas/" + codigo).param("email", "adivinado@example.com")
          .with(req -> { req.setRemoteAddr(ip); return req; }))
        .andReturn().getResponse().getStatus();
    }
    return ultimo;
  }

  private int limite = LimiteConsultasPublicas.MAX_POR_MINUTO;

  @Test
  @DisplayName("acertar códigos y correos ajenos se topa con un 429")
  void adivinarUnaReservaAjenaSeTope() throws Exception {
    String codigo = crearReserva();

    assertEquals(404, intentos("10.1.0.9", codigo, limite), "un correo ajeno da 404, no la reserva");
    assertEquals(429, intentos("10.1.0.9", codigo, 1),
      "seguir probando correos ajenos debe toparse");
  }

  @Test
  @DisplayName("el tope de consulta no cierra la web a otras conexiones")
  void elTopeEsPorIp() throws Exception {
    String codigo = crearReserva();
    intentos("10.1.0.10", codigo, limite + 1);

    mvc.perform(get("/api/reservas/" + codigo).param("email", "huesped@example.com")
        .with(req -> { req.setRemoteAddr("10.1.0.11"); return req; }))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.codigo").value(codigo));
  }

  @Test
  @DisplayName("el tope va más allá de la reserva: la disponibilidad tampoco es ilimitada")
  void laDisponibilidadTambienSeTope() throws Exception {
    for (int i = 1; i <= limite; i++) {
      int dia = 1 + (i % 27);
      int status = mvc.perform(get("/api/disponibilidad")
          .param("llegada", "2036-06-%02d".formatted(dia)).param("salida", "2036-06-%02d".formatted(dia + 1))
          .param("huespedes", "2")
          .with(req -> { req.setRemoteAddr("10.1.0.12"); return req; }))
        .andReturn().getResponse().getStatus();
      assertEquals(200, status, "la lectura " + i + " debe entrar");
    }
    assertEquals(429, mvc.perform(get("/api/disponibilidad")
        .param("llegada", "2036-07-10").param("salida", "2036-07-12").param("huespedes", "2")
        .with(req -> { req.setRemoteAddr("10.1.0.12"); return req; }))
      .andReturn().getResponse().getStatus(), "la disponibilidad también debe toparse");
  }
}