# Ronda 94 — Canales conectados cuenta canales, no intentos (Etapa D)

Fecha: 2026-10-08.

## Dolor

`f1_canales_conectados` ("Canales con sincronización autorizada exitosa frente a los
canales configurados") calculaba `exitosas/intentadas`: la misma fórmula que
`f1_pruebas_sync`, con otro nombre. Tres indicadores decían lo mismo y ninguno decía
cuántos canales están conectados.

## Cambio

- `canalesConfigurados()` (`channels` con `activo=1`) y `canalesConSyncExitosa()`
  (distintos, configurados, con éxito en el periodo).
- `f1_canales_conectados` = conectados/configurados: sin ellos → SIN_DATOS.
- `f1_pruebas_sync` intacto: ese sí mide intentos.

## Verificación real

```text
3 tests nuevos vistos fallar (2 valores + 1 SIN_DATOS).
Suite menos EscaladoSqliteTest: 290 corridos, 0 fallos, 0 errores, 1 omitida.
```

## Archivos

- Tocados: `IndicadoresService.java`, `IndicadoresRepository.java`,
  `IndicadoresServiceTest.java`.
- Recuperado aquí porque `round-94/verificacion.md` lo ocupa la Ronda 94 del colega;
  el original salió en `8653270` y sigue ahí en el historial.

## Riesgos y límites

- Sin capturas: el informe ya mostraba fórmula y cociente.
- Coordinación: 92 y 93 los publicó el colega; esta evidencia se restauró sin tocar
  la suya.
