# Ronda 137 - El plan elegido se respeta al reservar (Etapa B)

Fecha: 2026-10-08. Rama: `goal/round-137-plan-elegido`.

## Misión (brainstorming, vía acotada)

Opciones: (a) validar el plan elegido —bounded, embudo central—; (b) H6 rol
(delgado + zona activa del colega); (c) check-in (fuera de ventana). Va (a):
elegir el segundo plan atrapaba en 409 y al aceptar cambiaba de plan en silencio.

## Problema (reproducido con tests antes de afirmar)

`ReservaService.crear` comparaba lo esperado solo contra el PRIMER plan válido:
con el segundo plan elegido y vigente, el alta daba 409; y el 409 traía los
datos del primero, así que al aceptar el huésped reservaba otro plan sin saberlo.
Rojo: `PrecioCambiadoException` donde debía haber código; 409 con
`(300000, plan 1)` donde debía traer `(400000, plan 2)`.

## Cambio

- `InventarioService.precioDe(..., planId)`: valida el plan concreto (activo +
  mismas reglas) en vez del primero; vacío si no cubre.
- `ReservaService.crear`: con plan esperado se valida ese (precio movido → 409
  con sus datos); si ya no cubre, 409 con lo vigente para reconfirmar (incluye
  plan inexistente, como ya fijaba `planDistintoAlDeLaBusquedaEs409`); sin plan
  esperado, igual que antes. Todo en la misma transacción.
- Tests nuevos en `ReservaServiceTest.PrecioAcordado` (3 + candado): reserva el
  segundo plan, 409 del plan movido con sus datos, retirado trae lo vigente.
  El frontend no cambia (ya envía y reconfirma `ratePlanId`).

## Verificación real

- Rojo→verde: 1 fallo + 1 error + 1 candado → `PrecioAcordado` 7/7.
- Suite backend: **427 tests, 422 verdes, 5 rojos ajenos, 1 omitido**. Los 5
  son los tests de cambiar-huéspedes del colega (404 sin endpoint: su TDD en
  curso, intactos); lo mío y el `planDistintoAlDeLaBusquedaEs409` existente,
  verdes.
- Sin migración, sin UI en la ronda.

## Trabajo en equipo

- Solo mis rutas en el commit (2 main + test + evidencia + plan). Lo del colega
  (sus tests de huéspedes, OAuth, hotel-vacio) intacto y fuera.

## Archivos

- Tocados: `inventario/InventarioService.java`, `reservas/ReservaService.java`,
  `test/.../reservas/ReservaServiceTest.java`.
- Nuevos: este archivo. Plan: entrada de ronda 137.

## Pendiente

- H6; identidad por (subject, issuer); check-in/out; calendario con arrastre.
