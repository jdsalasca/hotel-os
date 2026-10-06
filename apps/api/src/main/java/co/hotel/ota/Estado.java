package co.hotel.ota;

/** Estados visibles en el panel. CONECTADO solo se alcanza con llamada autorizada exitosa. */
public enum Estado {
  NO_CONFIGURADO, ACCESO_PENDIENTE, SANDBOX, CONECTADO, ERROR, DESCONECTADO
}