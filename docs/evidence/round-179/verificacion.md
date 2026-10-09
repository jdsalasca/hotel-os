# Ronda 179 — Cierre accesible del menú móvil

## Cambio

- La tecla Escape cierra la navegación compacta cuando el foco está en el encabezado.
- El foco regresa al botón del menú después del cierre; `aria-expanded` vuelve a `false`.
- No se modificaron estilos ni endpoints.

## TDD y verificación

- RED: la prueba nueva falló porque, tras Escape, el botón seguía llamándose «Cerrar menú principal»; no aparecía el botón con nombre «Abrir menú principal».
- GREEN: `npm test -- src/App.test.tsx --reporter=dot` — 8/8.
- `npm test` — 24 archivos, 124/124.
- `npm run test:typecheck` — exit 0.
- `npm run build` — exit 0; 70 módulos transformados. JS 373,49 kB (107,44 kB gzip), CSS 22,25 kB (4,78 kB gzip).

## Revisión visual y de teclado

- Chrome local con viewport emulado de 390 × 844 px; ancho del documento: 390 px.
- Se abrió el menú, se avanzó con Tab al primer enlace y se pulsó Escape.
- El menú desapareció, el botón volvió a «Abrir menú principal», conservó el foco y anunció `aria-expanded="false"`.
- La captura de la vista cerrada se inspeccionó visualmente: cabecera y portada caben en el ancho móvil, sin desbordamiento horizontal.
- La consola mostró dos respuestas 401 en las comprobaciones de sesión de la portada anónima (`/api/yo` y `/api/admin/sesion`); las demás solicitudes visibles respondieron 200.
