package co.hotel.hotel;

/** Un dato operativo del hotel no puede guardarse. El mensaje ya sirve para el panel. */
public class ConfiguracionInvalidaException extends IllegalArgumentException {
  public ConfiguracionInvalidaException(String mensaje) { super(mensaje); }
}
