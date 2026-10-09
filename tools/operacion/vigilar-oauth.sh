#!/bin/sh
# Vigía OAuth: sondeo diario del camino ida-vuelta de Google contra prod.
#
# Por qué existe: la rotura de SameSite de la ronda 151 solo la vio el dueño, días
# después, porque nada la vigilaba. Este bucle corre probar-oauth2.mjs a diario y
# deja el resultado en el log del contenedor: un fallo futuro queda registrado sin
# esperar a nadie. El sondeo usa código falso a propósito; en el log del api se ve
# como "Malformed auth code" (ruido conocido y filtrable), un fallo real diría otra
# cosa (p. ej. authorization_request_not_found).
#
# Variables:
#   VIGIA_INTERVAL_SECONDS  segundos entre sondeos (por defecto, una vez al día)
# Uso en compose: servicio `vigia` con ./tools/operacion montado en /sondeo.
set -eu

BASE="${1:-https://hotel.eridu.top}"
INTERVALO="${VIGIA_INTERVAL_SECONDS:-86400}"

echo "vigía OAuth cada $INTERVALO s contra $BASE"
while true; do
  node /sondeo/probar-oauth2.mjs "$BASE" \
    || echo "vigía: SONDEO FALLIDO a las $(date -u +%FT%TZ), revisar el camino OAuth" >&2
  sleep "$INTERVALO"
done
