package co.hotel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuración del hotel. Todo lo que el hotel debe decidir vive aquí y en la tabla
 * hotel_config; nada de esto está en código ni en el repositorio.
 */
@ConfigurationProperties(prefix = "hotel")
public record HotelProperties(
    @DefaultValue("./data/hotel.sqlite3") String jdbcPath,
    @DefaultValue("") String adminInitToken,
    @DefaultValue("es") String idioma,
    @DefaultValue("America/Bogota") String zonaHoraria) {

  /** El token de arranque debe venir del entorno: en producción es obligatorio. */
  public boolean hayTokenInicial() { return adminInitToken != null && !adminInitToken.isBlank(); }
}