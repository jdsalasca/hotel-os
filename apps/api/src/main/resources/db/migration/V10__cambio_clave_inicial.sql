-- Cambio de clave inicial obligatorio para cuentas sembradas.
-- 0 = la cuenta usa su contraseña normal; 1 = debe cambiarla antes de entrar al panel.
ALTER TABLE users ADD COLUMN debe_cambiar_clave INTEGER NOT NULL DEFAULT 0;
