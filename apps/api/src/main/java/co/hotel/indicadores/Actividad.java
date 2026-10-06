package co.hotel.indicadores;

import java.time.LocalDate;

/** Actividad de adopción o difusión que el hotel confirma. */
public record Actividad(String tipo, String descripcion, LocalDate fecha, Integer participantes,
                        String confirmadaPor, boolean tieneDatosDeOrigen) {}