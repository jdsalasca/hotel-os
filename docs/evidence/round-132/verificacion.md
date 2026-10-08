# Ronda 132 - La CSP deja pasar el beacon de Cloudflare

## Reporte
La consola grita en cada página: el beacon de Cloudflare
(`static.cloudflareinsights.com/beacon.min.js`, analítica sin cookies inyectada
en el borde) choca con `script-src 'self'` y el navegador lo bloquea.

## Cambio
- `script-src 'self' https://static.cloudflareinsights.com` en `apps/web/Dockerfile`
  (nginx, prod) y `vercel.json` (misma cadena). Nada más se abre; el resto de la
  CSP queda igual de cerrada.

## Verificación (salida real, 2026-10-08)
- Cabecera servida por prod con el host incluido (curl -I).
- Recarga de `/admin/entrar` en navegador: sin el error CSP del beacon.
- `docker compose config` válido antes del deploy (el cambio es solo nginx).
