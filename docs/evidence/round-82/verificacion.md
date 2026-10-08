# Ronda 82 - Bienvenido con nombre + panel que orienta

Fecha: 2026-10-08.

## Dolor
Nadie decía tu nombre en ningún lado; entrar (contraseña o Google) te dejaba en una lista
sin contexto; el login con Google caía en la home en vez del panel.

## Cambio
- Migración V16: `users.nombre`. Al entrar con Google se guarda su `given_name` (sin
  pisar un nombre puesto a mano con un login sin atributos).
- `GET /api/admin/sesion` devuelve `nombre`; la cabecera dice **"Bienvenido de vuelta,
  {nombre}"** (cae al correo, y el huésped a su nombre de Google).
- Nueva puerta `/admin`: tarjetas a Reservas (con mensajes sin leer), Hoy, Habitaciones,
  Hotel/Mapa y Actividad. La nav "Panel" y ambos logins (contraseña navega, Google con
  default a `/admin`) llevan ahí.
- `useSesion` expone `nombre` (vía el `sesion.ts` compartido del colega, sin duplicar).

## Verificación real
- Backend: Oauth2AdminTest (+nombre guardado), CambioClave, SecurityIntegration: 18/18.
- Frontend: tsc con tests limpio, vitest **21/21**.
- Despliegue: `git reset --hard origin/develop` + `up -d --build`; V16 aplicada; bundle con
  el panel; health `ok`. La bienvenida con nombre real se ve con sesión (cuenta del hotel).
- Archivos del otro agente intactos: paquete `seguridad` solo suma en el manejador que ya
  tocaba la R22; `Oauth2AdminTest` solo adapta constructor + 1 test nuevo.

## Archivos
- Nuevos: `V16__nombre_visible_admin.sql`, `PaginaAdminPanel.tsx`, este archivo.
- Tocados: `ManejadorAdminOauth2.java`, `AdminSesionController.java`, `useSesion.ts`,
  `SaludoSesion.test.tsx`, `App.tsx`, `PaginaLoginAdmin.tsx`, `Oauth2AdminTest.java`.
