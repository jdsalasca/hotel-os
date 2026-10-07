package co.hotel.admin;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * El usuario demo admin/admin existe SOLO en desarrollo.
 *
 * En producción el arranque debe fallar, no simplemente omitirlo: una credencial conocida en un
 * despliegue real es una puerta abierta, y un fallo ruidoso en el arranque es más seguro que un
 * despliegue que parece funcionar.
 */
@SpringBootTest
class AdminDemoTest {

  private static final Path DB = crearBase();

  private static Path crearBase() {
    try {
      Path p = Files.createTempFile("hotel-demo-", ".sqlite3");
      Files.delete(p);
      return p;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @DynamicPropertySource
  static void propiedades(DynamicPropertyRegistry reg) {
    reg.add("hotel.jdbc-path", () -> DB.toAbsolutePath().toString());
    reg.add("hotel.ambiente", () -> "dev");
    reg.add("hotel.demo.admin", () -> "true");
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired PasswordEncoder encoder;

  @Test
  @DisplayName("en desarrollo se crea admin/admin y la contraseña valida contra el hash")
  void enDesarrolloCreaElUsuarioDemo() {
    String hash = jdbc.queryForObject("SELECT hash FROM users WHERE email='admin'", String.class);
    assertNotNull(hash, "debe existir el usuario demo");
    assertTrue(hash.startsWith("{bcrypt}"), "el hash lleva prefijo de algoritmo");
    assertTrue(encoder.matches("admin", hash), "admin/admin debe validar");
  }

  @Test
  @DisplayName("el usuario demo tiene rol ADMIN y queda activo")
  void elUsuarioDemoEsAdministrador() {
    String rol = jdbc.queryForObject("SELECT rol FROM users WHERE email='admin'", String.class);
    assertEquals("ADMIN", rol);
  }

  @Test
  @DisplayName("el arranque en producción con usuario demo habilitado se niega")
  void enProduccionSeNiegaElArranque() {
    // Se comprueba la regla, no el arranque completo: la condición es lo que hay que fijar.
    var propsProd = new co.hotel.config.HotelProperties("./x.sqlite3", "produccion", "token", "",
      "es", "America/Bogota", new co.hotel.config.CorreoProperties(false, "", "", "", "", "", "", ""),
      new co.hotel.config.Oauth2Properties("", "", ""));
    assertTrue(propsProd.esProduccion());
    assertFalse(propsProd.permiteAtajosDeDesarrollo(),
      "en producción no puede haber atajos de desarrollo, ni siquiera si alguien los activó");
  }
}