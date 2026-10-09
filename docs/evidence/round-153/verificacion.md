# Ronda 153 - El sondeo cubre las dos puertas OAuth

## Cambio
- `probar-oauth2.mjs`: `--registro google-admin|google-huesped` y por defecto
  las dos (el vigía desplegado las cubre sin tocar nada). La redirect_uri se
  valida contra el host de la base, no hardcodeada. Además: base inválida ya no
  revienta con throw sino FALLA limpio (lo cazó el propio uso).
- El login de huéspedes usa la misma cookie y manejadores endurecidos: nunca se
  había probado su camino; ahora sí.

## TDD rojo-verde
- `--registro google-huesped` con la versión anterior: `Invalid URL` (la tomaba
  como base). Con el flag: 7 ok contra prod en cada puerta.
- `--self-test` sigue detectando (exit 0 tras detectar).

## Verificación (salida real, 2026-10-09)
- Ambas puertas: ida 302 a Google con redirect https propia, state, sesión;
  vuelta 302 a su puerta con motivo; exit 0. `verificar-despliegue.mjs` exit 0.

## Despliegue
- Solo herramienta: merge + push, sin rebuild.
