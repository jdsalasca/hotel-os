#!/bin/sh
# Simulacro de restauración programado, sin demonio cron (igual que programar.sh).
#
# Por qué existe: verificar-respaldo.sh solo corría a mano; una corrupción dormida
# se descubriría el día del desastre. Este bucle lo corre solo y deja el resultado
# en el log del contenedor: ok ruidoso una vez por semana, ERROR ruidoso siempre.
#
# Variables:
#   SIMULACRO_INTERVAL_SECONDS  segundos entre simulacros (por defecto, una vez a la semana)
# Uso en compose: servicio `simulacro` con /backups en solo lectura (la copia va a /tmp).
set -eu

INTERVALO="${SIMULACRO_INTERVAL_SECONDS:-604800}"
DESTINO="${1:-/backups}"

echo "simulacro de restauración cada $INTERVALO s sobre $DESTINO (solo lectura)"
while true; do
  /usr/local/bin/verificar-respaldo.sh "$DESTINO" \
    || echo "simulacro FALLIDO a las $(date -u +%FT%TZ): el respaldo NO sirve, mirar ya" >&2
  sleep "$INTERVALO"
done
