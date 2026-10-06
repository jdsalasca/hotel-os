package co.hotel.ota;

/** Último resultado de sincronización de un canal. Inmutable: no muta el canal. */
public record SyncResult(boolean exitosa, String resultado, String en) {

  public static SyncResult ok(String detalle) {
    return new SyncResult(true, Bitacora.sanear(detalle), java.time.LocalDateTime.now().toString());
  }

  public static SyncResult fallo(String detalle) {
    return new SyncResult(false, Bitacora.sanear(detalle), java.time.LocalDateTime.now().toString());
  }
}