package co.hotel.reservas;

/**
 * Se pide un cambio de estado que las reglas del dominio no permiten.
 *
 * Es un 409 y no un 400: la petición está bien formada, el estado que se pide existe, pero no desde
 * aquí. Un 400 sugeriría que el hotel escribió mal el estado y que reintentando vale.
 */
public class ExcepcionDeEstado extends RuntimeException {

  public ExcepcionDeEstado(String mensaje) {
    super(mensaje);
  }
}