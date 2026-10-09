# Ronda 155 - El sondeo se marca en el log

## Cambio
- `FalloOauth2`: con `?sondeo=1` registra `vuelta de Google fallida [sondeo]…`
  para filtrar el ruido conocido del vigía diario (6 líneas idénticas anoche
  confundieron una lectura).
- `probar-oauth2.mjs` manda `sondeo=1` en la vuelta falsa.

## TDD rojo-verde
- `FalloOauth2Test.sondeoMarcado` nuevo: sin la marca, falla; con ella, 3/3.

## Verificación (salida real, 2026-10-09)
- `FalloOauth2Test` 3/3, `ManejadorOAuthRobustoTest` 2/2 (sus WARN prueban el
  log nuevo); suite 438/443 (5 rojos del colega en sus archivos, cero errores).
- Un BUILD FAILURE intermedio en corrida combinada no reprodujo al repetir:
  flake de compilación paralela, documentado.
- Backend con el cambio; frontend sin cambios.

## Despliegue
- Merge a `develop`, push y `compose up -d --build` en TopNUC con health OK.
