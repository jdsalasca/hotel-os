package co.hotel.inventario;

/** Error de validación del inventario o de las tarifas: el hotel debe corregir, no reintentar. */
public class DatosInvalidosException extends RuntimeException {
  public DatosInvalidosException(String mensaje) { super(mensaje); }
}