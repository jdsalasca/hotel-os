# Ronda 93 - Login en celular, verificado

Fecha: 2026-10-08.

## Dolor
El reporte de "login pegado" sin más datos obligaba a mirar la pantalla tal como la ve
el dueño en su teléfono.

## Cambio
Ninguno de código: verificación visual en 390px.
- `/admin/entrar` en móvil: logo, título, campos etiquetados a ancho completo, Entrar y
  Google apilados, pie con accesos. Nada roto ni cortado. Captura `login-movil.png`.
- Origin sin movimiento: producción ya corre lo último, nada que desplegar.
- Consola: faro de Cloudflare + 401 del sondeo (diseño, documentado en R64).

## Archivos
- Solo este archivo + plan.
