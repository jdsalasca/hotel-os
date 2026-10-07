# Ronda 22 — El panel acepta entrar con Google (con allowlist)

## Lo entregado

El panel tenía un solo método de entrada: contraseña. Ahora acepta Google, con una condición que
no es negociable: **estar autenticado en Google no basta, el correo tiene que estar en
`GOOGLE_ADMIN_EMAILS`**. Sin allowlist, cualquier cuenta de Google administraría el hotel.

- Un solo Client ID (el mismo del correo): dos registros, `google-admin` y `google-huesped`
  (el segundo se usa en la ronda 22), cada uno con su URL de retorno y su manejador.
- Quien entra con Google sale con `ROLE_ADMIN` y con el correo como nombre de sesión: el resto de
  rutas y la auditoría no distinguen cómo se autenticó cada sesión.
- La contraseña no se toca: sin Client ID configurado, el login con Google no existe y todo sigue
  como antes. Es el respaldo, no lo viejo.
- Si el correo no está en la allowlist: sin sesión y vuelta a `/admin/entrar?error=denegado`, que
  la página muestra en lenguaje llano.

## Tres fallos que salieron al verificar, no al diseñar

**1. El repositorio vacío tumba el arranque.** `InMemoryClientRegistrationRepository` rechaza la
lista vacía (`registrations cannot be empty`), y mi idea de "repositorio siempre presente pero
vacío sin credenciales" rompía las 82 pruebas que arrancan el contexto. Ahora el bean solo existe
con Client ID no vacío (`@ConditionalOnExpression`) y la cadena lo pide por `ObjectProvider`:
sin Google no hay login con Google, y nada más cambia.

**2. El botón llevaba a un 200 vacío.** nginx solo proxeaba `/api/*` al backend, así que
`/oauth2/authorization/google-admin` caía en el `index.html` del SPA. El baile OAuth2 vive en el
backend: se añadieron las rutas `/oauth2/` y `/login/oauth2/` al nginx de la imagen y los
rewrites equivalentes a `vercel.json`. Sin esto, el botón era decoración.

**3. El matcher de redirect no casa queries.** `redirectedUrlPattern` con `?*` y con `*` no casó
la URL de Google; aserción directa con `startsWith` y `contains`, que además fija que el
`redirect_uri` sea el del registro `google-admin`.

## Verificación

```text
.\mvnw.cmd test
Tests run: 188, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS
```

Los 3 tests de `Oauth2AdminTest` se escribieron antes que la implementación:

| Test | Qué fija |
|---|---|
| `laRutaDeAutorizacionLlevaAGoogle` | el registro existe y la vuelta es la de `google-admin` |
| `correoEnAllowlistEntra` | sesión con `ROLE_ADMIN` y nombre = correo, no sub |
| `correoFueraDeAllowlistNoEntra` | 302 a `/admin/entrar?error=denegado` y contexto limpio |

Por HTTP contra el stack real, con credenciales de mentira (solo se verifica la ida, Google no
está aquí):

```text
GET /oauth2/authorization/google-admin
-> 302 Location: https://accounts.google.com/o/oauth2/v2/auth?response_type=code&client_id=x&...
```

Evidencia visual: `login-google.png` (botón) y `login-denegado.png` (aviso), más las 34 capturas
del guion sin errores de consola ni de API.

La contraseña sigue intacta: `SecurityIntegrationTest` (login, throttle, CSRF, reserva pública)
pasa sin tocarlo.

## Riesgo del usuario, verificado

> tu /api/health hoy exige 4 tablas — confirmo que con 6 siga en ok

El health cuenta `name IN ('reservations','rooms','users','hotel_config')` y exige 4. Añadir tablas
no lo rompe: sigue contando las mismas 4 y responde `{"estado":"ok"}`. Esta ronda no añade tablas,
así que no hubo nada que tocar; la migración de huéspedes (V7, porque V6 ya la usó la ronda 20)
llega en la ronda 22 con su propia verificación del healthcheck.

## Lo que falta (ronda 22 y 23, ya planificado)

- R22: tabla `usuarios` + `reservas.usuario_id` (V7), manejador `google-huesped`, `/api/yo`,
  `/api/mis-reservas`, enlace al reservar. Tests: creación al primer login, anónima intacta, V6+V7.
- R23: botón Google para huéspedes + página Mis reservas + capturas.

## Configuración para el hotel

```bash
# .env (los tres compose ya pasan la variable)
GOOGLE_CLIENT_ID=            # el mismo del correo
GOOGLE_CLIENT_SECRET=        # el mismo del correo
GOOGLE_ADMIN_EMAILS=jefa@hotel.com,encargado@hotel.com
```

Y en Google Cloud, las dos URI de redireccionamiento autorizado (ver `.env.example`):

```text
http(s)://TU-DOMINIO/login/oauth2/code/google-admin
http(s)://TU-DOMINIO/login/oauth2/code/google-huesped
```