# Ronda 140 - Hola corto con rol, nombre ajustable y salir visible

## Reporte
El dueño entra y ve "Bienvenido de vuelta, savatar62@gma…" cortado, sin rol y
sin botón de salir (captura del reporte). Pide: nombre de Google, editable,
corto (pre-@ si no hay), y salir visible que cierre de verdad.

## Causa raíz
- El saludo montado sin sesión nunca repreguntaba; la recarga tras entrar lo
  resuelve para todo lo montado, no solo el saludo.
- El salir vivía solo en el pie, bajo toda la página.
- Con clave no hay `given_name` que ponga el nombre: `users.nombre` queda `''`.

## Cambio
- `nombreCorto(nombre, email)` en `formato.ts`: nombre, si no pre-@, si no `''`
  (el correo completo queda en el `title`).
- Saludo con `nombreCorto` + etiqueta de rol (admin y huésped).
- `POST /api/admin/perfil` (sesión, 2-80 caracteres): el dueño ajusta su nombre;
  la tarjeta "Mi cuenta" en el panel lo guarda y refresca la sesión.
- Tras entrar con clave: recarga completa a `/admin` (igual que el salir ya
  hacía hacia `/admin/entrar`).
- `CierreSesionPanel` junto al saludo en la cabecera (`cabecera__sesion` +
  `cabecera__salir`, tokens existentes); muerto `.nav__salir`/`.nav__error` fuera.

## TDD rojo-verde
- `formato.test`: 2 fallos sin `nombreCorto`; con él, 7/7.
- `AdminPerfilTest` nuevo: 404 sin ruta; con ella, 1/1 (guarda, recorta, se ve
  en `/api/admin/sesion`, 400 en corto, 401 anónimo).
- `PaginaLoginAdmin.test`: "recarga al panel" falla sin el cambio; con él, 3/3.
- `PaginaAdminPanel.test` nuevo: 1 fallo sin el formulario; con él, 1/1.
- `CierreSesionPanel`: presencia con sesión, ausencia sin ella, POST + redirect.

## Verificación (salida real, 2026-10-08)
- Frontend: 21 archivos / 74 tests en verde (pool limitado por memoria).
- `tsc` ×2: exit 0.
- Backend: 429/434 en verde; 5 fallos SOLO en TDD en rojo del colega
  (`HuespedFechasTest` 1, `AdminReservasControllerTest` 4, endpoints sin
  commitear). Cero fallos en archivos míos; 18 errores de una corrida anterior
  fueron `.class` borrados a mitad de compilación paralela, no código.
- Visual: saludo+salir exigen sesión; jsdom afirma presencia y redirecciones.
  Captura post-deploy del panel (cabecera sin regresión) — ver `panel.png`.

## Despliegue
- Merge a `develop`, push y `compose up -d --build` en TopNUC con health OK.
- PENDIENTE DEL DUEÑO: reintentar Google (el log dirá el motivo si falla) y
  verificar `GOOGLE_CLIENT_SECRET`.
