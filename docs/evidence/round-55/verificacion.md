# Ronda 55 — Cambiar las fechas de una reserva desde el panel

## Lo que faltaba

Mover una estancia exigía cancelar y recrear: se perdía el código, el precio y el hilo. Hermana
de la reasignación de la R54, con sus mismas reglas.

## Lo que se entrega

`POST /api/admin/reservas/{codigo}/fechas` con llegada y salida: solo vigentes, sin pisar a otra
reserva (la propia no cuenta como solape) ni a un bloqueo, con tarifa completa en las nuevas y
precio recalculado, todo en una transacción con rastro («fechas A→B a C→D»). Fechas ausentes o
invertidas son 400; lo inexistente, 404; lo ocupado o no vendible y las cerradas, 409.

En el detalle, formulario de Nueva llegada/salida junto al de reasignar, solo en PENDIENTE y
CONFIRMADA; el historial muestra el movimiento.

## Verificación

```text
.\mvnw.cmd test
Tests run: 280, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS

docker compose build api web  (tsc --noEmit + Vite en verde)
node tools/operacion/verificar-despliegue.mjs
despliegue: rutas de proxy, cabeceras y CSP coherentes   exit=0
```

En vivo: `H-DBBE5E47` del 1–3 al 10–12 de noviembre, total recalculado y línea en el historial.
Capturas: `cambiar-fechas.png` y `cambiar-fechas-movil.png`.

## Nota de git

Sin push por regla vigente. Solo archivos de esta ronda en el commit.