# Ronda 94 — Canales conectados cuenta canales, no intentos (Etapa D)

Fecha: 2026-10-08.

## Dolor

`f1_canales_conectados` ("Canales con sincronización autorizada exitosa frente a los
canales configurados") calculaba `exitosas/intentadas`: la misma fórmula que
`f1_pruebas_sync`, con otro nombre. Tres indicadores decían lo mismo y ninguno decía
cuántos canales están conectados.

## Cambio

- `canalesConfigurados()` (`channels` con `activo=1`) y `canalesConSyncExitosa()`
  (distintos con `exitosa=1` en el periodo, solo de configurados).
- `f1_canales_conectados` = conectados/configurados: sin configurados o sin sync,
  SIN_DATOS con motivo; si no, porcentaje con numerador y denominador.
- `f1_pruebas_sync` intacto: ese sí mide intentos.

## Verificación real

```text
3 tests nuevos vistos fallar (2 valores + 1 SIN_DATOS).
IndicadoresServiceTest verde; suite menos EscaladoSqliteTest: 290 corridos,
0 fallos, 0 errores, 1 omitida (preexistente). BUILD SUCCESS.
```

## Archivos

- Tocados: `IndicadoresService.java`, `IndicadoresRepository.java`,
  `IndicadoresServiceTest.java`, `docs/plan.md`.
- Nuevos: esta carpeta (sin capturas: sin cambios de UI; el informe ya mostraba
  nombre, fórmula, numerador y denominador).

## Riesgos y límites

- Coordinación: 92 y 93 los publicó el colega (verificado antes de crear); 94 libre.
  Trabajo del colega respetado en todo.
