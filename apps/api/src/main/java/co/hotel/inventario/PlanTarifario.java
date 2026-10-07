package co.hotel.inventario;

/** Plan tarifario. La moneda la decide el hotel (ISO 4217); aquí no hay ninguna por defecto. */
public record PlanTarifario(long id, String codigo, String nombre, String moneda, boolean activo,
                             int descuentoPct) {}