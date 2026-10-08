-- V18: OTRO vuelve a ser un origen que la base puede guardar.
--
-- El enum Java y V1 llevan OTRO desde el inicio, pero las reconstrucciones V6 y V8 lo
-- perdieron del CHECK (igual que le pasó a RECHAZADA). Desde entonces guardar una reserva
-- manual del panel era un 500 con CHECK constraint failed, no una reserva: nadie lo probó
-- porque ningún flujo persistía OTRO hasta el alta manual.
--
-- Se reconstruye la tabla porque SQLite no permite alterar un CHECK. Mismo criterio que V8: sin
-- PRAGMA foreign_keys=OFF, porque Flyway rechazaría mezclar DDL transaccional y no transaccional.
-- Las filas existentes solo pueden estar en los cuatro orígenes que el CHECK de V8 permitía,
-- así que la copia no puede fallar.

CREATE TABLE reservations_v18 (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  codigo TEXT UNIQUE NOT NULL,
  email TEXT NOT NULL,
  nombre TEXT,
  llegada TEXT NOT NULL,
  salida TEXT NOT NULL,
  huespedes INTEGER NOT NULL,
  estado TEXT NOT NULL CHECK (estado IN ('PENDIENTE','CONFIRMADA','CANCELADA','RECHAZADA')),
  origen TEXT NOT NULL CHECK (origen IN ('WEB','BOOKING','DESPEGAR','AIRBNB','OTRO')),
  idempotencia TEXT NOT NULL,
  creado_en TEXT NOT NULL,
  total_cents INTEGER,
  moneda TEXT,
  rate_plan_id INTEGER REFERENCES rate_plans(id),
  usuario_id INTEGER REFERENCES usuarios(id),
  UNIQUE (idempotencia, email)
);

INSERT INTO reservations_v18(id,codigo,email,nombre,llegada,salida,huespedes,estado,origen,
  idempotencia,creado_en,total_cents,moneda,rate_plan_id,usuario_id)
  SELECT id,codigo,email,nombre,llegada,salida,huespedes,estado,origen,
    idempotencia,creado_en,total_cents,moneda,rate_plan_id,usuario_id FROM reservations;

DROP TABLE reservations;
ALTER TABLE reservations_v18 RENAME TO reservations;

-- DROP TABLE se lleva los índices consigo: hay que recrear los que V6 y V7 pusieron.
CREATE INDEX IF NOT EXISTS idx_reservations_llegada ON reservations(llegada, estado);
CREATE INDEX IF NOT EXISTS idx_reservations_usuario ON reservations(usuario_id);
