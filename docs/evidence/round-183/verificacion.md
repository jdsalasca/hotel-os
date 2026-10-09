# Ronda 183 — búsqueda de disponibilidad primero

Fecha: 2026-10-09

## Cambio

En la portada activa, el formulario, el calendario y los resultados de disponibilidad
se muestran justo después del hero. Antes aparecían después de la galería, los pasos
de reserva y el mapa. El enlace «Ver disponibilidad» conserva su destino y ahora lleva
a un buscador cercano al inicio. No cambiaron la API ni las reglas de reserva.

## TDD

- Se agregó una prueba que exige que el formulario y el calendario antecedan a la galería.
- La prueba falló antes del cambio: `compareDocumentPosition` recibió `2` (la galería
  precedía al formulario; la expectativa era `4`).
- Tras mover el bloque de reserva, la misma prueba pasó.

## Verificación

- `npm test`: 24 archivos y **131 pruebas aprobadas**. El proceso terminó con exit 0.
  Vitest imprimió el aviso de JSDOM `Not implemented: Window's scrollTo()` y
  una recomendación de rendimiento para los entornos de prueba; no hubo fallos.
- `npm run test:typecheck`: exit 0.
- Build con el Dockerfile de producción (`node:22-alpine`): TypeScript pasó y Vite
  transformó **70 módulos** en **3:25**. Bundle: JavaScript 374.09 kB (107.71 kB gzip),
  CSS 22.29 kB (4.79 kB gzip). Imagen generada correctamente.
- El intento de build directo en el host permaneció en `transforming…` por más de dos
  minutos y se detuvo; el build oficial en Docker completó correctamente.
- La imagen candidata se sirvió en `127.0.0.1:5174`, aislada del puerto y contenedor
  existentes. HTTP respondió 200; el proxy a la API local respondió correctamente.
- Escritorio y móvil se revisaron visualmente. En móvil: ventana 390 px, contenido
  390 px, formulario a 659 px y galería a 1764 px. El CTA dejó la URL en
  `/#titulo-buscar`, con el título del formulario en la parte superior de la vista.
- La consola muestra dos 401 al consultar `/api/yo` y `/api/admin/sesion` sin sesión;
  son las comprobaciones de sesión esperadas. No hubo otros errores ni advertencias.

## Capturas

- `portada-escritorio.png`
- `portada-movil.png`

## Observación de producción antes del push

`https://hotel.eridu.top/` mostró venta pausada. El hero y el aviso inferior repiten
acciones que llevan a `/mis-reservas`; el código actual de `origin/develop` ya conserva
una sola acción en el hero. Se comprobará el estado del sitio después del push.
