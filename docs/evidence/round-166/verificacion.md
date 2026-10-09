# Ronda 166 - Abono en reserva cerrada, huespedes de la reserva y plazo de espera

> Esta seccionAmplia una ronda a medias: los tests de huespedes ya estaban escritos
> y en rojo (404, el endpoint no existia). Se completo la implementacion y se anadio
> el plazo de espera del cliente HTTP. Abajo, la parte ya verificada del abono.

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

---

# Ampliacion - Cambiar huespedes de una reserva

## El hueco
Habia tests escritos y **en rojo**: `POST /api/admin/reservas/{codigo}/huespedes` y
`POST /api/mis-reservas/{codigo}/huespedes` devolvian 404, el endpoint no existia
(5 fallos). Recepcion no podia corregir el grupo de una reserva: se cancelaba y se
volvia a crear, losing el historial y el abono.

## Cambio
- `ReservaService.cambiarHuespedes`: dominio unico para panel y huesped. Solo
  vigentes; el precio se recalcula con el grupo nuevo (`inventario.precioDe` ya
  valida capacidad, asi que subir de capacidad sale 409 sin tocar nada); todo en la
  transaccion de `SqliteTransactionExecutor` con rastro en auditoria.
- `ReservaRepository.actualizarHuespedes`: un UPDATE, solo la cabecera.
- `AdminReservasController` y `HuespedController`: los dos endpoints. El del huesped
  comprueba duenaDe() y devuelve 404 para la ajena (igual que el comprobante propio,
  no 403: no se confirma que exista).

Sin migracion nueva: `huespedes` ya existia en `reservations`.

## TDD rojo-verde
- Rojo reproducido primero: `Tests run: 52, Failures: 5` con
  `No static resource api/admin/reservas/H-4081BB5A/huespedes`.
- Verde: 52/52 en las tres clases tocadas.
- Al arrancar el test, `cambiarHuespedesDeCanceladaEs409` fallaba al **crear** la
  reserva con 409, no en lo que queria probar: los tests comparten la habitacion 101
  y `12-26→12-28` chocaba con `siguientes3` (`12-25→12-27`). Movido a `12-28→12-30` y
  la ventana de tarifa de la clase hasta el 30.

## Verificacion (salida real, 2026-10-09, stack de contenedor)
HTTP contra el backend levantado, no mocks:

| Peticion | Resultado |
|---|---|
| viva, `huespedes: 3` | 200, `"huespedes":3`, fechas sin mover |
| viva, `huespedes: 9` (capacidad 3) | 409 `esa habitacion no admite 9 huéspedes en esas fechas` |
| cancelada, `huespedes: 1` | 409 `solo se cambian los huéspedes de una reserva vigente` |
| huesped sin sesion | 401 |
| huesped B sobre reserva de A | 404, y la fila sigue con 1 huesped |

Visual (Playwright + Chrome del sistema, perfil aislado en TEMP):
- `02b-detalle-viva-pago.png`: cuenta con saldo real (abonado 1.000 / pendiente
  4.400), formulario de abono y el historial con `huéspedes 2 → 3 por admin`.
- `03b-detalle-cancelada-pago.png`: la cancelada sin formulario y el motivo escrito.
- `04-movil-reservas.png`: 390 px, sin desborde.
- `erroresJS=0` en todas las pasadas.

## Nota de entorno
- `node_modules` traia el binario de esbuild de Linux (copiado de una imagen Docker):
  `npm ci` en `apps/web` lo deja bien en Windows. Sin esto `vitest` no arranca.
- Los perfiles por defecto de los MCP de navegador los tiene ocupados el otro agente
  que trabaja en el repo: las capturas van con `playwright-core` global y Chrome del
  sistema en un perfil de TEMP. No se toco ninguna sesion ajena.

## Pendiente de esta ampliacion
El panel **no tiene todavia** formulario para cambiar huespedes: el endpoint existe y
esta probado, pero la pantalla no lo expone. El huesped tampoco. Queda para la
siguiente ronda.
