# Ronda 29 — La instrucción de despliegue de Vercel rompía el panel

## Lo que decía la guía

En `docs/operations/despliegue-respaldos.md`:

> En Vercel, configura la variable de entorno con la URL del backend: `VITE_API_BASE =
> https://api.tu-dominio.com`

Y tres párrafos antes, la misma página decía que `vercel.json` ya reescribe `/api/*` hacia el
backend. Las dos cosas no pueden ser verdad a la vez: si el navegador pide a `/api/...` en el
propio dominio, la reescritura resuelve la URL y la base no hace falta.

Con `VITE_API_BASE` cruzando orígenes, el panel deja de funcionar, y por cuatro razones a la vez:

| Qué pasa | Por qué |
|---|---|
| La sesión no viaja | `credentials: 'same-origin'` no manda cookies entre sitios distintos |
| La llamada se bloquea | la CSP de la ronda 25 es `connect-src 'self'` |
| La cookie no se guarda | es `SameSite=Strict`, que es lo correcto y no se arregla solo |
| El navegador la corta | la API no permite ese origen con CORS, y no debería |

Es un fallo silencioso: la portada funciona, se ve el botón de Google, pero el panel devuelve 401
en cuanto pide datos.

## Un tercer hallazgo al verificar

El `Dockerfile` del frontend **no declara `ARG VITE_API_BASE`**, así que en el camino de Docker la
variable no puede llegar al build: Vite la fija al compilar, y el compose no la pasa. Se comprobó
fijándola en el entorno y viendo que el build no la recogía.

Lo bueno es que en Docker el camino es siempre el correcto. Lo malo es que la guía prometía una
configuración que en Docker no existe y en Vercel rompe.

## Lo entregado

- **La guía dice la verdad**: no definas `VITE_API_BASE`, en ningún sitio, y por qué.
- **`cliente.ts` avisa en consola** si detecta la base cruzada, con el motivo. Un panel que entra y
  se cae es peor que uno que dice por qué.

```ts
if (BASE_API !== '' && !BASE_API.startsWith(window.location.origin) && !BASE_API.startsWith('/')) {
  console.error('[cliente] VITE_API_BASE=... apunta a otro origen y el panel no va a funcionar: ...');
}
```

## Verificación

Con la base cruzada inyectada en el navegador real:

```text
http://127.0.0.1:5174/admin/entrar
consola: error: Failed to load resource: the server responded with a status of 401
```

La petición sale hacia el otro origen y el backend responde 401: sin sesión. Con la base vacía, que
es lo correcto:

```text
POST /api/admin/login   -> 200
GET  /api/admin/reservas -> 200
```

Y el guion de capturas, con la configuración buena:

```text
capturas completas sin errores de consola ni de API   (34 capturas)
```

**El aviso no aparece**, que es lo que tiene que pasar: el guard calla cuando la configuración es
correcta.

```text
.\mvnw.cmd test
Tests run: 205, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS
```

## Por qué no se "arregló" soportando el origen cruzado

Porque se podría, y sería peor. Hacerlo pediría `SameSite=None` (que debilita la protección de
CSRF) más un `allow-origin` con credenciales en la API, todo para usar una ruta que la reescritura
de Vercel ya resuelve sin tocar nada. Un camino que funciona de menos es un camino que nadie va a
usar bien.

## Lo que queda sin verificar

La parte de Vercel: hace falta un despliegue real para ver el rewrite resolviendo y la cookie
atravesando el proxy. Lo verificado aquí es que el camino de Docker, que usa el mismo
`vercel.json`-style rewrite pero local, entra y pide datos sin 401.