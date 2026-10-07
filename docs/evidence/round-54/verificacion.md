# Ronda 54 — Reasignar la habitación de una reserva desde el panel

## Lo que faltaba

Si la habitación asignada tenía un problema o el huésped pedía cambiarse, no había forma: solo
cancelar y volver a crear, perdiendo código e historial.

## Lo que se entrega

`POST /api/admin/reservas/{codigo}/habitacion` con `roomId`: solo reservas vigentes, habitación
libre y vendible en esas fechas, precio recalculado con la nueva y todo en una transacción.
404 si no existe la reserva o la habitación; 409 si no está vigente, está ocupada o no está a la
venta. El movimiento queda en el historial con detalle («habitación 101 → 102»), para lo que el
historial ganó columna `detalle` (migración V11, NULL en lo anterior).

En el detalle de la reserva: desplegable de habitaciones + Reasignar, solo en PENDIENTE y
CONFIRMADA; el historial muestra el detalle cuando lo hay.

## Verificación

```text
.\mvnw.cmd test
Tests run: 277, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS

docker compose build api web  (tsc --noEmit + Vite en verde)
node tools/operacion/verificar-despliegue.mjs
despliegue: rutas de proxy, cabeceras y CSP coherentes   exit=0
```

En vivo: `H-15FC1175` de la 101 a la 102 → comprobante en la 102 con total recalculado (15600) e
historial «PENDIENTE→PENDIENTE · habitación 101 → 102 por admin». Capturas:
`reasignar-habitacion.png` y `reasignar-habitacion-movil.png`.

## Nota de git

Sin push por regla vigente. Solo archivos de esta ronda en el commit.