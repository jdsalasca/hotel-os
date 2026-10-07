-- V8: RECHAZADA vuelve a ser un estado que la base puede guardar.
--
-- El enum Java lleva RECHAZADA desde V1, pero V6 reconstruyó la tabla para cambiar la clave de
-- idempotencia y en la copia se perdió del CHECK. Desde entonces rechazar una reserva era un 500
-- con un CHECK constraint failed, no un estado: nadie lo probó porque el camino feliz no lo tocaba.
--
-- Se reconstruye la tabla porque SQLite no permite alterar un CHECK. Mismo criterio que V6: sin
-- PRAGMA foreign_keys=OFF, porque Flyway rechazaría mezclar DDL transaccional y no transaccional.
-- Las filas existentes solo pueden estar en los tres estados que el CHECK de V6 permitía, así que
-- la copia no puede fallar.

CREATE TABLE reservations_v8 (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  codigo TEXT UNIQUE NOT NULL,
  email TEXT NOT NULL,
  nombre TEXT,
  llegada TEXT NOT NULL,
  salida TEXT NOT NULL,
  huespedes INTEGER NOT NULL,
  estado TEXT NOT NULL CHECK (estado IN ('PENDIENTE','CONFIRMADA','CANCELADA','RECHAZADA')),
  origen TEXT NOT NULL CHECK (origen IN ('WEB','BOOKING','DESPEGAR','AIRBNB')),
  idempotencia TEXT NOT NULL,
  creado_en TEXT NOT NULL,
  total_cents INTEGER,
  moneda TEXT,
  rate_plan_id INTEGER REFERENCES rate_plans(id),
  usuario_id INTEGER REFERENCES usuarios(id),
  UNIQUE (idempotencia, email)
);

INSERT INTO reservations_v8(id,codigo,email,nombre,llegada,salida,huespedes,estado,origen,
  idempotencia,creado_en,total_cents,moneda,rate_plan_id,usuario_id)
  SELECT id,codigo,email,nombre,llegada,salida,huespedes,estado,origen,
    idempotencia,creado_en,total_cents,moneda,rate_plan_id,usuario_id FROM reservations;

DROP TABLE reservations;
ALTER TABLE reservations_v8 RENAME TO reservations;

-- DROP TABLE se lleva los índices consigo: hay que recrear los que V6 y V7 pusieron.
CREATE INDEX IF NOT EXISTS idx_reservations_llegada ON reservations(llegada, estado);
CREATE INDEX IF NOT EXISTS idx_reservations_usuario ON reservations(usuario_id);