#!/bin/sh
# Respaldos programados sin demonio cron: un bucle con sleep entre copias.
#
# Por qué un bucle y no cron: la imagen ya trae los scripts y bash; un demonio cron exigiría
# su configuración, sus logs y su supervisión por el mismo precio. El sleep cobra solo cuando
# el contenedor vive, que es exactamente cuando hay algo que respaldar.
#
# Variables:
#   BACKUP_INTERVAL_SECONDS  segundos entre copias (por defecto, una vez al día)
#   BACKUP_RETENTION_DAYS    las pasa a respaldar.sh (por defecto, 30 días)
# Uso en compose: command: ["/usr/local/bin/programar.sh"]
set -eu

INTERVALO="${BACKUP_INTERVAL_SECONDS:-86400}"
DB="${1:-/data/hotel.sqlite3}"
DESTINO="${2:-/backups}"

echo "respaldos programados cada $INTERVALO s: $DB -> $DESTINO"
while true; do
  /usr/local/bin/respaldar.sh "$DB" "$DESTINO" \
    || echo "respaldo fallido a las $(date -u +%FT%TZ), se reintenta en $INTERVALO s" >&2
  sleep "$INTERVALO"
done
