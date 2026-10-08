# Ronda 79 - El verificador de despliegue, contra prod

Fecha: 2026-10-08.

## Dolor
El checker de despliegue (ronda 30) solo se corría al cambiar el proxy. Con 20 rondas
encima sin pasarlo, nadie sabía si seguía verde.

## Cambio
Ninguno de código: pasar `tools/operacion/verificar-despliegue.mjs` y conciliar con vivo.
- Estático: **18/18 ok** (rutas /api, /oauth2, /login/oauth2; 5 cabeceras; CSP sin
  unsafe; vercel.json coherente; VITE_API_BASE vacía).
- Vivo: la CSP servida por `hotel.eridu.top` incluye `frame-src openstreetmap.org`
  (igual que el Dockerfile): el mapa no lo tumba la CSP.

## Archivos
- Solo este archivo + plan.
