# Ronda 128 - La vuelta fallida de Google se ve y el saludo dice el rol

(Nota de numeración: se trabajó como 125 sin saber que el colega ya había tomado
ese número. Siguiente número libre: 128.)

## Reporte
El dueño entra con Google y rebota a `/admin/entrar?error=oauth2` sin ninguna
explicación (captura del reporte). Además pide que tras entrar se vea
"hola [nombre], estás con rol [...]".

## Causa raíz (investigado antes de tocar)
- La ida a Google está BIEN: la URL lleva `client_id` válido y
  `redirect_uri=https://hotel.eridu.top/login/oauth2/code/google-admin` (https y
  host correctos); Google muestra el selector de cuentas sin error de registro.
- El fallo es en el canje o después, y era INVISIBLE: el `failureHandler` era un
  lambda mudo (sin log) y la página ignoraba `?error=oauth2` (solo mostraba
  `?error=denegado`). En 24h de logs de prod: cero líneas OAuth.
- Descartado: reloj del servidor (UTC correcto), esquema (`users.nombre` existe,
  V16 aplicada, el correo existe), allowlist (eso daría `?error=denegado`).
- Sospechoso principal para el rebote: `GOOGLE_CLIENT_SECRET` de prod
  (el dueño debe verificarlo en Google Cloud; con el log nuevo el próximo
  intento dirá el motivo exacto en el log).
- El saludo existía ("Bienvenido de vuelta, X") pero sin rol.

## Cambio
- `FalloOauth2` (bean): registra `vuelta de Google fallida en {uri}: {clase}:
  {mensaje}` (nunca el secreto) y conserva los destinos por registro.
- `PaginaLoginAdmin`: aviso visible con `?error=oauth2` ("Google no completó la
  entrada… prueba de nuevo o entra con contraseña").
- `SaludoSesion`: etiqueta de rol junto al nombre (`Administrador` / `Huésped`,
  componente `Etiqueta` existente, cero estilos nuevos).

## TDD rojo-verde
- `FalloOauth2Test`: compilación rota sin la clase; con ella, 2/2 (destino admin
  y huésped).
- `PaginaLoginAdmin.test`: 1 fallo por contenido sin el aviso; con él, 2/2.
- `SaludoSesion.test`: +2 aserciones de rol en verde (el texto base ya existía).

## Verificación (salida real, 2026-10-08)
- Backend: `Tests run: 409, Failures: 0, Errors: 0, Skipped: 1` + `BUILD SUCCESS`.
- Frontend: `vitest` 18 archivos / 58 tests en verde (con `--maxWorkers=2`: sin
  límite, los workers mueren por memoria y la corrida miente con menos archivos).
- `tsc --noEmit` y `tsc -p tsconfig.tests.json`: exit 0.
- En prod con navegador: login con clave falsa sigue dando "credenciales
  inválidas" limpio; captura de `/admin/entrar?error=oauth2` con el aviso tras
  el deploy — ver `error-oauth2-desktop.png`.

## Despliegue
- Merge a `develop`, push y `compose up -d --build` en TopNUC con health OK.
- PENDIENTE DEL DUEÑO: verificar `GOOGLE_CLIENT_SECRET` (y que
  `GOOGLE_ADMIN_EMAILS` incluya su correo) — el log nuevo dirá el motivo exacto.
