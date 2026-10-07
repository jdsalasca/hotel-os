-- V4: precio acordado al reservar. Cuando se confirma una reserva con tarifa completa, se
-- congela el total, la moneda y el plan en la propia reserva: el comprobante muestra lo que se
-- acordó ese día, aunque el hotel cambie los precios después. Las tres columnas quedan NULL en
-- reservas anteriores y en reservas hechas sin tarifa configurada: sin precio no hay total.
ALTER TABLE reservations ADD COLUMN total_cents INTEGER;
ALTER TABLE reservations ADD COLUMN moneda TEXT;
ALTER TABLE reservations ADD COLUMN rate_plan_id INTEGER REFERENCES rate_plans(id);
