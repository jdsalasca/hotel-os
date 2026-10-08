# Ronda 78 - Respaldos en modo túnel + higiene

Fecha: 2026-10-08.

## Dolor
El modo túnel nunca tuvo respaldo programado: `hotel-backups` estaba vacío en prod y el
`respaldo` solo existía en dev/producción. Además, `respaldar.sh` no se podía ejecutar
directo (sin shebang: `exec format error`).

## Cambio
- Servicio `respaldo` en `compose.tunnel.yaml` (mismo `programar.sh` diario + retención) y
  `BACKUP_INTERVAL_SECONDS` documentado en `.env.tunnel.example`.
- Shebang `#!/bin/sh` en `respaldar.sh`.
- Higiene: poda de contenedores detenidos (344 MB, incluidos los keycloak de hace 3 años) e
  imágenes colgadas. Caché de build intacta para no lentear deploys; disco en 20%.

## Verificación real
- Servicio `respaldo` arriba; primer respaldo automático al arrancar + uno manual:
  `hotel-20261008T015128Z.sqlite3 (236K, 25 tablas, integridad ok)`.
- `api` y `web` sanos; health `ok`.
- Archivos del otro agente intactos (auditoría sigue suya, sin tocar).

## Archivos
- Tocados: `compose.tunnel.yaml`, `.env.tunnel.example`, `tools/operacion/respaldar.sh`.
- Nuevo: este archivo.
