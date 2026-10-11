package co.hotel.correo;

import co.hotel.reservas.CrearReserva;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * El aviso que recibe el huésped al solicitar una reserva.
 *
 * En una reserva directa no hay plataforma intermediaria que le mande un correo: si el hotel
 * no lo hace, el huésped se queda con un código en una pantalla y sin ninguna constancia. Por
 * eso esto vive acoplado al alta de la reserva y no como una tarea aparte.
 *
 * Solo redacta el mensaje. Enviarlo es de {@link CorreoService}, que ya devuelve un
 * {@link ResultadoEnvio} con el motivo si falla en vez de propagar la excepción.
 *
 * No es un bean: lo construye {@code ReservaService}, que es quien sabe cuando avisar.
 */
public class CorreoConfirmacion {

  private static final Logger log = LoggerFactory.getLogger(CorreoConfirmacion.class);

  private final co.hotel.hotel.HotelConfigService hotel;

  public CorreoConfirmacion(co.hotel.hotel.HotelConfigService hotel) {
    this.hotel = hotel;
  }

  /** El asunto, con el nombre del hotel si lo tiene configurado. */
  public String asunto() {
    return nombreHotel() + ": solicitud de reserva recibida";
  }

  /**
   * El cuerpo va en texto plano: confirma la recepción de la solicitud sin prometer que ya
   * esté confirmada, y se lee igual en cualquier cliente.
   */
  public String cuerpo(String codigo, CrearReserva datos, LocalDate llegada, LocalDate salida) {
    return """
      Hola %s:

      Tu solicitud de reserva en %s fue recibida.

      Estado: Pendiente de confirmación.

        Código: %s
        Llegada: %s
        Salida: %s
        Huéspedes: %d

      Puedes consultar o cambiar tus fechas con el código y tu correo:
      %s/consulta

      Si necesitas algo, responde a este correo.
      """.formatted(datos.nombre(), nombreHotel(), codigo, llegada, salida, datos.huespedes(),
      basePublica());
  }

  private String nombreHotel() {
    String nombre = hotel.publicos().get("nombre");
    return nombre == null || nombre.isBlank() ? "el hotel" : nombre.trim();
  }

  /** Sin base configurada se deja el aviso sin URL: mejor un texto sin enlace que uno roto. */
  private String basePublica() {
    return System.getenv().getOrDefault("HOTEL_BASE_URL", "https://hotel.eridu.top");
  }
}
