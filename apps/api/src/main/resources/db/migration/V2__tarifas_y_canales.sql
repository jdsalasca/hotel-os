-- V2: tarifas configurables, planes tarifarios y canales con sus mapeos.
-- Sin precios ni nombres inventados: el hotel los registra antes de recibir reservas.

CREATE TABLE rate_plans (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  codigo TEXT UNIQUE NOT NULL,
  nombre TEXT NOT NULL,
  moneda TEXT NOT NULL,          -- ISO 4217 decided por el hotel, p.ej. COP
  activo INTEGER NOT NULL DEFAULT 1
);

-- Precio por noche en la moneda del plan. Sin fila para una fecha = sin tarifa configurada:
-- el flujo público no puede inventar un precio.
CREATE TABLE rates (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  rate_plan_id INTEGER NOT NULL REFERENCES rate_plans(id),
  room_type_id INTEGER NOT NULL REFERENCES room_types(id),
  fecha TEXT NOT NULL,
  precio_cents INTEGER NOT NULL CHECK (precio_cents >= 0),
  min_estancia INTEGER CHECK (min_estancia IS NULL OR min_estancia >= 1),
  max_estancia INTEGER CHECK (max_estancia IS NULL OR max_estancia >= 1),
  cerrado INTEGER NOT NULL DEFAULT 0,   -- 1 = no se puede reservar esa noche
  UNIQUE (rate_plan_id, room_type_id, fecha),
  CHECK (max_estancia IS NULL OR min_estancia IS NULL OR min_estancia <= max_estancia)
);

CREATE TABLE channels (
  codigo TEXT PRIMARY KEY,       -- WEB | BOOKING | DESPEGAR | AIRBNB | OTRO
  nombre TEXT NOT NULL,
  activo INTEGER NOT NULL DEFAULT 0
);

-- Mapeo entre el inventario local y la unidad que usa cada canal. Una habitación o un plan
-- sin mapeo no se publica en ese canal: evita ofrecer en venta lo que el canal no reconoce.
CREATE TABLE channel_mappings (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  channel_codigo TEXT NOT NULL REFERENCES channels(codigo),
  room_id INTEGER REFERENCES rooms(id),
  room_type_id INTEGER REFERENCES room_types(id),
  rate_plan_id INTEGER REFERENCES rate_plans(id),
  external_id TEXT NOT NULL,
  CHECK (room_id IS NOT NULL OR room_type_id IS NOT NULL),
  UNIQUE (channel_codigo, external_id)
);

CREATE TABLE ota_syncs (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  channel_codigo TEXT NOT NULL,
  operacion TEXT NOT NULL,
  exitosa INTEGER NOT NULL,
  detalle TEXT,                 -- ya saneado: sin secretos ni datos personales
  en TEXT NOT NULL
);

CREATE INDEX idx_rates_busqueda ON rates(room_type_id, fecha);
CREATE INDEX idx_channel_mappings_room ON channel_mappings(channel_codigo, room_id);
CREATE INDEX idx_ota_syncs_canal ON ota_syncs(channel_codigo, en);

INSERT INTO channels(codigo, nombre, activo) VALUES
  ('WEB', 'Web propia', 1),
  ('BOOKING', 'Booking.com', 0),
  ('DESPEGAR', 'Despegar', 0),
  ('AIRBNB', 'Airbnb', 0),
  ('OTRO', 'Otro canal', 0);