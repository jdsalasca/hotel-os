# Ronda 139 - El correo se guarda recortado (Etapa F)

Fecha: 2026-10-08. Rama: `goal/round-139-email-recortado`.

## Misión (brainstorming, vía acotada)

Opciones: (a) normalizar email en la frontera —bounded, bug real—; (b) H6 rol
(delgado + zona activa del colega); (c) check-in (fuera de ventana). Va (a):
la Etapa F lo nombra explícito ("email con espacios y normalización").

## Problema (reproducido con tests antes de afirmar)

`ReservaService.validar` recortaba para mirar pero `insertar` guardaba crudo:
`" ana@example.com "` pasaba y quedaba invisible para la consulta con el correo
limpio; y reintentando la idempotencia con otro recorte abría otra reserva (o un
409 consigo misma). El record ya recortaba `nombre`; al `email` le faltaba.
Rojo: guardado `'  ana@example.com  '` + consulta vacía; reintento 409.

## Cambio (`CrearReserva.java` + tests)

- Una línea en el constructor compacto: `email` recortado (null se respeta para
  el 400 de validación). Un solo punto de corte para web, panel y futuro; sin
  minúsculas (los espacios nunca son legítimos, la caja sí podría distinguir).
- Tests nuevos en `ReservaServiceTest.Normalizacion` (2): guarda recortado y
  consultable; reintento con espacios distintos devuelve la misma.

## Verificación real

- Rojo→verde: 1 fallo + 1 error → `ReservaServiceTest` 27/27.
- Suite backend: **434 tests, 429 verdes, 5 rojos ajenos, 1 omitido**. Los 5 son
  los tests de cambiar-huéspedes del colega (404 sin endpoint: su TDD en curso,
  intactos); lo mío y el resto, verdes.
- De paso, en el mismo archivo: colisión paralela (llave de más por edición
  simultánea que cerraba la clase antes de tiempo + helper `admin()` barrido).
  Restaurado lo estructural verbatim, cero lógica; verificado que sus tests
  siguen en su rojo diseñado y no en error de compilación.
- Sin UI ni migración en la ronda (normalización en memoria, sin backfill: las
  filas viejas con espacios, si las hay, se buscan con el correo tal cual).

## Trabajo en equipo

- Solo mis rutas en el commit (record + test + evidencia + plan). Lo del colega
  (sus tests de huéspedes, OAuth, hotel-vacio) intacto y fuera.

## Archivos

- Tocados: `reservas/CrearReserva.java`, `test/.../reservas/ReservaServiceTest.java`.
- Nuevos: este archivo. Plan: entrada de ronda 139.

## Pendiente

- H6; identidad por (subject, issuer); check-in/out; calendario con arrastre.
