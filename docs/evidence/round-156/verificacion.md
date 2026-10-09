# Ronda 156 - Simulacro semanal automático de restauración

## Cambio
- `tools/operacion/programar-simulacro.sh`: bucle (patrón de `programar.sh`) que
  corre `verificar-respaldo.sh` cada semana y grita al log si falla.
- Servicio `simulacro` en `compose.tunnel.yaml` (imagen de operación con sqlite,
  `/backups` en SOLO lectura —la copia vive en `/tmp`—, arranca corriendo una
  vez al desplegar). Dockerfile suma el script al COPY/chmod.
- `verificar-despliegue.mjs` exige el servicio (para que no se pierda).

## TDD rojo-verde
- Verificador: FALLA sin el servicio; con él, exit 0.
- Loop probado en contenedor real contra copia real de prod: 3/3
  "restauración simulada ok (esquema v17, 0 reservas legibles)".

## Verificación (salida real, 2026-10-09)
- En prod tras el deploy: `docker logs hotel-os-simulacro-1` con el drill verde
  del arranque.

## Despliegue
- Merge a `develop`, push y `compose up -d --build` en TopNUC.
