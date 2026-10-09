# Ronda 152 - Vigía OAuth diario en prod

## Cambio
- `tools/operacion/vigilar-oauth.sh`: bucle que corre `probar-oauth2.mjs` a diario
  (patrón de `programar.sh`) y grita al log si falla. El sondeo usa código falso:
  en el log del api se ve como "Malformed auth code" (ruido conocido); un fallo
  real diría otra cosa.
- Servicio `vigia` en `compose.tunnel.yaml` (node:22-alpine + `./tools/operacion`
  en `/sondeo`, solo red, sin volúmenes de datos). Solo el túnel vigila prod.
- `verificar-despliegue.mjs` exige el servicio (para que no se pierda como los
  respaldos en la ronda 78).

## TDD rojo-verde
- Verificador: FALLA sin el servicio; con él, exit 0.
- Loop probado en contenedor con intervalo de 8 s: 2 iteraciones verdes.
- `docker compose config` válido (con dummy local para el token requerido).

## Verificación (salida real, 2026-10-09)
- En prod tras el deploy: `docker logs hotel-os-vigia-1` con el sondeo en verde.
- Detección, no aviso: si falla, queda en el log (no hay buscapersonas).

## Despliegue
- Merge a `develop`, push y `compose up -d --build` en TopNUC.
