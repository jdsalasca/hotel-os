# Ronda 25 — CSP estricta y cabeceras iguales en las tres rutas

## Lo entregado

El sitio tenía `nosniff`, `X-Frame-Options`, `Referrer-Policy` y `Permissions-Policy`, pero **sin
Content-Security-Policy**. Para una reserva pública es la que más importa: sin ella, una inyección de
HTML puede leer los datos de la reserva del huésped o robarle la sesión del panel.

La política es estricta, sin `unsafe-inline` ni `unsafe-eval`:

```
default-src 'self'; base-uri 'self'; object-src 'none'; frame-ancestors 'none';
img-src 'self' data:; font-src 'self'; style-src 'self'; script-src 'self';
connect-src 'self'; form-action 'self'
```

Se pudo cerrar de verdad porque el build no deja nada inline. Comprobado antes de escribir nada:

- El `index.html` del build solo referencia `/assets/index-*.js` y `/assets/index-*.css`.
- Cero `style={{...}}`, cero `dangerouslySetInnerHTML` y cero `eval(` en los componentes.
- El CSS compilado no pide ningún recurso externo (las fuentes son del sistema).

Si algún día se añade un script o un estilo en línea, la CSP lo rompe de forma visible en vez de
dejarlo pasar en silencio. Esa es la razón de no poner `unsafe-inline`.

## Verificación: que la CSP exista no prueba nada

Un encabezado presente puede estar mal formado y no hacer nada. Lo que se verifica es que **muerde**:

```js
window.__csp = 'original';
const s = document.createElement('script');
s.textContent = 'window.__csp = "ejecutado";';
document.head.appendChild(s);
return window.__csp;      // tiene que seguir siendo 'original'
```

Eso vive ahora en `tools/capturas/capturar.mjs`, que ya recorre todas las pantallas: si la CSP
desaparece, se relaja o aparece un `unsafe-inline`, el guion falla y no se generan capturas.

La comprobación va en una página aparte a propósito: el script inyectado genera a propósito un error
de consola, y el resto del guion trata cualquier error de consola como fallo. Al principio el
propio test se Marcaba como problema, que es la forma más tonta de romperse uno mismo.

Resultado del guion contra los contenedores reales, con la CSP servida:

```text
csp activa y efectiva: default-src 'self'; base-uri 'self'; object-src 'none'; fr...
capturas completas sin errores de consola ni de API   (34 capturas)
```

Que no haya ni un error de consola en las 34 pantallas es la prueba de que la CSP no rompió nada:
todas las hojas de estilo, scripts, fuentes y llamadas a la API siguen passando.

## Detalle que salió al verificar: `Permissions-Policy` faltaba en el nginx

Estaba en el `Caddyfile` y en `vercel.json`, pero no en el nginx de la imagen web, que es el que
sirve la web en el modo directo y el túnel. Tres rutas de despliegue con cabeceras distintas hacen
que "se endureció" signifique una cosa según por dónde se entre. Ahora las tres llevan lo mismo.

Verificado por HTTP en las dos rutas:

```text
GET /              -> Content-Security-Policy, Permissions-Policy
GET /api/health    -> Content-Security-Policy, Permissions-Policy
```

Las cabeceras del `server` de nginx se heredan en los `location`, incluidos los que proxean a la API,
así que las respuestas de JSON también las llevan.

```text
.\mvnw.cmd test
Tests run: 197, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS
```

## Vercel

`vercel.json` lleva la misma política. **No verificada**: hace falta desplegar en Vercel, que está
fuera del entorno de esta ronda. Lo verificado es que el JSON es válido y que la política es idéntica
a la del nginx, de modo que si Vercel la sirve tal cual, el resultado es el mismo que aquí.

## Lo que sigue fuera de alcance

- La CSP no protege de nada si hay un XSS en el propio origen (ahí `script-src 'self'` no ayuda). Lo
  que hace es quitarle la vía fácil: un `<script>` inyectado no se ejecuta.
- `frame-ancestors 'none'` ya impide el clickjacking, pero se mantiene `X-Frame-Options` porque
  navegadores viejos no leen la CSP.