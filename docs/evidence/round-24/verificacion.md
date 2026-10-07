# Ronda 24 — El login con Google estaba roto: la sesión no sobrevivía

Ronda corta y de una sola cosa: **arreglar dos fallos que introduje en las rondas 22 y 23**. Los dos
son de autenticación, así que sólo se descubren probando el camino entero en vez de mirando
variables sueltas.

## Fallo 1: la sesión no persistía, el login con Google no funcionaba

`ManejadorHuespedOauth2` terminaba con `res.sendRedirect(...)` esperando que la cadena de seguridad
guardara el contexto. **No lo hace.** Desde Spring Security 6 el contexto solo se persiste si
alguien lo guarda, y el destino por defecto ni siquiera es la sesión: es un atributo de la petición.

El efecto: el huésped entraba con Google, volvía al sitio, y `/api/yo` respondía **401**. Todos los
tests anteriores pasaban porque comprobaban el `SecurityContextHolder` en memoria, que sí quedaba
puesto: la prueba miraba el sitio equivocado.

Reproducido antes del arreglo:

```text
el manejador tiene que guardar el contexto en la sesión a mano: el redirect por sí solo
no lo hace desde Spring Security 6, y /api/yo devolvería 401 al volver de Google
==> expected: <true> but was: <false>
```

El arreglo va en las dos capas, porque las dos estaban mal:

- `SecurityConfig` fija `.securityContext(s -> s.securityContextRepository(repoSesion))` con
  `HttpSessionSecurityContextRepository`. Esta es la causa raíz: `oauth2Login()` no persiste a
  sesión por defecto.
- Los dos manejadores guardan el contexto a mano antes de redirigir. El panel **también estaba
  roto** y sus tests tampoco lo detectaban, porque delegaba en
  `SavedRequestAwareAuthenticationSuccessHandler`, que por su cuenta usa el repositorio de
  atributos de petición.

## Fallo 2: redirect abierto en la vuelta del huésped

El manejador hacía esto:

```java
res.sendRedirect(req.getParameter("vuelve") != null ? req.getParameter("vuelve") : "/mis-reservas");
```

Un `vuelve` con valor `https://sitio-falso.example` mandaba al usuario al sitio del atacante
**justo después de autenticarse de verdad** en el hotel. Google conserva los parámetros de la
`redirect_uri`, así que el atacante elige ese valor y solo tiene que conseguir que la víctima autorice
la aplicación.

Ahora `destinoSeguro` acepta solo rutas internas: sin valor, o si no empieza por `/`, o si es
`//algo` (que es un protocolo-relativo, o sea otro dominio).

## Verificación

```text
.\mvnw.cmd test
Tests run: 197, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS
```

Los 3 tests nuevos:

| Test | Qué fija |
|---|---|
| `laSesionSobreviveAlRedireccion` | la sesión guarda el contexto tras el login de huésped |
| `laSesionDelAdminSobrevive` | lo mismo en el panel |
| `elVuelveNoPuedeSalirDelSitio` | `https://`, `//`, `javascript:` y nulo caen en `/mis-reservas`; `/reserva` sí se respeta |

Contra los contenedores reales, el camino de contraseña intacto:

```text
GET  /api/health                  -> {"estado":"ok","base":"accesible","tablas":"4"}
POST /api/admin/login             -> 200
GET  /api/admin/reservas          -> 200   (sesión de contraseña)
GET  /api/yo                      -> {"email":"admin","nombre":"","tieneReservas":false}
```

## Lo que este arreglo dice del resto

Los handlers de OAuth2 no se pueden probar solo mirando el contexto en memoria. Lo que importa es
qué queda **después** del redirect, en la siguiente petición. Esa es la clase de test que faltaba y
que ahora está, para los dos caminos.

## Lo que sigue sin poder verificarse aquí

El intercambio real con Google (callback, `code_verifier`, scopes) necesita una cuenta y una
redirect URI registrada. Lo verificado es: la ida a Google responde 302 con la `redirect_uri` del
registro correcta, el contexto se guarda en sesión, la allowlist rechaza, y el redirect de vuelta
no puede salirse del sitio.