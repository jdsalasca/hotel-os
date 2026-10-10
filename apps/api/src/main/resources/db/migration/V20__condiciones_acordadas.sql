-- V20: condiciones acordadas al reservar. El precio ya se congela desde V4; aquí se congela
-- lo otro que el huésped acepta al confirmar: hora de entrada, hora de salida y política de
-- cancelación. Sin esto, si el hotel cambia su política después de vender, quien ya reservó
-- pierde el acuerdo que hizo (y el comprobante mostraría reglas que nunca aceptó).
--
-- NULL en reservas anteriores y en las creadas sin hotel-config: no había nada que congelar
-- y no se inventa. Un comprobante antiguo sin condiciones es honesto; uno con condiciones
-- inventadas, no.
ALTER TABLE reservations ADD COLUMN hora_entrada TEXT;
ALTER TABLE reservations ADD COLUMN hora_salida TEXT;
ALTER TABLE reservations ADD COLUMN politica_cancelacion TEXT;