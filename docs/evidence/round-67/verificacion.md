# Ronda 67 — Edición masiva de tarifas con preview (cierra Etapa C)

Fecha: 2026-10-07.

## Dolor

La pantalla guardaba las noches con peticiones sucesivas: un fallo a mitad dejaba el mes
a medias y un éxito genérico ocultaba qué se guardó. Un precio válido + un mínimo
inválido ya no escribe a medias desde la Ronda 61, pero solo noche por noche.

## Cambio

Backend (`TarifaService`, TDD: 5 tests de servicio vistos fallar + 4 de contrato):
- `CambioNoche` / `FilaPrevia` / `PreviaLote(lista)` / `LoteRechazadoException(previa)`.
- `previsualizarLote()`: valida cada fila sin escribir; lo inválido viaja por fila.
- `aplicarLote()`: valida todo y escribe en una sola transacción; una fila inválida
  revierte todo (`LoteRechazadoException` con la previa).
- `fijarNoche()` reutiliza el mismo `resolverNoche()`: idénticas reglas en individual y lote.
- Lote vacío o de más de 366 noches → 400. Lo omitido se conserva, incluido el cierre.
- `POST /api/admin/tarifas/lote/preview` → 200 `{lista, filas}` (fecha mal escrita =
  error de su fila, mostrando la cruda). `POST /api/admin/tarifas/lote` → 201
  `{guardadas, noches}` o 400 `{error, filas}`.

Frontend (`PaginaAdminInventario.tsx`, estilos existentes + `.previa-fila--invalida`):
- «Guardar precios del mes» ahora es «Revisar cambios» → previa (Antes/Después/Estado
  por noche, motivo por fila) → «Confirmar y guardar N noches» / «Corregir».
- Confirmar deshabilitado si alguna fila falla; el motivo dice que si una falla no se
  guarda ninguna. Al confirmar, las noches se releen del servidor (antes la tabla
  quedaba con el borrador aunque el guardado fallara).

## Verificación real

```text
InventarioServiceTest: Tests run: 42, Failures: 0, Errors: 0
LoteTarifasControllerTest (nuevo): Tests run: 4, Failures: 0, Errors: 0
  - previa 200 sin escribir · lote válido 201 · fila mala 400 con filas y nada escrito
  - fecha "ayer" = error de su fila, no de la petición
CalendarioControllerTest: 7/7 (el refactor de fijarNoche no cambió reglas)

docker compose build web → tsc --noEmit limpio + vite build ok
Playwright contra web:5174 + api:8080 (admin demo, plan Flexible/EUR + tipo Doble):
- noche 08-oct 90 → 95: previa «Antes 90 / Después 95 / Cambia», Confirmar →
  previa cerrada y el 95 recargado del servidor (no del borrador)
- previa de 09-oct 95 → 96 descartada con Corregir (no escribió)
- 08-oct restaurada a 90 por el mismo flujo (los datos dev quedan como estaban)
Consola: solo 401 de chequeos de sesión sin login.
Captura: lote-previa-desktop.png (esta carpeta)
```

## Archivos

- Tocados: `TarifaService.java`, `InventarioAdminController.java`,
  `InventarioServiceTest.java`, `PaginaAdminInventario.tsx`, `_componentes.scss`,
  `docs/plan.md`.
- Nuevos: `LoteTarifasControllerTest.java`, esta carpeta.

## Riesgos y límites

- Vaciar un precio en el borrador se envía como 0 (comportamiento heredado, sin cambios).
- `cambiarEstado` inexistente sigue en 400 (contrato existente, Ronda 66).
- Trabajo del colega respetado: no se tocaron `chat/`, `V15`, `SecurityConfig`,
  `indice.scss`, `_chat.scss`, `PaginaMisReservas.tsx` ni `HiloMensajes.tsx`.
