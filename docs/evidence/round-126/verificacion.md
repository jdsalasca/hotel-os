# Ronda 126 - El panel crea reservas manuales (Etapa H, rebanada API)

Fecha: 2026-10-08. Rama: `goal/round-126-alta-manual`. (Se trabajó como 124 sin
saber que el colega la ocupaba con hotel-vacio; la 124 y la 125 son suyas.)

## Misión (brainstorming, vía acotada)

Opciones: (a) alta manual del panel, rebanada API —bounded, flujo de recepción—;
(b) H6 rol (delgado solo + zona SecurityConfig activa del colega); (c) check-in
(estado + UI, fuera de ventana). Va (a): sin esto, recepción no existe.

## Cambio

- `POST /api/admin/reservas` en `AdminReservasController`: email/nombre/fechas/
  huéspedes/roomId (+idempotencia opcional) → 201 con la fila del panel, origen
  OTRO, precio vigente congelado. Habitación requerida (la asigna recepción);
  400 entrada inválida; 409 sin disponibilidad o idempotencia en conflicto.
  Reusa `svc.crear` (mismas reglas, idempotencia y transacción que la web) sin
  handshake de precio (el hotel tarifa a sabiendas) y sin enganche a cuentas
  (H3: la manual es del huésped, no del admin logueado).
- **V18**: el CHECK de `origen` había perdido OTRO en las reconstrucciones V6/V8
  (igual que RECHAZADA en V8) y la primera manual daba 500. Reconstrucción con
  el patrón de V8, sin tocar migraciones aplicadas.
- Tests nuevos en `AdminReservasControllerTest` (5) con habitación tarifada
  aislada: crea+lista OTRO/PENDIENTE, correo malo 400 sin fila, sin habitación
  400, solape 409, fechas mal 400.

## Verificación real

- Rojo→verde: 405/500/errores → 33/33 en la clase.
- Suite backend **409 tests, 0 fallos, 0 errores, 1 omitido** (preexistente) +
  BUILD SUCCESS, sin JVMs ajenas.
- Ruta de actualización con sqlite3 real: en V17 con una fila WEB, `OTRO`
  falla con CHECK; tras aplicar V18 entra y la vieja sigue (`H-OTRA|OTRO`,
  `H-VIEJA|WEB`). Creación limpia cubierta por la suite (18 migraciones).
- Sin UI en la rebanada (el formulario del panel es la próxima): sin capturas.

## Trabajo en equipo

- Solo mis rutas en el commit (controlador + test + V18 + evidencia + plan).
  Lo del colega (PuertaAdmin/OAuth/hotel, rondas 124-125) intacto y fuera.
- Auditoría: el alta deja actor OTRO en dominio + el filtro admin registra al
  admin de la sesión (granularidad fina del actor, pendiente si se quiere).

## Archivos

- Tocados: `reservas/AdminReservasController.java`,
  `test/.../reservas/AdminReservasControllerTest.java`.
- Nuevos: `db/migration/V18__origen_otro_en_reservas.sql`, este archivo.
- Plan: entrada de ronda 126.

## Pendiente

- Formulario "Nueva reserva" en el panel (consume este endpoint); check-in/out;
  H6; identidad por (subject, issuer).
