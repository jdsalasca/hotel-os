-- Las recomendaciones ya existentes se clasifican como sitios para visitar.
ALTER TABLE lugares_interes ADD COLUMN categoria TEXT NOT NULL DEFAULT 'VISITAR'
  CHECK (categoria IN ('COMER', 'VISITAR', 'ALOJARSE'));
