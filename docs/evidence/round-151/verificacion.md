# Ronda 151 - OAuth: causa raíz SameSite=Strict + fin del silencio

## Reporte
Entrar con Google rebotaba a `/admin/entrar?error=oauth2` sin explicación.

## Causa raíz (confirmada con el log en vivo del intento real)
`vuelta de Google fallida … [authorization_request_not_found]`: el callback
llegaba SIN la sesión que inició el flujo. Los composes de prod fijaban
`SameSite=Strict` en la JSESSIONID y con Strict el navegador retiene la cookie
en navegaciones iniciadas en otro sitio — justo la vuelta desde Google. En dev
nunca se vio porque ahí rige el Lax del navegador. Descartado antes con
evidencia: registro/redirect (Google acepta), secreto (un sondeo falso devuelve
`invalid_grant`, no `invalid_client`), reloj, esquema y allowlist (daría
`denegado`).

## Cambio
- `compose.production.yaml` + `compose.tunnel.yaml`: `SameSite=Lax` (el GET de
  vuelta lo lleva; el CSRF lo sigue cubriendo el token XSRF). Comentado para
  que nadie lo "re-endurezca" sin leer.
- `CookieSesionTest`: exige Lax (era el test el que blindaba el bug).
- `FalloOauth2` (bean): la vuelta fallida queda en el log con causa, por registro.
- `ManejadorAdminOauth2` + `ManejadorHuespedOauth2`: lo que reviente a mitad se
  registra y cae a la puerta con motivo, jamás 500 mudo.
- `PaginaLoginAdmin`: aviso visible con `?error=oauth2`.
- `SaludoSesion`: etiqueta de rol junto al nombre.
- `verificar-despliegue.mjs`: rechaza Strict en composes (para que no vuelva).

## TDD rojo-verde
- `ManejadorOAuthRobustoTest` nuevo (mocks que revientan): sin el cambio ambas
  lanzan `RuntimeException: base caída`; con él, 2/2 con el redirect correcto.
- `CookieSesionTest` exige Lax real por HTTP; verificador: 2 FALLA sin el cambio,
  exit 0 con él.

## Verificación (salida real, 2026-10-09)
- `CookieSesionTest` 3/3, `ManejadorOAuthRobustoTest` 2/2, `HotelVentaTest` 3/3,
  `PreparacionTest` 2/2; BUILD SUCCESS.
- Login con clave falsa en prod sigue dando "credenciales inválidas" limpio.
- PENDIENTE DEL DUEÑO: reintentar Google (debe entrar directo al panel).

## Despliegue
- Merge a `develop`, push y `compose up -d --build` en TopNUC con health OK
  (el cambio de env recrea el api; la base no se toca).
