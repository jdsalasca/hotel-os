# Ronda 81 - La suite completa delató al WIP del colega (reporte, sin tocarlo)

Fecha: 2026-10-08.

## Dolor
Suite completa en rojo (354 tests, 12 errores) sin saber de quién era la culpa.

## Hallazgo (verificado, no adivinado)
- `AuditoriaAdminTest.elLoginQuedaAtribuido` (archivos en progreso del colega, sin commitear):
  el login revienta con `DelegatingPasswordEncoder: el hash no trae prefijo de algoritmo`.
- Ese fallo tumba su contexto y envenena la caché: `Oauth2AdminTest` (4) y
  `SecurityIntegrationTest` (7) caen en cascada con "failure threshold exceeded" solo en
  suite completa (solos pasan).
- Sin esa clase: **345/345 verde, BUILD SUCCESS** — todo lo commiteado (mío y suyo) está sano.
- No toqué sus archivos: el arreglo es suyo (revisar qué hash se guarda en su setup).

## Archivos
- Solo este archivo + plan.
