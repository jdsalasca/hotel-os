# Ronda 198 — Categorías para los lugares turísticos

## Cambio

El panel permite clasificar cada lugar como «Qué comer», «Qué visitar» o «Dónde alojarse».
La portada muestra la categoría y filtra con el mismo selector la lista y los marcadores del
mapa. La migración V21 asigna «Qué visitar» a los lugares que ya existían; la API rechaza
categorías fuera de las tres permitidas. Si una respuesta antigua no incluye categoría, la
interfaz conserva «Qué visitar» como valor predeterminado.

## Verificación automatizada

- TDD: las pruebas nuevas fallaron antes de implementar la categoría en API, panel y portada;
  después pasaron con la implementación.
- API (ejecutada desde `apps/api`): `.\mvnw.cmd test` — **460 pruebas, 0 fallos, 0 errores, 1 omitida; BUILD SUCCESS**. La suite
  ejecutó V21 en bases temporales. La prueba de lugares valida asignación, consulta pública,
  edición, compatibilidad con la categoría predeterminada y rechazo de un valor inválido.
- Frontend: `npm run test -- --reporter=dot` — **27 archivos, 161 pruebas aprobadas**.
- Tipos: `npm run test:typecheck` — **exit 0**.
- Producción: `npm run build` — **Vite completó el build**.
- `git diff --check` — limpio.

## Revisión visual

Se revisó la portada y el panel en Chrome con datos sintéticos. En móvil (390 × 844 px), al
seleccionar «Qué comer», queda un sitio en la lista y el mapa lleva solo el marcador del hotel y
el del café. `scrollWidth` y `clientWidth` son ambos 390 px. La consola no muestra errores.

Capturas:

- Portada de escritorio: [portada-escritorio.png](portada-escritorio.png)
- Panel de lugares en escritorio: [panel-escritorio.png](panel-escritorio.png)
- Portada móvil con «Qué comer» seleccionado: [portada-movil.png](portada-movil.png)

La revisión visual usó respuestas sintéticas del navegador; no modifica ni afirma cambios en los
datos del hotel en producción.
