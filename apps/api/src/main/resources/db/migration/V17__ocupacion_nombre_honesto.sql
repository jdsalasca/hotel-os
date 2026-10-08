-- f3_ocupacion contaba reservas vigentes (pendientes y confirmadas), no estancia
-- efectiva: sin recepción no hay ocupación que medir. Se renombra por lo que mide;
-- la clave no cambia para no romper historial ni clientes.
UPDATE indicator_definitions
SET nombre = 'Noches comprometidas',
  definicion = 'Noches con reserva vigente (pendiente o confirmada) sobre noches '
    || 'disponibles para la venta. No distingue estancia efectiva: eso llegará con '
    || 'la recepción.',
  formula = 'noches comprometidas / noches disponibles para venta × 100'
WHERE clave = 'f3_ocupacion';
