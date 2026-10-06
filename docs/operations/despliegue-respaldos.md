# Despliegue y respaldos (resumen Ronda 1, detalle en Ronda 6)
- SQLite en `/data/hotel.sqlite3` vía volumen nombrado `hotel-data`. Nunca solo en FS efímero ni dentro de imagen.
- Backups: volumen/destino configurable `hotel-backups` + `BACKUP_RETENTION_DAYS`. Copia consistente con `sqlite3 .backup` / `VACUUM INTO`, nunca copia en caliente del fichero abierto sin checkpoint. Restauración probada en Ronda 6.
- Healthchecks + `depends_on: condition: service_healthy` + orden de arranque.
- Dev: `compose.yaml` (puertos 8080/5173). Prod: `compose.production.yaml` (solo 443/80 vía proxy, HTTPS + dominio `${DOMAIN}`, cabeceras seguras, mínima exposición).
- Actualización prod: backup previo → `compose pull/build` → migrar → verificar → rollback con backup.
- ⚠️ NUNCA `docker compose down -v` en producción (borra volúmenes). El volumen local no protege si se destruye VM/disco: se exige copia externa (pasos en Ronda 6).
- Imagen Java: `eclipse-temurin:25-jre` (mantenida, compatible Java 25 + sqlite-jdbc).
