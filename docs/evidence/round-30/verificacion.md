# Ronda 30 — Comprobación de la configuración de despliegue

## El problema que no tiene cobertura

El flujo de Google se rompió **dos veces** en esta sesión por lo mismo: se añadió una ruta al
backend y el proxy siguió reescribiendo solo `/api/*`.

- Ronda 22: `GET /oauth2/authorization/google-admin` devolvía **200 con el `index.html` del SPA**.
- Ronda 23: lo mismo con `google-huesped`.

En los dos casos lo detectó alguien mirando la respuesta a mano. Ni las 205 pruebas del backend ni
el guion de capturas lo ven, porque **el backend respondía bien**: lo que faltaba era el camino, y
una ruta que nadie pide nunca se nota en un test de servicio.

El arreglo de cada vez fue añadir `location /oauth2/` y `location /login/oauth2/` al nginx de la
imagen, y los reescrituras equivalentes a `vercel.json`. Nada impedía que la tercera vez se
olvidara.

## Lo entregado

`tools/operacion/verificar-despliegue.mjs`: comprueba lo que un test de servicio no puede mirar.

- Las tres rutas que deben llegar al backend: `/api/`, `/oauth2/` y `/login/oauth2/`, en el nginx
  **y** en los reescrituras de Vercel.
- Las cinco cabeceras de seguridad, en ambos.
- Que la CSP no se relaja con `unsafe-inline` ni `unsafe-eval`, en ambos.
- Que `VITE_API_BASE` esté vacía, por lo de la ronda 29.

Lee la configuración de nginx **del Dockerfile**, que es donde vive (va incrustada con un heredoc),
así que corre sin Docker, sin construir la imagen y sin desplegar nada.

## Que detecte fallos, no que diga que todo va bien

Una comprobación que nunca falla no sirve. Se probó rompiendo justo lo que se rompió dos veces:

```text
$ # renombrada 'location /oauth2/' -> 'location /oauth2-eliminada/'
FALLA el proxy reenvía /oauth2/ al backend (si no, el SPA contesta con su index.html)
DESPLIEGUE: 1 comprobación(es) sin cuadrar
exit=1

$ # restaurado
despliegue: rutas de proxy, cabeceras y CSP coherentes entre nginx y Vercel
exit=0
```

Un detalle que salió al escribirlo: la primera versión daba un fallo falso, porque el `Dockerfile`
**explica en un comentario por qué no usa `unsafe-inline`**, y la búsqueda de la palabra pulsaba
contra el comentario. Ahora se ignoran las líneas de comentario, que es lo correcto.

## Lo que esta comprobación NO cubre

- Que Vercel realmente resuelva el reescritura y la cookie atraviese su proxy. Eso necesita un
  despliegue real.
- Los tres compose: se validan a mano y en la ronda 8, pero exigiría el CLI de Docker, que el
  script no necesita para nada más.
- El arranque del contenedor y el healthcheck, que sí cubren las capturas y `docker compose up`.

Se dejó así a propósito: una comprobación que depende de Docker y de construir imágenes solo se
puede ejecutar en una máquina con Docker, y justo donde hace falta (una máquina limpia o un CI) es
donde no está.

## Verificación del resto

```text
.\mvnw.cmd test
Tests run: 205, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS
```