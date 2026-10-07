-- V12: Libro de abonos de una reserva.
--
-- Sin pasarela de pagos (el hotel no ha elegido proveedor), el registro manual con trazabilidad
-- es lo que permite cobrar en recepción sin papelitos. Cada abono dice cuánto, en qué moneda,
-- por qué concepto, quién lo registró y cuándo; anular no borra, marca quién y cuándo, para que
-- el dinero que entró y salió quede contado.
--
-- El saldo no se guarda: es total menos abonos vigentes, calculado al leer. Guardarlo sería
-- otra fuente de verdad que se desincroniza.
CREATE TABLE pagos (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  reservation_id INTEGER NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
  monto_cents INTEGER NOT NULL CHECK (monto_cents > 0),
  moneda TEXT NOT NULL,
  concepto TEXT NOT NULL DEFAULT '',
  actor TEXT NOT NULL,
  creado_en TEXT NOT NULL,
  anulado_en TEXT,
  anulado_por TEXT
);

CREATE INDEX idx_pagos_reserva ON pagos(reservation_id);
