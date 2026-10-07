# Ronda 18 — Límite de reservas públicas y dos fallos que salieron al verificar

## 1. `POST /api/reservas` escribía en la base sin ningún límite

Es el único endpoint público que inserta filas, y no tenía sesión ni tope. Un bucle con
`curl` reserva todo el inventario con correos inventados y los huéspedes reales ven que no hay
disponibilidad. El daño no es una caída: es perder la venta.

`LimiteReservas` cuenta por IP, 10 reservas cada 15 minutos, en memoria por la misma razón que
`LoginThrottle`: instancia única, no hay segundo proceso donde repartir el contador. El mapa tiene
techo de 10 000 claves, porque traffic no autenticado con una IP distinta por petición llenaría la
memoria.

### La afirmación que hubo que verificar

El límite usa `request.getRemoteAddr()`, y toda la web llega a través del nginx del contenedor
web. Si eso devolviera la IP del proxy, **todos los huéspedes compartirían un cubo y la web se
cerraría al undécimo visitante**. Medido contra los contenedores reales:

```text
12 peticiones con 12 X-Forwarded-For distintos:  201 201 201 201 201 201 201 201 201 201 201 201
12 peticiones con la misma IP:                  201 201 201 201 201 201 201 201 201 201 201 429 429
```

`server.forward-headers-strategy=framework` hace que `getRemoteAddr()` sea la IP real, así que el
límite separa a las personas y no a los contenedores. El mensaje del 429:

```json
{"error":"demasiadas reservas seguidas desde esta conexión. Inténtalo en unos minutos."}
```

## 2. `MensajeError` anteponía un encabezado a todos los errores

El huésped bloqueado leía esto:

> **No se pudo completar la operación.** demasiadas reservas seguidas desde esta conexión…

Dos frases pegadas sin espacio, con mayúscula y punto antes de una minúscula. `MensajeError`
metía `<strong>No se pudo completar la operación.</strong>` delante de cualquier mensaje. Como
todos los mensajes del servidor ya son frases completas, el encabezado no aportaba nada y estorbaba
en toda la aplicación, no solo en el 429.

Ahora `titulo` es opcional: sin él, el mensaje del servidor es el aviso y va en negrita.

```html
<!-- antes -->
<div class="aviso aviso--error"><strong>No se pudo completar la operación.</strong><span>…</span></div>
<!-- después -->
<div class="aviso aviso--error"><strong>demasiadas reservas seguidas desde esta conexión. …</strong></div>
```

Evidencia visual: `docs/evidence/round-18/limite-429.png`,捕获 en el navegador real con el
formulario enviado de verdad.

## 3. nginx dejaba la web caída si se recreaba solo la API

Este salió al ejecutar las capturas, que fallaron con un 504 que no se explicaba.

nginx resuelve un nombrehost en `proxy_pass` **una sola vez, al arrancar**. Al recrear el
contenedor de la API, esta toma una IP nueva y el web sigue apuntando a la vieja: 504 para todo,
hasta que alguien reinicia el web. Medido:

```text
desde dentro del contenedor web:  wget http://api:8080/api/health  ->  {"estado":"ok"}   (funciona)
a través del nginx:              curl localhost:5174/api/health    ->  504                (no)
```

Con `resolver 127.0.0.11 valid=10s` y el upstream en una variable, nginx vuelve a resolver cada
10 segundos. Recreando **solo** la API, con el mismo contenedor web:

```text
t+41 ms   -> 502
t+2091 ms -> 502
t+4218 ms -> 200      web recreado durante la prueba: NO
```

Cuatro segundos y se recupera solo. Antes era permanente. Esto también afecta a producción:
cambiar la imagen de la API con `--force-recreate` era una caída total hasta reiniciar el web.

## Verificación

```text
.\mvnw.cmd test
Tests run: 178, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS

docker compose ... capturas
capturas completas sin errores de consola ni de API (35 capturas)
```

`MensajeError` lo usan casi todas las pantallas, así que la suite completa de capturas es la
prueba de que este cambio no rompió ningún estado de error.

## Lo que NO cubre

- El contador está en memoria: reiniciar la API libera los cubos. Es coherente con el despliegue
  de instancia única, y un atacante necesitaría reiniciar el servidor para que no le sirviera.
- El límite es por IP, así que una red grande (hotel con wifi de clientes) comparte cubo para
  personas distintas. Con 10 por 15 minutos aguanta, pero si algún día molesta, la salida es
 ucket por IP + huella del cliente, no subir el número.
- `GET /api/disponibilidad` es público y golpea la base sin límite. Es de lectura y no consume
  inventario, así que se deja así a propósito.
- Limitado a reservas. `POST /api/admin/**` ya exige sesión de administrador.