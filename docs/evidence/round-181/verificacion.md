# Ronda 181: lectura de fechas e importes en Mis reservas

## Cambio

- La fecha deja el ISO crudo y usa la fecha corta localizada (`10 jun 2030`). El mes y el
  año se mantienen juntos en escritorio; en móvil se conserva el diseño de tarjeta.
- Los importes reutilizan el formateador compartido del sitio, que muestra el código de
  moneda para evitar símbolos ambiguos y conserva la precisión indicada para cada moneda.
- El formato local de moneda duplicado desaparece de `PaginaMisReservas`.

## Verificación automatizada

- RED observado: el test de `fechaCorta` esperaba `10 jun 2030` y recibió `10 de jun de
  2030`; las pruebas del componente fallaron con la fecha ISO original.
- `npm test`: 24 archivos, 126 pruebas aprobadas.
- `npm run test:typecheck`: exit 0.
- `npm run build`: 70 módulos transformados; Vite produjo `dist/index.html` (1.77 kB),
  CSS de 22.29 kB y JavaScript de 373.65 kB (107.55 kB gzip); build completado en 2 min 4 s.

## Revisión visual

Chrome local contra Vite, con una reserva sintética `COP` y sesiones simuladas; no se
consultó el backend. Escritorio 1440×1000 y móvil 390×844. En ambos tamaños se ven
`10 jun 2030`, `12 jun 2030` y `COP 485.000`; `documentWidth` coincide con el ancho del
viewport y la consola no muestra errores de JavaScript.

- `desktop.png`
- `mobile.png`
