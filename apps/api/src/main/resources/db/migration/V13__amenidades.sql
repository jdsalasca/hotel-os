-- V13: Servicios (amenidades) por tipo de habitación.
--
-- El catálogo viene sembrado con los diez servicios habituales; el hotel marca con checkbox
-- los que tiene cada tipo. La carta es cerrada a propósito: texto libre aquí acaba en
-- "wifi", "WiFi" y "WIFI" como tres servicios distintos. Sin migración de datos: antes no
-- había servicios, así que todo tipo empieza vacío.
CREATE TABLE amenidades (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  codigo TEXT UNIQUE NOT NULL,
  nombre TEXT NOT NULL
);

CREATE TABLE room_type_amenidades (
  room_type_id INTEGER NOT NULL REFERENCES room_types(id) ON DELETE CASCADE,
  amenidad_id INTEGER NOT NULL REFERENCES amenidades(id) ON DELETE CASCADE,
  PRIMARY KEY (room_type_id, amenidad_id)
);

INSERT INTO amenidades(codigo,nombre) VALUES
  ('wifi','Wifi gratis'),
  ('tv','TV'),
  ('minibar','Minibar'),
  ('caja-fuerte','Caja fuerte'),
  ('jacuzzi','Jacuzzi'),
  ('vista','Vista panorámica'),
  ('balcon','Balcón'),
  ('desayuno','Desayuno incluido'),
  ('parqueadero','Parqueadero'),
  ('aire','Aire acondicionado');
