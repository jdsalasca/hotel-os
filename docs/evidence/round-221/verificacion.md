# Ronda 221 — Recuperar el checklist de preparación del hotel

## Cambio

Cuando falla `GET /api/admin/preparacion`, el panel ya no interpreta el error como un checklist vacío. Muestra una alerta accesible que explica que no pudo comprobar los pasos pendientes y ofrece «Reintentar». La respuesta exitosa conserva el comportamiento anterior: muestra lo pendiente y no presenta una lista vacía cuando todo está completo.

## Verificación automatizada

- La nueva prueba se ejecutó primero contra el código anterior y falló: no encontró un elemento `role="alert"` después de simular un fallo de red.
- Después del cambio: `npm test` — 31 archivos, 208 pruebas aprobadas.
- `npm run test:typecheck` — aprobado.
- `npm run build` — aprobado; incluye typecheck de producción y compilación Vite.
- `git diff --check` — aprobado.

## Verificación en navegador

En Chrome se abrió `/admin` con sesión administrativa sintética. La primera respuesta de preparación fue 503 y produjo el aviso visible; al pulsar «Reintentar», la respuesta 200 mostró «Completa el mapa del hotel» dentro de la lista. No se usaron datos ni credenciales reales.

- Escritorio: viewport 1440 × 1000. Captura del estado de error: [panel-error-escritorio.png](panel-error-escritorio.png).
- Móvil: viewport 390 × 844. Captura del estado de error: [panel-error-movil.png](panel-error-movil.png); captura tras recuperar: [panel-recuperado-movil.png](panel-recuperado-movil.png).
- Sin desbordamiento horizontal: en móvil, `scrollWidth` 390 px para un viewport de 390 px; en escritorio, `scrollWidth` 1425 px para un viewport de 1440 px.
- Consola del navegador: sin errores ni advertencias.

## Alcance

Cambio limitado al componente `ListaPreparacion`, su prueba y el registro de esta ronda. No modifica API, persistencia, estilos globales ni el panel fuera del estado de carga fallida.
