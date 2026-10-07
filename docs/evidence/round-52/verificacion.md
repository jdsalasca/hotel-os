# Ronda 52 — Buscar y filtrar reservas en el panel

## Lo que faltaba

La lista del panel era todo o nada (hasta 100 sin orden de búsqueda): encontrar la reserva de un
huésped que llama por teléfono exigía recorrerla a ojo.

## Lo que se entrega

`GET /api/admin/reservas` acepta `q` (código, correo o nombre, sin mayúsculas; los comodines se
buscan literales) y `estado` (PENDIENTE/CONFIRMADA/CANCELADA/RECHAZADA; otro valor es 400, no una
lista vacía silenciosa). En la pantalla, barra con buscador, desplegable de estado y botón
Filtrar; sin resultados, vacío con motivo en vez de tabla vacía.

## Verificación

```text
.\mvnw.cmd test
Tests run: 271, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS

docker compose build api web  (tsc --noEmit + Vite en verde)
node tools/operacion/verificar-despliegue.mjs
despliegue: rutas de proxy, cabeceras y CSP coherentes   exit=0
```

En vivo: buscar «r50-cancela» deja una sola fila; estado CANCELADA solo trae canceladas.
Capturas: `filtro-reservas.png` (escritorio) y `filtro-reservas-movil.png` (390 px, la barra
apila campo, desplegable y botón).

## Nota de git

Sin push por regla vigente. Solo archivos de esta ronda en el commit.