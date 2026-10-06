package co.hotel.ota;

/**
 * Un canal de venta. El contrato es mínimo a propósito: lo que los tres proveedores tienen en
 * común es pedir y comparar reservas; lo específico de cada uno vive en su conector.
 */
public interface ConectorOta {

  Canal canal();

  /** Última versión de la API del proveedor consultada al implementar este conector. */
  String versionApiConsultada();

  /** Sincroniza con el proveedor. Nunca devuelve éxito si el proveedor rechazó la operación. */
  ResultadoSync sincronizarReservas();
}