-- V14: Sitios y experiencias cerca del hotel para el mapa de la landing.
--
-- El hotelero los administra desde el panel; la web pública muestra los activos con su
-- distancia al hotel. Sin datos sembrados: cada hotel tiene sus propios sitios.
CREATE TABLE lugares_interes (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  nombre TEXT NOT NULL,
  descripcion TEXT NOT NULL DEFAULT '',
  latitud REAL NOT NULL CHECK (latitud >= -90 AND latitud <= 90),
  longitud REAL NOT NULL CHECK (longitud >= -180 AND longitud <= 180),
  activo INTEGER NOT NULL DEFAULT 1,
  creado_en TEXT NOT NULL
);
