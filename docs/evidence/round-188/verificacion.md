# Ronda 188: filtro de amenidades en disponibilidad

Fecha: 2026-10-09.

## Resultado

El huésped puede filtrar las ofertas disponibles por amenidades. Al elegir varias, solo se
conservan los tipos que tienen todas las seleccionadas; la página muestra el número de
coincidencias, explica cuando no hay resultados y permite quitar los filtros. Las tarjetas
siguen mostrando sus amenidades. El panel de inventario aclara que la selección aplica a todas
las habitaciones del tipo.

La disponibilidad existente sigue siendo la fuente de ofertas. Esta ronda no modifica backend,
calendario, reservas, precios ni llamadas de escritura.

## Verificación automatizada

- `npm test -- src/paginas/PaginaInicio.test.tsx`: 25 pruebas aprobadas; cubre selección AND,
  conteo, cero coincidencias y limpieza. La prueba nueva falló antes de implementar el filtro.
- `npm test -- src/paginas/PaginaAdminInventario.test.tsx`: 6 pruebas aprobadas; la aserción del
  texto nuevo falló antes de actualizarlo.
- `npm test`: 25 archivos y 140 pruebas aprobados, exit 0.
- `npm run typecheck`: exit 0.
- `npm run test:typecheck`: exit 0.
- `npm run build`: exit 0; Vite transformó 71 módulos y generó el bundle de producción.
- `git diff --check`: exit 0.

## Revisión visual

Revisada en Chrome con viewport de escritorio de 1365 × 900 y móvil de 390 × 844. Las respuestas
de las APIs fueron simuladas en un contexto aislado del navegador; esto valida la presentación y
el comportamiento del cliente, no una conexión o contenido de producción. En móvil, el ancho del
documento y el de la ventana coinciden (390 px); el filtro, las tarjetas y el estado vacío caben
sin desbordamiento. No se observaron errores de consola.

Capturas conservadas junto a esta verificación:

- `filtro-escritorio.jpg`: selección aplicada y conteo.
- `sin-coincidencias-escritorio.jpg`: cero coincidencias y acción para limpiar.
- `filtro-movil.jpg`: resultados filtrados en móvil.
- `sin-coincidencias-movil.jpg`: estado vacío en móvil.
- `panel-amenidades-movil.jpg`: instrucción del alcance por tipo en inventario.

## Integración

La integración por cherry-pick, la validación en un worktree limpio, la publicación a
`origin/develop` y la comprobación del SHA remoto se registran al cerrar la integración.
