# Ronda 162 - Caché HTTP con criterio

## Hallazgo
El origen no enviaba `Cache-Control`: los assets con hash se revalidaban siempre
y el HTML quedaba al azar del borde (4 h por defecto) — cada deploy tardaba
horas en verse.

## Cambio
- `location /assets/` → `Cache-Control: public, max-age=31536000, immutable`.
- `location = /index.html` → `Cache-Control: no-cache` (revalida siempre).
- En ambos se repiten las 5 cabeceras de seguridad: con `add_header` en un
  location, nginx olvida las del nivel server (documentado ahí mismo).
- `verificar-despliegue.mjs` exige ambas reglas.

## TDD rojo-verde
- Verificador: FALLA sin las reglas; con ellas, exit 0.
- `nginx -t` sobre la config extraída, en `nginx:1.27-alpine`: sintaxis ok.

## Verificación (salida real, 2026-10-09)
- En origen tras el deploy: asset con `max-age=31536000, immutable`, `/` con
  `no-cache`, resto de cabeceras intactas en ambas rutas.

## Despliegue
- Merge a `develop`, push y `compose up -d --build` en TopNUC con health OK.
