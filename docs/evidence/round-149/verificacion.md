# Ronda 149 - Sondeo OAuth2 sin credenciales

## Cambio
- `tools/operacion/probar-oauth2.mjs`: recorre la ida (302 a Google, redirect_uri
  https propia, state, JSESSIONID) y la vuelta con código falso (302 a la puerta
  con motivo). `--self-test` contra puerto cerrado prueba que el detector
  detecta. Sin credenciales, sin efectos: el código falso solo provoca el rebote
  ya previsto.

## Verificación (salida real, 2026-10-09)
- `--self-test` → detecta y exit 0.
- Contra prod → 7 ok + exit 0; el log del api registró
  `vuelta de Google fallida … [invalid_grant] Malformed auth code`, que prueba
  la cadena entera (proxy, registro, sesión, canje ante Google y traza).
- En el camino el propio sondeo cazó un falso negativo suyo (el header crudo
  trae la redirect_uri sin codificar): corregido a aceptar ambas formas.

## Despliegue
- Solo herramienta: sin cambio en la app, no hay rebuild. Merge + push.
- PENDIENTE DEL DUEÑO: reintentar Google para leer SU motivo en el log.
