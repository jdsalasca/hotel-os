# Ronda 16 — OAuth2 y autenticación listos para producción

Fecha: 2026-10-06. Rama `develop`.

## Entregable

La autenticación y el OAuth2 de correo quedan verificados contra el comportamiento real de
producción, no contra supuestos.

## Defecto 1: el canje OAuth2 nunca habría funcionado en producción

`CorreoService.accessToken()` armaba un cuerpo **JSON** y lo etiquetaba como
`x-www-form-urlencoded`. Peor: `HttpClienteOta` forzaba `Content-Type: application/json` en toda
petición con cuerpo, así que la petición salía con **dos** Content-Type. El endpoint `/token` de
Google solo acepta formulario codificado: el primer envío real habría fallado.

```text
# Antes (capturado por el test contra servidor local):
cuerpo={"client_id":"client-id",...,"grant_type":"refresh_token"}  -> rechazado por Google
# Después:
grant_type=refresh_token&client_id=...&client_secret=...&refresh_token=...  -> aceptado
```

- `HttpClienteOta` ya no pisa el Content-Type: solo pone `application/json` por defecto cuando
  nadie lo fijó. Los conectores OTA pasan `Map.of()` y siguen igual.
- El test `canjeOAuth2ComoFormulario` fija el contrato: un solo Content-Type, cuerpo codificado,
  nada de JSON.

## Defecto 2: el proxy de producción no tenía a dónde mandar nada

`compose.production.yaml` montaba volúmenes de Caddy pero **no existía ningún `Caddyfile`** en el
repositorio. El proxy arrancaba sin rutas: ni `/api/*` al backend ni el resto al frontend.

- `Caddyfile` creado con `{$DOMAIN}`, `reverse_proxy /api/* api:8080`, resto a `web:80` y
  cabeceras de seguridad. Validado con `caddy validate` (configuración válida) y con
  `docker compose config` (exit 0). Montado solo-lectura.

## Endurecimiento de la sesión

- `SERVER_SERVLET_SESSION_COOKIE_SECURE=true` y `SAME_SITE=strict` en el compose de producción.
- `CookieSesionTest` lo prueba con HTTP real contra el contenedor embebido, porque MockMvc no
  aplica las banderas de la cookie del contenedor. Verificado también a mano:
  `JSESSIONID=...; Path=/; Secure; HttpOnly; SameSite=Strict`.
- Nota de método: la primera comprobación manual dio cookie sin banderas porque el `docker run`
  iba sin `-p` y el curl pegaba contra el contenedor de desarrollo. Por eso existe el test
  automatizado: el ojo se equivoca de contenedor, el test no.

## Verificación real

```text
.\mvnw.cmd test
Tests run: 172, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

```text
docker compose ... capturas
capturas completas sin errores de consola ni de API (30 capturas)
```

Lo que ya estaba y se revalidó: throttle de login, contraseña mínima de 12 en el arranque,
token de un solo uso, CSRF activo, sesión con `changeSessionId`, demo imposible en producción.