package co.hotel.correo;

import static org.junit.jupiter.api.Assertions.*;

import co.hotel.reservas.CrearReserva;
import co.hotel.reservas.Origen;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * El huesped de una reserva directa no recibe ningun correo: no hay plataforma intermediaria
 * que se lo mande. Este archivo fija el contenido del aviso que si le llega.
 *
 * La base se levanta con el contexto real para que el nombre del hotel salga de verdad: si el
 * hotel no tiene nombre configurado, el aviso no debe decir "null".
 */
@SpringBootTest
class CorreoConfirmacionTest {

  @DynamicPropertySource
  static void propiedades(DynamicPropertyRegistry reg) {
    reg.add("hotel.jdbc-path", () -> {
      try {
        var p = java.nio.file.Files.createTempFile("hotel-confirmacion-", ".sqlite3");
        java.nio.file.Files.delete(p);
        return p.toAbsolutePath().toString();
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    });
  }

  @Autowired private JdbcTemplate jdbc;

  private CorreoConfirmacion correo() {
    return new CorreoConfirmacion(new co.hotel.hotel.HotelConfigService(
      new co.hotel.hotel.HotelConfigRepository(jdbc), null));
  }

  private CrearReserva datos() {
    return new CrearReserva("ana@example.com", "Ana", LocalDate.parse("2026-11-01"),
      LocalDate.parse("2026-11-05"), 2, Origen.WEB, "k", 1);
  }

  @Test
  @DisplayName("el aviso lleva el codigo y las fechas, que es lo que el huesped necesita")
  void llevaCodigoYFechas() {
    var d = datos();
    String cuerpo = correo().cuerpo("H-ABC123", d, d.llegada(), d.salida());

    assertTrue(cuerpo.contains("H-ABC123"), "sin el codigo el huesped no puede consultar: " + cuerpo);
    assertTrue(cuerpo.contains("2026-11-01"), cuerpo);
    assertTrue(cuerpo.contains("2026-11-05"), cuerpo);
    assertTrue(cuerpo.contains("Ana"), cuerpo);
  }

  @Test
  @DisplayName("el aviso dice como consultar la reserva sin depender de la cuenta")
  void diceComoConsultar() {
    var d = datos();
    String cuerpo = correo().cuerpo("H-ABC123", d, d.llegada(), d.salida());

    // Quien reserva sin cuenta de Google solo tiene el codigo y el correo: la ruta de
    // consulta por codigo es la que le deja recuperar la reserva.
    assertTrue(cuerpo.contains("/consulta"), cuerpo);
  }

  @Test
  @DisplayName("sin nombre de hotel el aviso no dice null")
  void sinNombreDeHotelNoDiceNull() {
    var d = datos();
    String cuerpo = correo().cuerpo("H-ABC123", d, d.llegada(), d.salida());

    assertFalse(cuerpo.contains("null"), cuerpo);
    assertNotNull(correo().asunto());
    assertFalse(correo().asunto().isBlank());
  }
}
