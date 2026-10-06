package co.hotel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuración del hotel. Todo lo que el hotel decide vive aquí y en la tabla hotel_config;
 * nada de esto está en el código ni en el repositorio.
 *
 * @param ambiente        dev | produccion. En produccion se niegan los atajos de desarrollo.
 * @param correo          configuración del envío de correo (OAuth2 de Google, sin valores por defecto)
 * @param adminInitToken  secreto de arranque del primer administrador
 */
@ConfigurationProperties(prefix = "hotel")
public record HotelProperties(
    @DefaultValue("./data/hotel.sqlite3") String jdbcPath,
    @DefaultValue("dev") String ambiente,
    @DefaultValue("") String adminInitToken,
    @DefaultValue("es") String idioma,
    @DefaultValue("America/Bogota") String zonaHoraria,
    @DefaultValue CorreoProperties correo) {

  /** El token de arranque debe venir del entorno: en producción es obligatorio. */
  public boolean hayTokenInicial() { return adminInitToken != null && !adminInitToken.isBlank(); }

  public boolean esProduccion() { return "produccion".equalsIgnoreCase(ambiente); }

  /**
   * Atajos de desarrollo (usuario demo) solo fuera de producción. Esto es lo que impide que
   * admin/admin exista algún día en el hotel real: no es una convención, es una condición.
   */
  public boolean permiteAtajosDeDesarrollo() { return !esProduccion(); }
}