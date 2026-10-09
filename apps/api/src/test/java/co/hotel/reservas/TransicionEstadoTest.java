package co.hotel.reservas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Los estados de una reserva no se cambian en cualquier orden.
 *
 * El servicio aceptaba cualquier estado nuevo sin mirar el anterior, así que una reserva cancelada
 * se podía volver a confirmar desde el panel. Y eso no es solo un dato mal puesto: al volver a
 * CONFIRMADA la reserva vuelve a ocupar la habitación (`vigente()`), con lo que el hotel se queda
 * sin vender una fecha que ya había liberado.
 *
 * Las reglas viven en el dominio y no en el panel: esconder los botones es Genesis, pero quien
 * decide es el servidor, que es el único que no se puede esquivar.
 */
@DisplayName("transiciones de estado de una reserva")
class TransicionEstadoTest {

  @Nested
  @DisplayName("permitidas")
  class Permitidas {

    @Test
    @DisplayName("PENDIENTE admite confirmar, cancelar y rechazar")
    void desdePendiente() {
      assertEquals(3, EstadoReserva.PENDIENTE.desde().size());
      assertTrue(EstadoReserva.PENDIENTE.puede(EstadoReserva.CONFIRMADA));
      assertTrue(EstadoReserva.PENDIENTE.puede(EstadoReserva.CANCELADA));
      assertTrue(EstadoReserva.PENDIENTE.puede(EstadoReserva.RECHAZADA));
    }

    @Test
    @DisplayName("CONFIRMADA se puede cancelar o cerrar como no presentada")
    void confirmadaSePuedeCerrar() {
      EstadoReserva noPresentada = EstadoReserva.valueOf("NO_PRESENTADA");
      assertEquals(2, EstadoReserva.CONFIRMADA.desde().size());
      assertTrue(EstadoReserva.CONFIRMADA.puede(EstadoReserva.CANCELADA));
      assertTrue(EstadoReserva.CONFIRMADA.puede(noPresentada));
    }

    @ParameterizedTest
    @EnumSource(value = EstadoReserva.class, names = {"PENDIENTE", "CONFIRMADA"})
    @DisplayName("cancelar se puede siempre desde los estados vivos")
    void cancelarSiempre(EstadoReserva origen) {
      assertTrue(origen.puede(EstadoReserva.CANCELADA),
        "cancelar desde " + origen + " tiene que estar permitido");
    }
  }

  @Nested
  @DisplayName("prohibidas")
  class Prohibidas {

    @ParameterizedTest
    @EnumSource(value = EstadoReserva.class, names = {"CANCELADA", "RECHAZADA", "NO_PRESENTADA"})
    @DisplayName("los estados terminales no vuelven a la vida")
    void losTerminalesSonTerminales(EstadoReserva terminal) {
      assertTrue(terminal.esTerminal());
      assertFalse(terminal.vigente());
      assertEquals(0, terminal.desde().size(),
        "desde " + terminal + " no se puede cambiar a nada: el hotel ya liberó la fecha");
    }

    @Test
    @DisplayName("CONFIRMADA no vuelve a PENDIENTE")
    void confirmadaNoVuelve() {
      assertFalse(EstadoReserva.CONFIRMADA.puede(EstadoReserva.PENDIENTE),
        "desconfirmar no es una operación: el huésped ya tuvo su confirmación");
    }
  }

  @Test
  @DisplayName("intentarlo devuelve un error que dice de dónde se viene")
  void elErrorExplicaElMotivo() {
    ExcepcionDeEstado e = assertThrows(ExcepcionDeEstado.class,
      () -> EstadoReserva.validar(EstadoReserva.CANCELADA, EstadoReserva.CONFIRMADA));
    assertTrue(e.getMessage().contains("CANCELADA"),
      "el mensaje tiene que decir de dónde se viene: " + e.getMessage());
    assertTrue(e.getMessage().contains("CONFIRMADA"), "y adónde se quería ir: " + e.getMessage());
  }
}
