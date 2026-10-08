# Ronda 110 - Simulacro de restauración que sí muerde

Fecha: 2026-10-08.

## Diseño (brainstorming, vía acotada)
Respaldar sin probar restaurar es fe: opciones (a) checks ad-hoc (no reutilizables),
(b) `verificar-respaldo.sh` commiteado que restaura a temporal + integrity + esquema +
tablas (elegido: corre en el contenedor, sin tocar /data), (c) pipeline auto-restore
(sobrediseño, toca datos). TDD para scripts: positivo y negativo antes de commitear.

## Verificación real (las tres en el servidor, contenedor hotel-ops)
- Positivo: `restauración simulada ok (esquema v15, 0 reservas legibles)`, exit 0.
- Negativo corrupto: `ERROR: sqlite falló (PRAGMA...): file is not a database`, exit 1.
- Negativo vacío: `ERROR: no hay respaldos`, exit 1. Sintaxis `sh -n` ok.
- Bug real cazado por el negativo: con `set -eu`, un sqlite3 que falla dentro de `$()`
  mataba el script en silencio con exit 0. Todo sqlite pasa por `consulta()`, que
  convierte el fallo en ERROR con motivo y exit 1.
- Despliegue + health `ok`.
- Archivos del otro agente intactos.

## Archivos
- Nuevo: `tools/operacion/verificar-respaldo.sh`, este archivo.
