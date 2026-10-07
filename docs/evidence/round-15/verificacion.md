# Ronda 15 — Precio acordado y comprobante de reserva

Fecha: 2026-10-06. Rama `develop`.

## Entregable

Cada reserva congela su precio al confirmarse (total, moneda y plan) y el huésped y el panel
pueden ver el comprobante: habitación, fechas, total acordado, identidad del hotel e historial.
Si la reserva se hizo sin tarifa configurada, el comprobante dice "pendiente de tarifar" en lugar
de inventar un importe. No es una factura: no hay pagos ni impuestos, solo el resumen verificable.

## Por qué el snapshot

Antes, el total de una reserva solo existía mientras las tarifas no cambiaran: recalcularlo hoy
con los precios de hoy reescribe la historia. La prueba que lo fija: se reserva a 150.000 la
noche, el hotel sube a 200.000 después, y el comprobante sigue mostrando 300.000.

Migración `V4__precio_acordado.sql`: `total_cents`, `moneda` y `rate_plan_id` anulables en
`reservations`. NULL significa "sin tarifa al reservar", no cero.

## Separación de capas

- `InventarioService.precioDe` calcula con las mismas reglas de la oferta pública (activa,
  capacidad, tarifa completa) reutilizando `calcularPrecio`, que ahora también devuelve el plan.
- `ReservaService.crear` pide el precio dentro de la misma transacción y lo guarda junto a la
  reserva; la aceptación de la reserva no cambia.
- `ComprobanteService` (nuevo, solo lectura) arma reserva + habitación + hotel + historial.
- Los controladores exponen dos rutas finas:
  `GET /api/reservas/{codigo}/comprobante?email=` (público, misma compuerta que la consulta) y
  `GET /api/admin/reservas/{codigo}/comprobante` (panel, sin correo).

## Verificación real

```text
.\mvnw.cmd test
Tests run: 170, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

```text
npx tsc --noEmit
TSC_SIN_ERRORES
```

```text
docker compose -f compose.yaml -f compose.capturas.yaml up --build --force-recreate \
  --abort-on-container-exit capturas
capturas completas sin errores de consola ni de API
```

Casos nuevos:

- Reserva con tarifa guarda total 300.000, moneda COP y plan.
- Reserva sin tarifa guarda NULL en las tres columnas.
- Precio posterior modificado no mueve el comprobante.
- Comprobante público con correo ajeno: 404.
- Comprobante del panel sin correo: 200 con habitación y total.

## Interfaz verificada a mano

- `docs/screenshots/escritorio-04b-comprobante.png` y `movil-04b-comprobante.png`: el huésped
  consulta con código + correo y ve habitación, fechas, total acordado con plan, estado e
  historial, con botón de impresión (la navegación se oculta al imprimir).
- `docs/screenshots/escritorio-06b-detalle.png` y `movil-06b-detalle.png`: el detalle del panel
  muestra habitación y total acordado junto al historial.
- La consulta pública ahora usa el comprobante directamente: misma compuerta, más datos, una sola
  llamada en vez de dos.