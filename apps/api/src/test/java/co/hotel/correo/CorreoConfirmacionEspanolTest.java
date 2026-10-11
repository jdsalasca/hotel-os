package co.hotel.correo;

import static org.junit.jupiter.api.Assertions.*;

import co.hotel.reservas.CrearReserva;
import co.hotel.reservas.Origen;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * El correo de confirmación es lo único que recibe quien reserva sin cuenta: no hay
 * plataforma intermediaria que le escriba. Si el español sale corrupto, el huésped lee
 * símbolos raros en el único mensaje que tiene y no sabe si su reserva existe.
 *
 * Estos tests fijan el texto. No comprueban "que haya algo", comprueban las palabras
 * acentuadas exactas que deben salir.
 *
 * Se añadieron porque el español del correo no estaba fijado por ninguna prueba: una
 * codificación mal cerrada habría pasado desapercibida. Comprobado su utilidad rompiendo la
 * codificación a propósito —fallan dos de estos tres tests— y restaurando después.
 */
@SpringBootTest
class CorreoConfirmacionEspanolTest {

  @DynamicPropertySource
  static void propiedades(DynamicPropertyRegistry reg) {
    reg.add("hotel.jdbc-path", () -> {
      try {
        var p = Files.createTempFile("hotel-espanol-", ".sqlite3");
        Files.delete(p);
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
    return new CrearReserva("ana@ejemplo.com", "Ana", LocalDate.parse("2026-11-01"),
      LocalDate.parse("2026-11-05"), 2, Origen.WEB, "k", 1);
  }

  @Test
  @DisplayName("el correo sale en español correcto, con sus tildes")
  void correoEnEspanolCorrecto() {
    String cuerpo = correo().cuerpo("H-ABC123", datos(), LocalDate.parse("2026-11-01"),
      LocalDate.parse("2026-11-05"));

    // Las palabras acentuadas del mensaje, tal como salen.
    assertTrue(cuerpo.contains("solicitud"), cuerpo);
    assertTrue(cuerpo.contains("confirmación"), cuerpo);
    assertTrue(cuerpo.contains("Código"), cuerpo);
    assertTrue(cuerpo.contains("Huéspedes"), cuerpo);
    assertTrue(cuerpo.contains("código"), cuerpo);

    // Y ni un símbolo de reemplazo: si la codificación se rompe, aparece U+FFFD.
    assertFalse(cuerpo.contains("\uFFFD"), cuerpo);
  }

  @Test
  @DisplayName("el fuente del correo está en UTF-8, no en latin-1")
  void fuenteEnUtf8() throws Exception {
    // Maven compila con UTF-8 (pom.xml). Si alguien pega el texto en latin-1, javac
    // sustituye los acentos y el correo sale corrupto sin que falle ninguna prueba.
    Path p = Path.of("src/main/java/co/hotel/correo/CorreoConfirmacion.java");
    if (!Files.exists(p)) p = Path.of("../src/main/java/co/hotel/correo/CorreoConfirmacion.java");
    String texto = Files.readString(p, StandardCharsets.UTF_8);

    assertTrue(texto.contains("confirmación"),
      "el fuente debe tener 'confirmación' en UTF-8; se leyó: " + texto.lines()
        .filter(l -> l.contains("Pendiente")).findFirst().orElse("(sin línea)"));
    assertTrue(texto.contains("Código"), "falta 'Código' en el fuente");
    // El carácter de reemplazo delata una lectura con codificación ajena.
    assertFalse(texto.contains("\uFFFD"), "el fuente tiene símbolos de reemplazo");
  }

  @Test
  @DisplayName("el asunto también sale en español")
  void asuntoEnEspanol() {
    assertNotNull(correo().asunto());
    assertFalse(correo().asunto().isBlank());
    assertFalse(correo().asunto().contains("\uFFFD"));
  }
}
