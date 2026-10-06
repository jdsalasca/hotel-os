package co.hotel.inventario;

/**
 * Oferta de una habitación con su precio total ya calculado.
 *
 * Solo se construye cuando el hotel tiene tarifa configurada para **todas** las noches del
 * periodo: es preferible no ofrecer nada antes que inventar o dejar sin definir un precio.
 */
public record OpcionOferta(Habitacion habitacion, RoomType tipo, long totalCents, String moneda, int noches) {}