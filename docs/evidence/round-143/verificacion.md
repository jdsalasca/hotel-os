# Ronda 143 - La consulta tolera espacios (Etapa F, lado lectura)

Fecha: 2026-10-08. Rama: `goal/round-143-consulta-recorta`. (Se trabajó como
142 sin saber que el colega la ocupaba; la 142 es suya, intacta.)

## Misión (brainstorming, vía acotada)

Opciones: (a) recorte bilateral en consulta —bounded, cierra la R139—; (b) H6
rol (delgado + zona activa del colega); (c) check-in (fuera de ventana). Va
(a): la R139 recorta al guardar; lo preguntado y las filas viejas seguían sin
verse, y sin consulta no hay cancelación ni comprobante.

## Cambio (`ReservaService.consultar` + tests)

- Compara recortado en ambos lados con resguardo de nulos (antes un email nulo
  guardado era NPE→500). Cubre consulta, cancelar y comprobante público, que
  consultan por aquí; el panel no cambia (no pide correo).
- Tests nuevos en `ReservaServiceTest.Consulta` (2): pregunta con espacios
  encuentra; fila vieja con espacios (insert directo, sin backfill) se encuentra
  limpia.

## Verificación real

- Rojo→verde: 2 fallos en consulta segura → `ReservaServiceTest` 32/32.
- Suite backend: **439 tests, 434 verdes, 5 rojos ajenos, 1 omitido**. Los 5 son
  los tests de cambiar-huéspedes del colega (404 sin endpoint: su TDD en curso,
  intactos); lo mío y el resto, verdes.
- Sin UI ni migración en la ronda.

## Trabajo en equipo

- Solo mis rutas en el commit (servicio + test + evidencia + plan). Lo del
  colega intacto y fuera (su 142 restaurada con `checkout` tras detectar mi
  sobreescritura a tiempo, antes de commitear).

## Archivos

- Tocados: `reservas/ReservaService.java`, `test/.../reservas/ReservaServiceTest.java`.
- Nuevos: este archivo. Plan: entrada de ronda 143.

## Pendiente

- H6; identidad por (subject, issuer); check-in/out; calendario con arrastre.
