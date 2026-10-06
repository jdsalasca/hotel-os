package co.hotel.disponibilidad;

import java.time.LocalDate;

/** Regla anti-doble-reserva: intervalos [llegada,salida). ponytail: O(n) lineal, índice DB en Ronda 2 si volumen lo exige. */
public final class Solape {
  private Solape() {}
  public static boolean solapa(LocalDate aIni, LocalDate aFin, LocalDate bIni, LocalDate bFin) {
    return aIni.isBefore(bFin) && bIni.isBefore(aFin);
  }
}
