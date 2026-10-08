# Ronda 138 - Límite de paginación con contrato en el panel (Etapa F)

Fecha: 2026-10-08. Rama: `goal/round-138-limite`.

## Misión (brainstorming, vía acotada)

Opciones: (a) validar `limite` en lista+CSV —bounded, volcado evitable—;
(b) H6 rol (delgado + zona activa del colega); (c) check-in (fuera de ventana).
Va (a): en SQLite `LIMIT -1` es sin tope y `?limite=-1` volcaba la tabla.

## Cambio (`AdminReservasController.java` + tests)

- `listar` y `exportarCsv` rechazan `limite < 1` con 400 (el CSV con su línea
  `error,...` como el 400 de estado que ya tenía).
- `@ExceptionHandler(MethodArgumentTypeMismatchException)`: `?limite=muchas`
  vuelve al contrato `{error}` en español en vez del 400 crudo de Spring.
- Tests nuevos en `AdminReservasLimiteTest` (4, clase propia para no pisar los
  tests del colega en `AdminReservasControllerTest`): negativo y cero en lista,
  no-numérico en lista, negativo en CSV.

## Verificación real

- Rojo→verde: 4×200 donde 400 → 4/4 en verde.
- Suite backend: **432 tests, 427 verdes, 5 rojos ajenos, 1 omitido**. Los 5 son
  los tests de cambiar-huéspedes del colega (404 sin endpoint: su TDD en curso,
  intactos); lo mío y el resto, verdes.
- Sin UI ni migración en la ronda.

## Trabajo en equipo

- Solo mis rutas en el commit (controlador + test + evidencia + plan). Lo del
  colega (sus tests de huéspedes, OAuth, hotel-vacio) intacto y fuera.

## Archivos

- Tocados: `reservas/AdminReservasController.java`,
  `test/.../reservas/AdminReservasControllerTest.java`.
- Nuevos: este archivo. Plan: entrada de ronda 138.

## Pendiente

- H6; identidad por (subject, issuer); check-in/out; calendario con arrastre.
