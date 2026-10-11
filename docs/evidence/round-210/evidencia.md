# R210 — copia del código de reserva

## Alcance verificado

El huésped puede copiar el código desde la confirmación. Si el navegador bloquea el portapapeles, la interfaz selecciona el código y explica cómo copiarlo manualmente. La acción alcanza un área táctil mínima de 44 px, mantiene el foco y anuncia el resultado sin moverlo.

## Entorno y datos

- Frontend local en `http://127.0.0.1:4173/reserva`, servido desde el worktree de verificación sobre `develop` en `637c709` más los cambios de esta ronda.
- Página abierta en contexto de navegador aislado, a `1440 × 1000` para escritorio y `390 × 844` para móvil.
- Reserva, correo y respuesta HTTP sintéticos (`H-DEMO-210`, `huesped@example.test`). El POST `/api/reservas` fue respondido con una fixture en el navegador. No se escribió en producción, base de datos ni servicio de correo; el proveedor real no se probó.

## Capturas

- [Confirmación en escritorio](../../../output/playwright/round-210/reserva-confirmada-desktop.png)
- [Confirmación en móvil](../../../output/playwright/round-210/reserva-confirmada-movil.png)
- [Móvil con portapapeles bloqueado y código seleccionado](../../../output/playwright/round-210/reserva-portapapeles-bloqueado-movil.png)

## Comprobaciones observadas

- El botón presenta `min-height: 44px` y una altura renderizada de `44px` tanto a 1440 px como a 390 px.
- En móvil el documento mide 390 px de ancho para una ventana de 390 px: no hay desbordamiento horizontal.
- Con teclado, `Tab` enfoca “Copiar código” y `Enter` ejecuta la acción. La región `role="status"` con `aria-live="polite"` anuncia “Código copiado. Pégalo donde lo necesites.” sin quitar foco.
- Al rechazar `navigator.clipboard.writeText`, el navegador dejó seleccionado exactamente `H-DEMO-210`; la región de estado anunció “No se pudo copiar; el código quedó seleccionado, cópialo con Ctrl+C.”
- No hubo errores de consola en la sesión final del navegador. Las rutas API se simularon; el resultado no demuestra entrega de correo ni persistencia real.

## Pruebas y build

- RED: `CodigoReserva.test.tsx` falló antes del cambio porque faltaba la clase específica del objetivo táctil.
- GREEN: `apps/web/src/componentes/CodigoReserva.test.tsx` y `apps/web/src/paginas/PaginaReserva.test.tsx`: **10/10** pruebas en 2 archivos.
- `npm run test:typecheck`: pasó.
- `npm run build`: pasó; Vite transformó 75 módulos y generó los bundles de producción.
- `git diff --check`: pasó.

La etapa visual de R210 queda cerrada para copiar, alternativa manual, teclado, anuncio accesible y objetivo táctil. La entrega durable del correo continúa abierta en R213.
