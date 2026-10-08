-- Nombre visible del personal para el "bienvenido de vuelta". Se rellena solo con el
-- nombre de Google al entrar por OAuth; por contraseña queda vacío hasta que el admin entre
-- una vez con Google. Nada obligatorio: el saludo usa el correo si no hay nombre.
ALTER TABLE users ADD COLUMN nombre TEXT NOT NULL DEFAULT '';
