package co.hotel.ota;

/** Un mapeo no puede guardarse. El mensaje ya está redactado para mostrarse en el panel. */
public class MapeoInvalidoException extends IllegalArgumentException {
  public MapeoInvalidoException(String mensaje) { super(mensaje); }
}
