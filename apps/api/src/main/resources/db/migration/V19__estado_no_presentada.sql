-- V19: no presentar una reserva confirmada cierra su vigencia y libera el inventario.
--
-- SQLite no permite alterar el CHECK. Primero se apartan las cuatro tablas hijas: al quitar la
-- tabla reservations, sus ON DELETE CASCADE borrarían pagos, mensajes, historial y habitaciones
-- asignadas. Los respaldos CTAS son temporales y sin FK para sobrevivir a esa reconstrucción.
-- También se guardan los máximos AUTOINCREMENT para no reutilizar identificadores ya emitidos.

CREATE TABLE reservation_items_v19_backup AS SELECT * FROM reservation_items;
CREATE TABLE reservation_history_v19_backup AS SELECT * FROM reservation_history;
CREATE TABLE pagos_v19_backup AS SELECT * FROM pagos;
CREATE TABLE mensajes_v19_backup AS SELECT * FROM mensajes;
CREATE TABLE reservations_v19_sequences_backup AS
  SELECT name, seq FROM sqlite_sequence
  WHERE name IN ('reservations','reservation_items','reservation_history','pagos','mensajes');

DROP TABLE reservation_items;
DROP TABLE reservation_history;
DROP TABLE pagos;
DROP TABLE mensajes;

CREATE TABLE reservations_v19 (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  codigo TEXT UNIQUE NOT NULL,
  email TEXT NOT NULL,
  nombre TEXT,
  llegada TEXT NOT NULL,
  salida TEXT NOT NULL,
  huespedes INTEGER NOT NULL,
  estado TEXT NOT NULL CHECK (estado IN ('PENDIENTE','CONFIRMADA','CANCELADA','RECHAZADA','NO_PRESENTADA')),
  origen TEXT NOT NULL CHECK (origen IN ('WEB','BOOKING','DESPEGAR','AIRBNB','OTRO')),
  idempotencia TEXT NOT NULL,
  creado_en TEXT NOT NULL,
  total_cents INTEGER,
  moneda TEXT,
  rate_plan_id INTEGER REFERENCES rate_plans(id),
  usuario_id INTEGER REFERENCES usuarios(id),
  UNIQUE (idempotencia, email)
);

INSERT INTO reservations_v19(id,codigo,email,nombre,llegada,salida,huespedes,estado,origen,
  idempotencia,creado_en,total_cents,moneda,rate_plan_id,usuario_id)
  SELECT id,codigo,email,nombre,llegada,salida,huespedes,estado,origen,
    idempotencia,creado_en,total_cents,moneda,rate_plan_id,usuario_id FROM reservations;

DROP TABLE reservations;
ALTER TABLE reservations_v19 RENAME TO reservations;

CREATE TABLE reservation_items (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  reservation_id INTEGER NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
  room_id INTEGER NOT NULL REFERENCES rooms(id),
  desde TEXT NOT NULL,
  hasta TEXT NOT NULL,
  CHECK (desde < hasta)
);
INSERT INTO reservation_items(id,reservation_id,room_id,desde,hasta)
  SELECT id,reservation_id,room_id,desde,hasta FROM reservation_items_v19_backup;
CREATE INDEX idx_reservation_items_solape ON reservation_items(room_id, desde, hasta);

CREATE TABLE reservation_history (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  reservation_id INTEGER NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
  estado_ant TEXT,
  estado_nuevo TEXT NOT NULL,
  detalle TEXT,
  actor TEXT NOT NULL,
  en TEXT NOT NULL
);
INSERT INTO reservation_history(id,reservation_id,estado_ant,estado_nuevo,detalle,actor,en)
  SELECT id,reservation_id,estado_ant,estado_nuevo,detalle,actor,en FROM reservation_history_v19_backup;

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
INSERT INTO pagos(id,reservation_id,monto_cents,moneda,concepto,actor,creado_en,anulado_en,anulado_por)
  SELECT id,reservation_id,monto_cents,moneda,concepto,actor,creado_en,anulado_en,anulado_por
  FROM pagos_v19_backup;
CREATE INDEX idx_pagos_reserva ON pagos(reservation_id);

CREATE TABLE mensajes (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  reservation_id INTEGER NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
  autor TEXT NOT NULL CHECK (autor IN ('HUESPED','HOTEL')),
  texto TEXT NOT NULL CHECK (length(texto) >= 1 AND length(texto) <= 1000),
  creado_en TEXT NOT NULL,
  visto INTEGER NOT NULL DEFAULT 0
);
INSERT INTO mensajes(id,reservation_id,autor,texto,creado_en,visto)
  SELECT id,reservation_id,autor,texto,creado_en,visto FROM mensajes_v19_backup;
CREATE INDEX idx_mensajes_reserva ON mensajes(reservation_id);

-- Restablece los máximos anteriores incluso si se habían borrado las últimas filas de una tabla.
UPDATE sqlite_sequence
SET seq = MAX(seq, (SELECT seq FROM reservations_v19_sequences_backup b WHERE b.name=sqlite_sequence.name))
WHERE name IN (SELECT name FROM reservations_v19_sequences_backup);
INSERT INTO sqlite_sequence(name,seq)
  SELECT b.name,b.seq FROM reservations_v19_sequences_backup b
  WHERE NOT EXISTS (SELECT 1 FROM sqlite_sequence s WHERE s.name=b.name);

DROP TABLE reservation_items_v19_backup;
DROP TABLE reservation_history_v19_backup;
DROP TABLE pagos_v19_backup;
DROP TABLE mensajes_v19_backup;
DROP TABLE reservations_v19_sequences_backup;

CREATE INDEX IF NOT EXISTS idx_reservations_llegada ON reservations(llegada, estado);
CREATE INDEX IF NOT EXISTS idx_reservations_usuario ON reservations(usuario_id);
