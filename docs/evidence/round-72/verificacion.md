# Ronda 72 — Línea base, meta y responsable editables (Etapa D)

Fecha: 2026-10-08.

## Dolor

Línea base, meta y responsable llegaban en `null` hasta que el hotel las fijara, pero no
había dónde fijarlas sin SQL. El panel además ni mostraba el responsable.

## Cambio

- `IndicadoresRepository.fijarReferencia()` (`UPDATE … WHERE clave`, devuelve filas).
- `IndicadoresService.fijarReferencia()`: lee la definición (clave inexistente →
  `ReferenciaNoEncontradaException`), fusiona (lo ausente se conserva, lo vacío
  limpia), topa en 200 caracteres y devuelve lo guardado.
- `POST /api/admin/indicadores/definiciones/{clave}/referencia` → 200 con lo guardado,
  404 clave inexistente, 400 cuerpo ausente/vacío o tope pasado.
- Panel: cada tarjeta muestra Responsable y trae `<details>` con los tres campos
  prellenados + Guardar; al guardar relee el informe (lo que se ve es lo guardado).

## Verificación real

```text
IndicadoresServiceTest + IndicadoresEscriturasTest: BUILD SUCCESS, 0 fallos
(3 servicio + 2 contrato nuevos, vistos fallar antes: símbolos inexistentes).
Un test nuevo contaminó la BD compartida de su clase y tumbó
noSeInventanLineaBaseNiMeta: se aísla restaurando con la propia semántica de
limpieza (finally). Lección guardada.
Suite menos EscaladoSqliteTest: 268 corridos, 0 fallos, 0 errores, 1 omitida
(preexistente). BUILD SUCCESS.
docker compose build web → tsc + vite ok. npm run test:typecheck → exit 0.
npm test → 17/17 (sin cambios en tests esta ronda).

Prueba viva (imágenes reconstruidas, admin demo, /admin/indicadores):
- referencia f1_canales_conectados = base/meta/responsable → etiquetas visibles
- recarga muestra lo guardado; vaciar los tres por API → 200 con nulls (limpieza)
- datos dev restaurados a null tras la prueba.
Captura: referencia-desktop.png (esta carpeta). Consola: sin errores nuevos.
```

## Archivos

- Tocados: `IndicadoresRepository.java`, `IndicadoresService.java`,
  `IndicadoresController.java`, `IndicadoresServiceTest.java`,
  `IndicadoresEscriturasTest.java`, `PaginaAdminIndicadores.tsx`,
  `_componentes.scss` (`.referencia`), `docs/plan.md`.
- Nuevos: esta carpeta.

## Riesgos y límites

- Sin cambios de contrato en informe/CSV: el informe ya traía los tres campos.
- Trabajo del colega respetado: sin solapes en esta ronda (`git status` limpio tras
  el commit).
