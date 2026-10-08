# Ronda 123 - Parámetros malformados con contrato en la búsqueda (Etapa F)

Fecha: 2026-10-08. Rama: `goal/round-123-contrato-400`.

## Misión (brainstorming, vía acotada)

Opciones: (a) simetría de validación en disponibilidad —bounded, contrato
público—; (b) rol honesto (delgado solo); (c) descargable (zona del colega).
Va (a): `huespedes=muchos` devolvía el 400 de Spring sin `$.error`.

## Problema (reproducido con tests antes de afirmar)

- `GET /api/disponibilidad?huespedes=muchos`, `detalle?roomId=cualquiera` y
  búsqueda sin `llegada` respondían 400 pero con el cuerpo por defecto de Spring,
  sin `$.error` y en otro idioma: el frontend no podía mostrar el motivo.
  Rojo: 3× `No value at JSON path "$.error"` (23 tests, 3 fallos).
- Los ceros (`huespedes=0` en las tres lecturas) ya eran 400 con motivo vía
  servicio: quedaron como candados, no como fix.

## Cambio (`DisponibilidadController.java` + tests)

- `@ExceptionHandler(MethodArgumentTypeMismatchException)`: 400 con motivo en
  español (`huespedes` → "número de huéspedes inválido", `roomId` →
  "identificador de habitación inválido", resto con su nombre).
- `@ExceptionHandler(MissingServletRequestParameterException)`: 400 nombrando el
  parámetro que falta.
- Alcance solo a este controlador (sin advice global): el resto no se toca.
- Tests nuevos en `DisponibilidadControllerTest` (4): no-numérico, ceros en las
  tres lecturas, roomId no-numérico, llegada ausente.

## Verificación real

- Rojo→verde: 23/23 en la clase; suite backend **399 tests, 0 fallos, 0 errores,
  1 omitido** (`PermisosDeVolumenTest`, `@DisabledOnOs(WINDOWS)` preexistente) +
  BUILD SUCCESS. Sin JVMs ajenas durante la corrida.
- Frontend y capturas no aplican (cambio solo backend, sin marcas visibles).

## Trabajo en equipo

- Solo mis 2 rutas en el commit. En el árbol hay un `puerta-desktop.png`
  sin commitear del colega (su R121): intacto y fuera de mi commit.

## Archivos

- Tocados: `disponibilidad/DisponibilidadController.java`,
  `test/.../disponibilidad/DisponibilidadControllerTest.java`.
- Nuevos: este archivo. Plan: entrada de ronda 123.

## Pendiente

- H6 (rol real en `/api/admin/sesion`); identidad por (subject, issuer);
  comprobante descargable; calendario con arrastre.
