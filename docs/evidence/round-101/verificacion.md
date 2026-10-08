# Ronda 101 — Tarifas por rango y días de semana sobre el lote atómico (Etapa C/I)

Fecha: 2026-10-08.

## Dolor

La Etapa C pedía edición por rango y por días de la semana; el lote solo aceptaba
fechas sueltas. El hotel no puede tarifar "fines de semana de noviembre" sin mandar
30 noches una por una.

## Diseño (bounded)

El rango se expande a noches y sigue el mismo camino del lote (preview + aplicar
atómicos): cero reglas duplicadas. Días en ISO 1–7, ausentes = todos; rango
inclusivo con tope de 366 noches como el lote.

## Cambio

- `POST /api/admin/tarifas/lote/rango/preview` (200 con filas) y
  `/api/admin/tarifas/lote/rango` (201 o 400 con detalle): `rango{desde, hasta,
  diasSemana}` + valores comunes; expansión + validación en `expandirRango()`.
- `previsualizarLote`/`aplicarLote` refactorizados a `vistaPrevia()`/`aplicaLote()`
  compartidos por ambos pares de endpoints.

## Verificación real

```text
3 tests nuevos vistos fallar (404 sin endpoints); en el camino, mi cuenta de días
(lun–vie del 3 al 10 son 6 con el lunes 10) y el helper de lectura con rango fijo.
LoteTarifasControllerTest: 7/7.
Suite menos EscaladoSqliteTest: 295 corridos, 0 fallos, 0 errores, 1 omitida.
BUILD SUCCESS.

Prueba viva (admin demo, plan/tipo reales del dev):
- rango 02–08 nov, sáb+dom → 200 lista:true, ["2026-11-07","2026-11-08"].
- sin sesión → 403 (correcto).
```

## Base: el lote lee el rango una vez (Ronda 99 original)

Esa evidencia (`round-99/`) la ocupa hoy la Ronda 99 del colega; el texto original
sigue en el commit `3eea3c2`. Resumen: previa de 10 en 3 lecturas y aplicar en 13
sentencias (`resolverConPrevia` + `previasPorFecha`, sin doble resolución ni
relectura), con `LoteRendimientoTest` (previa ≤ 4, aplicar ≤ 16). El rango de esta
ronda se apoya en ese lote.

## Archivos

- Tocados: `InventarioAdminController.java`, `LoteTarifasControllerTest.java`,
  `docs/plan.md`.
- Nuevos: esta carpeta (sin capturas: sin UI; el panel la usará después).

## Riesgos y límites

- Sin UI todavía: el panel sigue con el flujo por mes; la previa/confirmación del
  rango queda para la pantalla.
- Coordinación: 97/99/100 del colega (su `round-99` intacto, restaurado); 101 libre.
  Trabajo del colega respetado en todo.
