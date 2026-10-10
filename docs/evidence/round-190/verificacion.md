# Ronda 190 — Opciones de consulta de reservas

## Alcance

La pantalla `/mis-reservas`, cuando el huésped no ha iniciado sesión, presenta dos opciones
separadas: consultar con una cuenta de Google o buscar una reserva con su código y correo. La
autenticación Google y la ruta `/consulta` se mantienen.

## Evidencia

- Prueba nueva: primero falló porque la pantalla no distinguía las dos opciones; pasó tras el
  cambio. Las pruebas del menú también verifican el nombre final «Consultar una reserva».
- `npm test`: **27 archivos, 143 pruebas aprobadas**.
- `npm run test:typecheck`: aprobado.
- `npm run build`: aprobado; Vite transformó 71 módulos. Bundle: CSS **23,38 kB**, JS **378,34 kB**.
- Chrome, sesión sin autenticar, escritorio **1365 × 900**: dos tarjetas en una fila, CTA alineados.
- Chrome, sesión sin autenticar, móvil **390 × 844**: tarjetas apiladas; `scrollWidth` y ancho de
  viewport coinciden (**390 px**), sin desbordamiento horizontal.
- En local, el navegador recibió 401 en `/api/yo` y `/api/admin/sesion`, las comprobaciones
  esperadas al no haber sesión; `/api/hotel` respondió 200. No se inició sesión ni se llamó a OAuth.

Capturas revisadas:

- `escritorio.jpg`
- `movil.jpg`
