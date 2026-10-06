package co.hotel.ota;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * La bitácora de sincronización nunca guarda credenciales, tokens ni datos personales.
 * Nombres de huespedes: se guardan como referencia opaca.
 */
class BitacoraTest {

  @Test void redactaValorDeSecreto() {
    String s = Bitacora.sanear("fallo 401 con client_secret=abc123");
    assertFalse(s.contains("abc123"), "no debe conservar el valor del secret");
    assertTrue(s.contains("[REDACTADO]"));
  }

  @Test void redactaTokenConPrefijoDeProveedor() {
    String s = Bitacora.sanear("rechazado token sk-live-9999 en cabecera");
    assertFalse(s.contains("sk-live-9999"), "no debe conservar el token");
  }

  @Test void redactaAuthorization() {
    assertFalse(Bitacora.sanear("Authorization: Bearer eyJhbGciOi.abc").contains("eyJhbGciOi.abc"));
  }

  @Test void redactaEmail() {
    assertFalse(Bitacora.sanear("huesped ana@example.com cancelo").contains("ana@example.com"));
  }

  @Test void redactaTelefonoLargo() {
    assertFalse(Bitacora.sanear("telefono +573001234567").contains("573001234567"));
  }

  @Test void conservaTextoUtilParaDiagnosticar() {
    String s = Bitacora.sanear("HTTP 503 del proveedor en endpoint OTA_HotelResNotif");
    assertTrue(s.contains("HTTP 503"));
    assertTrue(s.contains("OTA_HotelResNotif"));
  }

  @Test void nullEsNullYTieneTopeDeLongitud() {
    assertNull(Bitacora.sanear(null));
    assertTrue(Bitacora.sanear("x".repeat(2000)).length() <= 520);
  }
}