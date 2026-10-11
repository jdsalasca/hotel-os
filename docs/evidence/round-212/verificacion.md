# Ronda 212 — El correo refleja el estado real de la solicitud

## Hallazgo reproducido

Al crear una reserva, el backend persiste el estado `PENDIENTE` y la pantalla pública dice “Pendiente de confirmación”. El correo de R209 decía “reserva confirmada”, tanto en el asunto como en el cuerpo. El huésped recibía dos versiones incompatibles del estado de su reserva.

La prueba nueva `CorreoConfirmacionTest.comunicaElEstadoPendienteSinPrometerConfirmacion` reprodujo el defecto antes del cambio:

```text
Tests run: 4, Failures: 1, Errors: 0, Skipped: 0
AssertionFailedError: el hotel: reserva confirmada ==> expected: <true> but was: <false>
```

## Corrección

- Asunto: “solicitud de reserva recibida”.
- Cuerpo: informa que se recibió la solicitud y muestra “Pendiente de confirmación”.
- Se conserva el código, fechas, huéspedes y la ruta para consultar la reserva.
- No se afirma que el hotel haya confirmado una solicitud en estado `PENDIENTE`.

## Verificación

- Prueba enfocada: `cd apps/api; .\mvnw.cmd -q -Dtest=CorreoConfirmacionTest test` — **exit code 0**, 4 pruebas.
- Suite completa: `cd apps/api; .\mvnw.cmd -q test` — **exit code 0**, 474 pruebas, 0 fallos, 0 errores, 1 omitida (63 informes Surefire).
- Artefacto: `cd apps/api; .\mvnw.cmd -q -DskipTests package` — **exit code 0**.
- `git diff --check` — sin errores de whitespace.

## Límite que continúa abierto

Esta ronda corrige lo que el correo afirma; no demuestra que el proveedor lo entregue. La implementación de R209 envía después de guardar la reserva y registra un fallo en el log, pero aún no tiene outbox duradero ni reintento automático. La configuración real y una prueba autorizada de entrega siguen pendientes de la Etapa 8. Las pruebas de esta ronda solo inspeccionan el texto del mensaje y no usan datos personales ni un proveedor externo.
