-- V11: Detalle libre en el historial de la reserva.
--
-- Hasta ahora cada fila decía solo "de estado A a estado B por alguien". Los movimientos que no
-- cambian el estado (reasignar habitación) no tenían dónde contarse: o se perdían, o se
-- inventaba una fila A→A que confunde. `detalle` guarda el qué ("habitación 101 → 102") y los
-- estados siguen diciendo lo suyo. NULL en todo lo anterior, que no necesita reescritura.
ALTER TABLE reservation_history ADD COLUMN detalle TEXT;
