# Ronda 76 — El detalle es oferta vendible: ocupada o bloqueada es 404 (Etapa F)

Fecha: 2026-10-08.

## Dolor

`detalleOferta()` revisaba habitación activa, capacidad y tarifa, pero no reservas ni
bloqueos: una habitación con reserva vigente encima devolvía un desglose con pinta de
vendible, mientras la búsqueda (que sí los mira) no la ofrecía. Dos verdades distintas
para la misma noche.

## Cambio

- `InventarioService.detalleOferta()`: exige que la habitación esté en
  `inventario.disponibles(desde, hasta)` (la misma regla de la búsqueda, sin
  duplicarla). Contrato cumplido: el detalle es oferta vendible.
- Sin cambios de controlador ni frontend: el 404 con motivo ya existía para "no a la
  venta" y ahora cubre ocupada y bloqueada.

## Verificación real

```text
3 tests de servicio nuevos (libre existe, ocupada ausente, bloqueada ausente) +
1 de contrato (ocupada por reserva pública real → 404): vistos fallar antes del fix.
En el camino, el test de contrato necesitó CSRF en el POST público (patrón de
ReservaPublicaTest): 403 → .with(csrf()).
InventarioServiceTest + DisponibilidadControllerTest: 63/63.
Suite menos EscaladoSqliteTest: 275 corridos, 0 fallos, 0 errores, 1 omitida
(preexistente). BUILD SUCCESS.

Prueba viva (imagen api reconstruida, admin demo, habitación 1, noche 08-oct):
- libre → detalle 200; bloqueo real → detalle 404; bloqueo retirado → detalle 200.
- datos dev restaurados (bloqueo de prueba retirado).
```

## Archivos

- Tocados: `InventarioService.java`, `InventarioServiceTest.java`,
  `DisponibilidadControllerTest.java`, `docs/plan.md`.
- Nuevos: esta carpeta (sin capturas: sin cambios de UI).

## Riesgos y límites

- Una consulta SQL más por detalle (la de libres): mismo costo que la búsqueda.
- Trabajo del colega respetado: sin solapes en esta ronda.
