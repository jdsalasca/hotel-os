-- V7: Huéspedes con cuenta de Google.
--
-- `google_sub` es el identificador estable de la cuenta en Google. No se usa el correo como
-- clave porque un mismo correo puede cambiar de nombre o de alias; y dos personas distintas
-- pueden compartir un correo, así que las reservas cuelgan del id, no del texto.
--
-- `reservations.usuario_id` queda NULLABLE a propósito: quien reserva sin entrar sesión no tiene
-- cuenta, y esa reserva no puede quedarse huérfana ni romperse. Las anónimas siguen funcionando
-- exactamente igual que hasta ahora.

CREATE TABLE usuarios (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  google_sub TEXT UNIQUE NOT NULL,
  email TEXT NOT NULL,
  nombre TEXT NOT NULL DEFAULT '',
  creado_en TEXT NOT NULL
);
-- Para que /api/mis-reservas no necesite el id en la URL.
CREATE INDEX idx_usuarios_email ON usuarios(lower(email));

ALTER TABLE reservations ADD COLUMN usuario_id INTEGER REFERENCES usuarios(id);
CREATE INDEX idx_reservations_usuario ON reservations(usuario_id);