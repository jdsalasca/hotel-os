-- V6: La clave de idempotencia identifica un intento de reserva, no a una persona.
--
-- Antes era UNIQUE global. Buscar solo por la clave devolvía la reserva de quien la hubiera usado
-- antes, con nombre, correo y total, a cualquiera que compartiera la clave. Ahora la restricción
-- es por (idempotencia, correo): dos huéspedes distintos pueden usar la misma clave sin que uno
-- reciba los datos del otro, y un reintento del mismo huésped sigue sin duplicarse.
--
-- Se reconstruye la tabla porque SQLite no permite alterar una restricción UNIQUE: se copia todo,
-- se cambia el esquema y se renombra.
--
-- SQLite no tiene DDL transaccional, así que esta migración NO lleva PRAGMA foreign_keys=OFF.
-- Flyway rechaza mezclarla porque detectaría sentencias transaccionales y no transaccionales en el
-- mismo archivo. Sin foreign keys el borrado no dispara los REFERENCES de reservation_items y
-- reservation_history, que se resuelven igual al final.

CREATE TABLE reservations_v6 (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  codigo TEXT UNIQUE NOT NULL,
  email TEXT NOT NULL,
  nombre TEXT,
  llegada TEXT NOT NULL,
  salida TEXT NOT NULL,
  huespedes INTEGER NOT NULL,
  estado TEXT NOT NULL CHECK (estado IN ('PENDIENTE','CONFIRMADA','CANCELADA')),
  origen TEXT NOT NULL CHECK (origen IN ('WEB','BOOKING','DESPEGAR','AIRBNB')),
  idempotencia TEXT NOT NULL,
  creado_en TEXT NOT NULL,
  total_cents INTEGER,
  moneda TEXT,
  rate_plan_id INTEGER REFERENCES rate_plans(id),
  UNIQUE (idempotencia, email)
);

INSERT INTO reservations_v6(id,codigo,email,nombre,llegada,salida,huespedes,estado,origen,
  idempotencia,creado_en,total_cents,moneda,rate_plan_id)
  SELECT id,codigo,email,nombre,llegada,salida,huespedes,estado,origen,
    idempotencia,creado_en,total_cents,moneda,rate_plan_id FROM reservations;

DROP TABLE reservations;
ALTER TABLE reservations_v6 RENAME TO reservations;

-- El índice que V1 declaraba sobre esta tabla se reconstruye aquí: DROP TABLE se lleva sus índices
-- consigo, y V1 no puede volver a ejecutarse porque ya está aplicada. El nombre se conserva para no
-- tener dos índices distintos sobre (llegada, estado).
CREATE INDEX IF NOT EXISTS idx_reservations_llegada ON reservations(llegada, estado);