-- V1: esquema base. Fechas TEXT ISO-8601 (YYYY-MM-DD). Intervalos semiabiertos [desde, hasta).
-- Migraciones versionadas de solo lectura en production: nunca se edita una ya aplicada.

CREATE TABLE hotel_config (
  clave TEXT PRIMARY KEY,
  valor TEXT NOT NULL,
  actualizado_en TEXT NOT NULL DEFAULT ''
);

CREATE TABLE room_types (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  codigo TEXT UNIQUE NOT NULL,
  nombre TEXT NOT NULL,
  capacidad_max INTEGER NOT NULL CHECK (capacidad_max > 0)
);

CREATE TABLE rooms (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  codigo TEXT UNIQUE NOT NULL,
  room_type_id INTEGER REFERENCES room_types(id),
  estado TEXT NOT NULL DEFAULT 'ACTIVA' CHECK (estado IN ('ACTIVA','MANTENIMIENTO','FUERA_DE_SERVICIO')),
  nombre TEXT NOT NULL DEFAULT ''
);

CREATE TABLE users (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  email TEXT UNIQUE NOT NULL,
  hash TEXT NOT NULL,
  rol TEXT NOT NULL CHECK (rol IN ('ADMIN','STAFF')),
  activo INTEGER NOT NULL DEFAULT 1,
  creado_en TEXT NOT NULL
);

CREATE TABLE reservations (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  codigo TEXT UNIQUE NOT NULL,
  email TEXT NOT NULL,
  nombre TEXT NOT NULL DEFAULT '',
  llegada TEXT NOT NULL,
  salida TEXT NOT NULL,
  huespedes INTEGER NOT NULL CHECK (huespedes > 0),
  estado TEXT NOT NULL CHECK (estado IN ('PENDIENTE','CONFIRMADA','CANCELADA','RECHAZADA')),
  origen TEXT NOT NULL CHECK (origen IN ('WEB','BOOKING','DESPEGAR','AIRBNB','OTRO')),
  idempotencia TEXT UNIQUE NOT NULL,
  creado_en TEXT NOT NULL,
  CHECK (llegada < salida)
);

CREATE TABLE reservation_items (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  reservation_id INTEGER NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
  room_id INTEGER NOT NULL REFERENCES rooms(id),
  desde TEXT NOT NULL,
  hasta TEXT NOT NULL,
  CHECK (desde < hasta)
);

CREATE TABLE reservation_history (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  reservation_id INTEGER NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
  estado_ant TEXT,
  estado_nuevo TEXT NOT NULL,
  actor TEXT NOT NULL,
  en TEXT NOT NULL
);

CREATE TABLE blocks (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  room_id INTEGER REFERENCES rooms(id),
  desde TEXT NOT NULL,
  hasta TEXT NOT NULL,
  motivo TEXT NOT NULL DEFAULT '',
  CHECK (desde < hasta)
);

CREATE INDEX idx_reservation_items_solape ON reservation_items(room_id, desde, hasta);
CREATE INDEX idx_reservations_llegada ON reservations(llegada, estado);
CREATE INDEX idx_blocks_solape ON blocks(room_id, desde, hasta);

-- Sin datos de demostración: el hotel registra su inventario y sus tarifas antes de recibir reservas.