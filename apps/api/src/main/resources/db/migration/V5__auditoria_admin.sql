-- V5: Rastro de acciones administrativas: quién tocó qué y con qué resultado.
-- El actor es el correo de la sesión, nunca el que afirme el cliente. No se guarda el cuerpo de la
-- petición porque ahí viajan contraseñas y el token de arranque.
CREATE TABLE admin_actions (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  actor TEXT NOT NULL,
  metodo TEXT NOT NULL,
  ruta TEXT NOT NULL,
  estado INTEGER NOT NULL,
  en TEXT NOT NULL
);
CREATE INDEX idx_admin_actions_en ON admin_actions(en);