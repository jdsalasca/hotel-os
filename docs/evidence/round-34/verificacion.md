# Ronda 34 — La reserva pública ya no se vende sin precio

## Lo que hacía

La búsqueda solo ofrece habitaciones con precio totalizado, pero el alta aceptaba cualquier
`roomId` —e incluso ninguno— por otro camino. Sin habitación elegida se quedaba con la primera
libre, sin mirar capacidad ni tarifas. Y el servicio guardaba la reserva igual con el total en
NULL. Había hasta un test que lo bendecía: `sinTarifaGuardaNuloEnVezDeInventarUnPrecio`.

Vender sin importe: el huésped confirma sin saber cuánto paga y el comprobante sale sin total.

## La regla nueva, en el servicio

`ReservaService.crear` solo lo usa el flujo público, así que la regla vive ahí sin necesidad de
un contrato administrativo todavía: **sin precio completo no hay reserva**. `precioDe` ya codifica
las reglas de la oferta —activa, capacidad suficiente, tarifa completa, noches abiertas y
restricciones de estancia—, así que un solo rechazo cubre la habitación retirada, la que no
alcanza, la noche sin tarifa y la inexistente:

```text
409 la habitación no está a la venta para esas fechas y huéspedes
```

Y sin `roomId` el controlador ya no pide la primera libre sino la primera **vendible**
(`disponiblesConPrecio` con los huéspedes pedidos). Si no hay ninguna, 409 con motivo en vez de
una reserva sin precio. Cero huéspedes sin habitación sigue siendo 400 con su motivo, no un 409
que culpe a la disponibilidad. Además esto cierra un NPE latente: antes, sin habitaciones libres,
`primeraDisponible` devolvía null y reventaba al desempaquetar el `long`.

Lo que queda fuera a propósito: el plan se sigue eligiendo como el primero activo con tarifa
completa, igual que la búsqueda. Que el huésped vea y elija el plan explícitamente es la siguiente
ronda de este flujo, no esta.

## Tests que hubo que actualizar (y por qué está bien)

Nueve clases reservaban en habitaciones sin tipo ni tarifas porque medían otra cosa —límites,
idempotencia, CSRF, auditoría, panel, concurrencia—. Todas recibieron inventario vendible en su
`@BeforeEach` mediante un ayudante compartido nuevo, `HotelDePrueba.tarifarTodo`, en vez de nueve
bloques copiados. Ningún test cambió lo que mide; solo el decorado que el alta ahora exige.

## Verificación

```text
.\mvnw.cmd test
Tests run: 232, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS

node tools/operacion/verificar-despliegue.mjs
despliegue: rutas de proxy, cabeceras y CSP coherentes   exit=0
```

Contra el servidor en marcha:

| Caso | Antes | Ahora |
|---|---|---|
| Flujo normal (oferta con precio → `roomId`) | 201 | 201 `H-6040A36B` PENDIENTE |
| `roomId` inexistente (9999) | 201 con total NULL | 409 «no está a la venta…» |
| Sin `roomId` en fechas sin tarifas (2031) | 201 con total NULL | 409 «no hay habitaciones a la venta…» |

Los dos errores de consola del navegador son esos dos 409 de la propia sonda, esperados.

## Nota de git

Por la regla vigente —sin push sin autorización—, esta ronda queda commiteada en `develop` sin
subir al remoto. El commit pendiente de push es el de esta ronda.