# Ronda 141 - Sin reservas hacia el pasado (Etapa F)

Fecha: 2026-10-08. Rama: `goal/round-141-pasado-no`.

## Misión (brainstorming, vía acotada)

Opciones: (a) rechazar llegadas pasadas —bounded, invariante—; (b) H6 rol
(delgado + zona activa del colega); (c) check-in (fuera de ventana). Va (a):
la Etapa F lo lista ("fechas pasadas") y una PENDIENTE en el pasado nace
siendo no-show. Horizonte/estancia máxima quedan fuera a propósito: son
política del hotel, no invariante.

## Cambio

- `ReservaService.validar` y `cambiarFechas`: `llegada < hoy` → 400 "la llegada
  no puede ser en el pasado". Hoy sí vale (el walk-in existe). Cubre web, panel
  y lo que venga (un solo punto de corte).
- Tests nuevos en `ReservaServiceTest.Pasado` (3): llegada pasada 400 sin
  escribir, hoy aceptado (candado anti off-by-one con tarifas del día), mover al
  pasado 400 sin moverse.
- `EscaladoSqliteTest.rendimientoDeReservas` movido a fechas futuras (medía
  caudal con enero: el qué mide no cambia, solo el calendario).

## Verificación real

- Rojo→verde: la pasada llegaba a inventario (409/500-ish) en vez de frontera;
  `ReservaServiceTest` 28/28.
- Suite backend: **437 tests, 432 verdes, 5 rojos ajenos, 1 omitido**. Los 5 son
  los tests de cambiar-huéspedes del colega (404 sin endpoint: su TDD en curso,
  intactos); lo mío y el resto, verdes.
- Sin UI ni migración en la ronda.

## Trabajo en equipo

- Solo mis rutas en el commit (servicio + 2 tests + evidencia + plan). Lo del
  colega intacto y fuera.

## Archivos

- Tocados: `reservas/ReservaService.java`,
  `test/.../reservas/ReservaServiceTest.java`,
  `test/.../reservas/EscaladoSqliteTest.java`.
- Nuevos: este archivo. Plan: entrada de ronda 141.

## Pendiente

- H6; identidad por (subject, issuer); check-in/out; calendario con arrastre;
  horizonte y estancia máxima (política del hotel).
