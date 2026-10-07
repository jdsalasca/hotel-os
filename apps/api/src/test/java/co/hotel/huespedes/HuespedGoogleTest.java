package co.hotel.huespedes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Los huéspedes que entran con Google. Al primer login se crea el usuario solo, y a partir de ahí
 * puede ver sus reservas por sesión, sin tener que acordarse del código.
 *
 * La reserva anónima sigue funcionando: `usuario_id` es nullable a propósito, porque quien reserva
 * sin entrar no tiene cuenta y esa reserva no puede quedarse huérfana ni romperse.
 */
@SpringBootTest(properties = {
  "hotel.oauth2.client-id=cliente-de-prueba",
  "hotel.oauth2.client-secret=secreto-de-prueba"
})
@AutoConfigureMockMvc
class HuespedGoogleTest {

  private static final Path DB = crearBase();

  private static Path crearBase() {
    try {
      Path p = Files.createTempFile("hotel-huesp-", ".sqlite3");
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

  private OAuth2AuthenticationToken ficha(String sub, String email, String nombre) {
    var principal = new DefaultOAuth2User(java.util.List.of(),
      java.util.Map.of("sub", sub, "email", email, "name", nombre), "email");
    return new OAuth2AuthenticationToken(principal, java.util.List.of(),
      "google-huesped");
  }

  @Test
  @DisplayName("el primer login crea el usuario y devuelve quién es")
  void primerLoginCreaElUsuario() throws Exception {
    var manejador = new ManejadorHuespedOauth2(new UsuariosHuespedRepository(jdbc));
    var respuesta = new org.springframework.mock.web.MockHttpServletResponse();

    manejador.onAuthenticationSuccess(new org.springframework.mock.web.MockHttpServletRequest(),
      respuesta, ficha("sub-ana", "ana@example.com", "Ana Pérez"));

    Integer filas = jdbc.queryForObject("SELECT COUNT(*) FROM usuarios WHERE google_sub=?", Integer.class,
      "sub-ana");
    assertEquals(1, filas, "el primer login debe crear el usuario");
    assertEquals("ana@example.com", jdbc.queryForObject(
      "SELECT email FROM usuarios WHERE google_sub=?", String.class, "sub-ana"));
    assertEquals("Ana Pérez", jdbc.queryForObject(
      "SELECT nombre FROM usuarios WHERE google_sub=?", String.class, "sub-ana"));
  }

  @Test
  @DisplayName("entrar otra vez no duplica el usuario")
  void elSegundoLoginNoDuplica() throws Exception {
    var manejador = new ManejadorHuespedOauth2(new UsuariosHuespedRepository(jdbc));
    var req = new org.springframework.mock.web.MockHttpServletRequest();
    // Una respuesta por login: reutilizar la misma falla al intentar el segundo redirect.
    manejador.onAuthenticationSuccess(req, new org.springframework.mock.web.MockHttpServletResponse(),
      ficha("sub-bruno", "bruno@example.com", "Bruno"));
    manejador.onAuthenticationSuccess(req, new org.springframework.mock.web.MockHttpServletResponse(),
      ficha("sub-bruno", "bruno@example.com", "Bruno"));

    assertEquals(1, jdbc.queryForObject(
      "SELECT COUNT(*) FROM usuarios WHERE google_sub='sub-bruno'", Integer.class));
  }

  @Test
  @DisplayName("la reserva anónima se guarda sin usuario_id, no se rompe")
  void laReservaAnonimaSigueSiendoValida() {
    jdbc.update("INSERT INTO rooms(codigo, estado, nombre) VALUES('101','ACTIVA','Habitación 101')");
    jdbc.update("INSERT INTO reservations(codigo,email,nombre,llegada,salida,huespedes,estado,"
      + "origen,idempotencia,creado_en) VALUES('H-ANON1','ana@example.com','Ana',"
      + "'2035-05-10','2035-05-12',1,'PENDIENTE','WEB','k-anon','2035-01-01 00:00:00')");

    Integer id = jdbc.queryForObject("SELECT usuario_id FROM reservations WHERE codigo='H-ANON1'",
      (rs, n) -> (Integer) rs.getObject("usuario_id"));
    assertNull(id, "una reserva sin sesión no tiene usuario: la columna admite NULL a propósito");
  }

  @Test
  @DisplayName("tras entrar con Google, la sesión guarda el contexto: si no, /api/yo daría 401")
  void laSesionSobreviveAlRedireccion() throws Exception {
    var manejador = new ManejadorHuespedOauth2(new UsuariosHuespedRepository(jdbc));
    var req = new org.springframework.mock.web.MockHttpServletRequest();
    req.getSession(true);
    var res = new org.springframework.mock.web.MockHttpServletResponse();

    manejador.onAuthenticationSuccess(req, res, ficha("sub-persis", "persis@example.com", "Persis"));

    var sesion = (org.springframework.mock.web.MockHttpSession) req.getSession(false);
    var guardado = sesion.getAttribute(
      org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
    assertTrue(guardado != null,
      "el manejador tiene que guardar el contexto en la sesión a mano: el redirect por sí solo no "
        + "lo hace desde Spring Security 6, y /api/yo devolvería 401 al volver de Google");
    try {
      var contexto = (org.springframework.security.core.context.SecurityContext) guardado;
      assertEquals("persis@example.com",
        contexto.getAuthentication().getName(), "la sesión debe saber quién es el huésped");
    } finally {
      org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
  }

  @Test
  @DisplayName("nunca se redirige fuera del sitio: un `vuelve` ajeno es un redirect abierto")
  void elVuelveNoPuedeSalirDelSitio() throws Exception {
    var manejador = new ManejadorHuespedOauth2(new UsuariosHuespedRepository(jdbc));
    assertEquals("/mis-reservas", destinoCon(manejador, null));
    assertEquals("/mis-reservas", destinoCon(manejador, "https://sitio-falso.example/robo"));
    assertEquals("/mis-reservas", destinoCon(manejador, "//sitio-falso.example/robo"));
    assertEquals("/mis-reservas", destinoCon(manejador, "javascript:alert(1)"));
    // Una ruta interna sí se respeta: es el caso legítimo de "vuelve a donde estabas".
    assertEquals("/reserva", destinoCon(manejador, "/reserva"));
  }

  private String destinoCon(ManejadorHuespedOauth2 manejador, String vuelve) throws Exception {
    var req = new org.springframework.mock.web.MockHttpServletRequest();
    req.getSession(true);
    if (vuelve != null) req.setParameter("vuelve", vuelve);
    var res = new org.springframework.mock.web.MockHttpServletResponse();
    try {
      manejador.onAuthenticationSuccess(req, res,
        ficha("sub-redir-" + String.valueOf(vuelve), "redir@example.com", "Redir"));
    } finally {
      org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
    return res.getRedirectedUrl();
  }

  @Test
  @DisplayName("sin sesión, /api/yo y /api/mis-reservas responden 401, no datos")
  void sinSesionNoDevuelveNada() throws Exception {
    mvc.perform(get("/api/yo")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/mis-reservas")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("con sesión de huésped, /api/yo dice quién es y /api/mis-reservas lista las suyas")
  void conSesionVeSusReservas() throws Exception {
    jdbc.update("INSERT INTO usuarios(google_sub,email,nombre,creado_en) VALUES('sub-mia','mia@example.com',"
      + "'María','2035-01-01')");
    long id = jdbc.queryForObject("SELECT id FROM usuarios WHERE google_sub='sub-mia'", Long.class);
    jdbc.update("INSERT INTO rooms(codigo, estado, nombre) VALUES('301','ACTIVA','Habitación 301')");
    jdbc.update("INSERT INTO reservations(codigo,email,nombre,llegada,salida,huespedes,estado,"
      + "origen,idempotencia,creado_en,usuario_id) VALUES('H-MIA1','mia@example.com','María',"
      + "'2035-07-10','2035-07-12',2,'PENDIENTE','WEB','k-mia','2035-01-01',?)", id);

    var huesped = user("mia@example.com").roles("HUESPED");

    mvc.perform(get("/api/yo").with(huesped))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.email").value("mia@example.com"))
      .andExpect(jsonPath("$.nombre").value("María"))
      .andExpect(jsonPath("$.tieneReservas").value(true));

    mvc.perform(get("/api/mis-reservas").with(huesped))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.reservas[0].codigo").value("H-MIA1"));
  }

  @Test
  @DisplayName("las reservas del huésped se listan por su usuario_id, no por su correo")
  void lasReservasSonDelUsuarioQueIngreso() throws Exception {
    long usuario = jdbc.queryForObject(
      "INSERT INTO usuarios(google_sub,email,nombre,creado_en) VALUES('sub-luis','luis@example.com',"
        + "'Luis','2035-01-01') RETURNING id", Long.class);
    jdbc.update("INSERT INTO rooms(codigo, estado, nombre) VALUES('201','ACTIVA','Habitación 201')");
    jdbc.update("INSERT INTO reservations(codigo,email,nombre,llegada,salida,huespedes,estado,"
      + "origen,idempotencia,creado_en,usuario_id) VALUES('H-LUIS1','luis@example.com','Luis',"
      + "'2035-06-10','2035-06-12',1,'PENDIENTE','WEB','k-luis','2035-01-01',?)", usuario);
    // Mismo correo, otra persona: no debe ver la de Luis.
    jdbc.update("INSERT INTO usuarios(google_sub,email,nombre,creado_en) VALUES('sub-otro',"
      + "'luis@example.com','Impostor','2035-01-01')");

    long impostor = jdbc.queryForObject(
      "SELECT id FROM usuarios WHERE google_sub='sub-otro'", Long.class);
    Integer ajenas = jdbc.queryForObject(
      "SELECT COUNT(*) FROM reservations WHERE usuario_id=?", Integer.class, impostor);
    assertEquals(0, ajenas, "las reservas son del usuario, no del correo: dos cuentas con el mismo "
      + "correo no se mezclan");
  }
}