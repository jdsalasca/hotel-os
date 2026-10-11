# Ronda 218 — Recuperación de la guía local

## Problema observado

La portada consultaba `/api/lugares`, pero, cuando la solicitud fallaba, el `catch` guardaba `null`. La vista interpretaba ese `null` igual que una respuesta válida sin datos y ocultaba por completo el mapa y su sección, sin explicar qué ocurrió ni ofrecer una forma de reintentar.

## Cambio entregado

- La pantalla diferencia un fallo de red de una respuesta válida sin ubicación o sin lugares.
- Ante un fallo, conserva el título «Encuéntranos y explora» y presenta un aviso accesible con el estado y una acción «Reintentar».
- Durante el reintento, el botón queda deshabilitado y anuncia «Reintentando…».
- Las respuestas de solicitudes anteriores se descartan al desmontar la portada o iniciar otro intento.
- No cambia el API ni la base de datos; la respuesta exitosa sin datos conserva el comportamiento existente.

## Pruebas y compilación

La regresión se añadió antes de corregir el comportamiento: el primer recorrido del test terminó con el fallo esperado porque no encontraba el rol `alert`. Tras implementar el aviso y el reintento:

- Suite completa: **31 archivos y 204 pruebas aprobados** (`npm test -- --reporter=dot`, código de salida 0).
- Tipos de las pruebas (`npm run test:typecheck`): código de salida 0.
- Tipos de producción y build (`npm run build`): código de salida 0; Vite transformó 75 módulos.
- `git diff --check`: código de salida 0.
- El log de la suite contiene el mensaje de error controlado `pum` de la prueba ya existente del límite de errores; las 204 pruebas terminaron aprobadas.

## Revisión visual

La vista de producción se abrió en Chrome usando respuestas locales sintéticas: el primer `/api/lugares` falla; al pulsar «Reintentar», la segunda petición devuelve un lugar de demostración. No se alteró ningún servicio ni dato real.

- Escritorio, 1440 × 1000: aviso y acción visibles dentro de la sección de guía.
- Móvil, 390 × 844: aviso y acción visibles, ancho de documento 390 px.
- Recuperación móvil, 390 × 844: mapa y «Café de la plaza» visibles después del segundo intento; ancho de documento 390 px.
- Consola del navegador: cero errores o advertencias durante la revisión visual.

Capturas: `guia-error-escritorio.png`, `guia-error-movil.png` y `guia-recuperada-movil.png`.

## Alcance pendiente

Esta ronda recupera los errores de la consulta pública de la guía. No cambia el resultado exitoso que indica que el hotel aún no está ubicado, ni agrega un mapa vacío cuando la API responde correctamente sin hotel ubicado y sin lugares.
