# Ronda 130 - El huésped mueve sus fechas sin llamar (Etapa H)

Fecha: 2026-10-08. Rama: `goal/round-130-fechas-huesped`.

## Misión (brainstorming, vía acotada)

Opciones: (a) cambio de fechas del huésped —bounded, paridad autoservicio—;
(b) H6 rol (delgado + zona SecurityConfig activa del colega); (c) check-in
(estado + UI, fuera de ventana). Va (a): el panel ya mueve fechas; el huésped no.

## Cambio (`HuespedController.java` + test nuevo)

- `POST /api/mis-reservas/{codigo}/fechas` {llegada, salida}: 401 sin sesión,
  404 si no es suya (sin oráculo de existencia, como el comprobante propio),
  400 fechas ausentes/invertidas, 409 no vigente o sin hueco, 200 con las fechas
  y el precio recalculado. Delega en `ReservaService.cambiarFechas` (misma
  transacción, excluyéndose a sí misma del solape); sin tocar `SecurityConfig`
  (el matcher `/api/mis-reservas/**` ya lo cubre) y sin ciclos de dependencias.
- Tests en `HuespedFechasTest` (5): dueño mueve y persiste, ajena 404 intacta,
  anónima 401, encima de otra 409 intacta, mal escritas 400.

## Verificación real

- Rojo→verde: 4×404 + 1×401-lucky → 5/5 en la clase.
- Suite backend **414 tests, 0 fallos, 0 errores, 1 omitido** (preexistente) +
  BUILD SUCCESS, escalonada con el colega (0 JVMs ajenas).
- Sin UI en la rebanada (el formulario en Mis reservas es la próxima): sin
  capturas. Sin migración (reusa tablas y reglas).

## Trabajo en equipo

- Solo mis rutas en el commit (controlador + test + evidencia + plan). Lo del
  colega (OAuth/hotel-vacio/PuertaAdmin, incluido su WIP en `PaginaInicio.*`)
  intacto y fuera.

## Archivos

- Tocados: `huespedes/HuespedController.java`.
- Nuevos: `test/.../huespedes/HuespedFechasTest.java`, este archivo.
- Plan: entrada de ronda 130.

## Pendiente

- Formulario de cambio en Mis reservas; check-in/out; H6; identidad por
  (subject, issuer).
