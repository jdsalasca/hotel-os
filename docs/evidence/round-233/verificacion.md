# Ronda 233 — Recuperación después de un 404 en la consulta

**Resultado:** aprobado en el worktree `fix/r233-consulta-no-encontrada`, sobre la base `e406f51`.

## Cambio comprobado

- La consulta mantiene el mensaje genérico: «No encontramos una reserva con ese código y ese correo»; no revela cuál dato coincide.
- El 404 añade una indicación para revisar y corregir ambos campos, enlazada a ellos con `aria-describedby`.
- Código y correo permanecen editables y se conservan después del error para permitir reintentar.
- Solo cuando hay una sesión de huésped se muestra el enlace secundario «Ver mis reservas de esta cuenta», que lleva a `/mis-reservas`.
- Las pruebas cubren la respuesta anónima sin enlace, la respuesta con sesión y los valores retenidos. No se modificaron API ni base de datos.

## Pruebas y compilación

- Prueba dirigida del nuevo comportamiento: **2/2 aprobadas**; antes de implementar, las dos regresiones fallaron por las salidas y el texto aún ausentes.
- Suite frontend: **233/233 aprobadas**, en 32 archivos.
- `npm run typecheck`: aprobado.
- `npm run test:typecheck`: aprobado.
- `npm run build`: aprobado; Vite generó `dist` para producción.
- `git diff --check`: aprobado.

En una ejecución inicial de la suite completa falló una prueba existente del calendario de inventario. Esa prueba pasó aislada y la repetición completa terminó 233/233; no se cambió el calendario ni su prueba en esta ronda.

## Verificación visual

Se usó Chromium headless aislado contra la previsualización local del build. Las respuestas de API fueron sintéticas: sesión de huésped de prueba, sesión administrativa no autenticada, hotel de prueba, contador de mensajes en cero y un 404 deliberado para `POST /api/reservas/consulta`. No se consultó producción ni se usaron datos reales.

| Vista | Resultado |
|---|---|
| Escritorio, 1440 × 1000 | HTTP 200 para la aplicación; guía visible; enlace a `/mis-reservas`; campos retenidos; ancho del documento 1440 px. |
| Móvil, 390 × 844 | HTTP 200 para la aplicación; guía visible; enlace a `/mis-reservas`; campos retenidos; ancho del documento 390 px. |

En ambas vistas no hubo excepciones JavaScript ni desbordamiento horizontal. La consola registró únicamente los estados HTTP esperados de la simulación: 401 al consultar la sesión administrativa y 404 al probar la reserva inexistente. El encabezado recibió también su respuesta sintética tipada; ninguna ruta de API quedó sin simular.

### Capturas

- [Escritorio, 1440 × 1000](escritorio.png)
- [Móvil, 390 × 844](movil.png)
