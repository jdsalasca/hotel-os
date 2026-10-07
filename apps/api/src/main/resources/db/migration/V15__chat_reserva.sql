-- V15: Conversación huésped ↔ hotel por reserva.
--
-- Cada mensaje dice quién lo escribió y si ya lo vio el otro lado. `visto` lo marca quien LEE:
-- al abrir el hilo el hotel marca los del huésped y viceversa. Sin borrado: la conversación
-- es parte del historial de la reserva, igual que el historial de estados.
CREATE TABLE mensajes (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  reservation_id INTEGER NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
  autor TEXT NOT NULL CHECK (autor IN ('HUESPED','HOTEL')),
  texto TEXT NOT NULL CHECK (length(texto) >= 1 AND length(texto) <= 1000),
  creado_en TEXT NOT NULL,
  visto INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_mensajes_reserva ON mensajes(reservation_id);
