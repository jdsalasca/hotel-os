# Ronda 84 - La puerta del panel, vista en vivo

Fecha: 2026-10-08.

## Dolor
La R82 dejó la puerta `/admin` sin verificación visual: los tests cubren la lógica pero
nadie había mirado la página servida por producción.

## Cambio
Ninguno de código: verificación visual en vivo.
- `https://hotel.eridu.top/admin` sin sesión: logo, nav con Habitaciones y Panel activo,
  aviso "Sesión requerida" y pie con Mapa. Captura `puerta-panel.png`.
- Consola: faro de Cloudflare (toggle del dueño) + 401 del sondeo sin sesión (diseño).
- Origin sin movimiento desde el último deploy: producción ya corre lo último.

## Archivos
- Solo este archivo + plan.
