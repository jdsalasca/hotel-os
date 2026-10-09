# Ronda 166 - Abono en reserva cerrada

## El hueco
`POST /api/admin/reservas/{codigo}/abonos` aceptaba dinero en una reserva CANCELADA o
RECHAZADA: devolvía 201 y el saldoPediente bajaba. Un reserva ya soltó la fecha y el
huésped quizá no viaja: anotar un abono ahí es un cobro sinاعي que ni el estado ni
el historialExplanation. El panel además ofrecía el formulario, así que el error
salía después de escribir el importe.

## Cambio
- `PagosService.abonar`: si la reserva no está vigente, 400 con motivo (usa
  `EstadoReserva.vigente()`, ya موجودة; no se inventó una regla nueva).
- `PaginaAdminReservas`: helper `admiteDinero(estado)` reutilizado en los tres
  puntos que ya comparaban estado a mano; la reserva cerrada muestra el motivo en
  lugar del formulario. Cero estilos nuevos.

## TDD rojo-verde
- Backend `PagosAdminTest.abonoEnCanceladaEs400`: 409 al reservar (la fecha caía
  fuera del rango tarifado, no era el fallo buscado) → con fechas dentro del
  rango, FALLA con `201` donde debe ser `400`; con el cambio, verde.
- Frontend `PaginaAdminReservas.test.tsx`: sin el cambio FALLA
  "una reserva cancelada no ofrece cobrar y lo explica"; con él 5/5.
  El caso contrario (PENDIENTE sí ofrece) queda cubierto para que el helper no
  cierre la puerta de más.

## Verificación (salida real, 2026-10-09)
- Backend: `PagosAdminTest` 7/7 (`Tests run: 7, Failures: 0, Errors: 0`).
- Frontend: 24 archivos / 99 tests + `tsc --noEmit` sin errores de tipos;
  `vite build` ok (70 módulos, 367 kB).
- Comportamiento en el stack real (contenedores, no mocks):
  - viva: abono 201 → saldo `abonado 150000 / pendiente 150000`
  - cancelada: `{"error":"la reserva está CANCELADA: no admite abonos (si hubo
    cobro, es una devolución, no un abono)"}` (400)
- Visual (Playwright, navegador real): `reserva-viva-con-abono.png` muestra la
  cuenta con el formulario y el abono registrado; `reserva-cancelada-sin-abono.png`
  muestra el aviso y el formulario ausente. El listado ya rotulaba CANCELADA como
  "Sin acciones"; ahora el detalle es coherente con esa fila.
- Stack throwaway (proyecto `hotelr166`, puertos 18080/15173) demolido al terminar:
  `down -v` con sus volúmenes, nada fuera del repo.

## Despliegue
- Merge a `develop`, push y `compose up -d --build` en TopNUC con health OK.
