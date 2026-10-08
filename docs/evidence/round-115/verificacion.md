# Ronda 115 - La reserva y su vínculo a la cuenta en una sola unidad (Etapas G + D)

Fecha: 2026-10-08. Rama: `goal/round-114-vinculo-atomico` (número de rama previo a
saber que la 114 era del colega; la evidencia y el plan mandan: 115).

## Problemas (Etapa G, reproducidos antes de afirmar)

- **H1.** Crear reserva y vincularla a la cuenta eran dos escrituras separadas:
  `svc.crear()` confirmaba su transacción y `vincular()` corría después en autocommit.
  Si el vínculo fallaba, la reserva quedaba huérfana con 500.
- **H3.** Con sesión abierta y otro correo en el cuerpo, la reserva se enganchaba a la
  cuenta de la sesión sin correspondencia: el comentario del controlador lo prohibía
  ("si reserva para otro correo, no se engancha"), pero el código solo lo cumplía
  cuando la sesión aún no tenía fila en `usuarios`. Con identidad previa, se enganchaba.
- **H2 (de regalo, en vivo).** El test nuevo de H3 creó el segundo `usuarios` con el
  mismo correo y la petición reventó con `IncorrectResultSizeDataAccessException`
  (500): `idPorEmail` exigía exactamente una fila. El duplicado panel+Google con el
  mismo correo es un caso real (el admin que reservó para sí y luego entra con Google).

## Cambio

- `ReservaService.crear(..., Long usuarioId)` (nueva sobrecarga; la de 4 args delega
  con null): el `vincular` corre dentro del mismo `tx.enTransaccion`, en el alta y en
  el reintento idempotente. El resguardo `usuario_id IS NULL` ya existía y no pisa dueño.
- `ReservaController.crear`: resuelve el id ANTES del alta y solo si la sesión reclama
  SU correo (`equalsIgnoreCase` con trim; vacío no engancha). Admin que reserva para sí
  conserva su identidad de panel; para otro correo, nada. Se eliminó el `vincular`
  posterior.
- `UsuariosHuespedRepository.idPorEmail`: desempate determinista por antigüedad
  (`ORDER BY id LIMIT 1`) en vez de 500. Es un desempate, no una identidad: resolver
  por (subject, issuer) sigue pendiente (siguiente ronda).
- Constructor `ReservaService` con `ReservaServiceHuesped`; actualizadas las 2
  construcciones manuales (`ReservaServiceTest`, `EscaladoSqliteTest`).

## TDD rojo-verde (salida real)

- Rojo H3 admin: `adminConIdentidadReservaAjenaNoSeEngancha` →
  `expected: <null> but was: <1>`.
- Rojo/error H3 huésped: `reservarParaOtroCorreoNoSeEngancha` → 500 por H2 (ver arriba).
- Verde tras el fix: `ReservaVinculadaTest` 6/6 + `AdminReservaPropiaTest` 4/4.
- Cerrojoss nuevos en el mismo verde: `correoDuplicadoNoEs500` (201 contra la más
  antigua) y `vinculoFallidoNoDejaHuerfana` (usuario 999999 → FK revierte el alta,
  conteo intacto). Estos dos se escribieron sobre el fix ya en verde como regresión;
  el H2 quedó probado en rojo por el error del H3, y el de atomicidad no compilaba
  sin la sobrecarga.
- Suite completa: `Tests run: 393, Failures: 0, Errors: 0, Skipped: 1` + BUILD SUCCESS
  (el omitido es `PermisosDeVolumenTest`, `@DisabledOnOs(WINDOWS)` preexistente).
  Corridas escalonadas con el colega (0 JVMs ajenas) para no repetir la colisión de
  `target/` de la ronda 113.

## Trabajo en equipo

- Solo mis rutas en el commit (3 main + 4 test + evidencia + plan). Lo del colega
  (chat, HiloMensajes, PaginaAdminReservas, PaginaMisReservas) intacto y ya commiteado
  por él en su ronda 114.
- Evidencia en `round-115/` nuevo; la `round-114/` es suya y no se tocó.

## Archivos

- Tocados: `reservas/ReservaService.java`, `reservas/ReservaController.java`,
  `huespedes/UsuariosHuespedRepository.java`,
  `test/.../huespedes/ReservaVinculadaTest.java`,
  `test/.../reservas/AdminReservaPropiaTest.java`,
  `test/.../reservas/ReservaServiceTest.java`,
  `test/.../reservas/EscaladoSqliteTest.java`.
- Nuevos: este archivo. Plan: entrada de ronda 115.

## Pendiente (no de esta ronda)

- Identidad por (subject, issuer) en vez de correo (H2 de fondo), rotación de sesión
  en login manual (H5), logout que no declare éxito si el servidor falla (H4),
  STAFF sin permisos (H6): informe del revisor ya en mano para las próximas rondas.
