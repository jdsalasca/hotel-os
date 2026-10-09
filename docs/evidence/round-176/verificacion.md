# Ronda 176 - Una reserva cerrada no debe dinero

## El hueco
`PagosService.saldo()` calculaba el pendiente siempre como `total - abonado`, sin
mirar el estado de la reserva. Una reserva **CANCELADA** seguía mostrando el
total acordado como pendiente: el panel y el comprobante público del huésped
pedían pagar por algo que ya no se reservó.

Apareció en la captura de la ronda 166, que muestra una reserva cancelada con
*"Abonado: COP 0 · Pendiente: COP 3.000"*.

## El cambio
```java
long pendiente = reserva.estado().vigente() ? total - abonado : 0L;
```
Reutiliza `EstadoReserva.vigente()`, la misma regla que ya impide abonar en una
reserva cerrada (ronda 166). No se inventa una regla nueva.

Decisión: el **total acordado** y lo **abonado** se siguen enseñando — el total
es parte del histórico y lo abonado es dinero real (si se cobró antes de
cancelar, lo que toca es devolverlo). Lo único que no se inventa es la deuda.

## TDD rojo-verde
- Sin el cambio:
  `JSON path "$.pendienteCents" expected:<0> but was:<300000>`
  (300000 = los COP 3.000 de la captura).
- Con el cambio, `PagosAdminTest`: `Tests run: 8, Failures: 0, Errors: 0`.
- El test cubre las dos puertas: `/api/admin/reservas/{codigo}/saldo` y el
  comprobante público `/api/reservas/{codigo}/comprobante`.

## Nota de numeración
Se numeró 176 porque 175 lo ocupaba la ronda de "condiciones del hotel"
(`760f2f2`), que se commiteó mientras esta estaba en curso.

## Estado de la verificación
La suite backend **completa no pudo correr en verde** en este checkout: hay
trabajo en vuelo del agente paralelo (migración `V20__condiciones_acordadas.sql`
sin trackear y `ComprobanteService`/`ReservaRepository`/`ReservaService`
modificados) que rompe el arranque de los tests de contexto de Spring
(`no such table: hotel_config`). Ese trabajo **no está commiteado**: `origin/develop`
es sano. Los tests propios de la ronda pasan en aislamiento.