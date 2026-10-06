package co.hotel.disponibilidad;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SolapeTest {
  @Test void rechazaSuperpuesta() {
    assertTrue(Solape.solapa(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-05"), LocalDate.parse("2026-11-04"), LocalDate.parse("2026-11-06")));
  }
  @Test void permiteSalidaIgualLlegada() {
    assertFalse(Solape.solapa(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-05"), LocalDate.parse("2026-11-05"), LocalDate.parse("2026-11-07")));
  }
}
