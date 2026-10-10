# Ronda 199 — Selección de bloqueos desde el calendario

## Cambio

Las noches libres del calendario del panel se pueden seleccionar en dos pasos con ratón,
teclado o pantalla táctil; con ratón también se puede arrastrar un intervalo. La selección
solo rellena el formulario existente. Muestra el número de noches y la fecha de salida
(excluida), y no guarda hasta que el administrador confirma. No permite atravesar noches
ocupadas, bloqueadas o sin datos; anuncia el conflicto. Si el servidor rechaza una selección
por un cambio concurrente, la API responde HTTP 409 y el formulario conserva las fechas.

El API revalida, dentro de la transacción, los bloqueos por habitación y los bloqueos de todo
el hotel contra reservas vigentes y otros bloqueos. Los intervalos adyacentes siguen
permitidos.

## Verificación automatizada

- TDD: las pruebas nuevas fallaron antes del cambio porque las celdas libres no eran controles
  seleccionables y el servicio insertaba bloqueos sin revalidar conflictos. La revisión de rama
  añadió dos regresiones que también fallaron antes de corregirse: fechas manuales obsoletas al
  iniciar selección y un ancla que no se reiniciaba después de un conflicto.
- Backend, suite completa desde `apps/api`: `mvnw.cmd test` — **465 pruebas, 0 fallos, 0 errores,
  1 omitida; BUILD SUCCESS**.
- Backend, casos específicos finales: `mvnw.cmd -Dtest=InventarioServiceTest,BloqueosControllerTest test`
  — **58 pruebas, 0 fallos, 0 errores, 0 omitidas; BUILD SUCCESS**.
- Frontend: `npm run test -- --reporter=dot` — **27 archivos, 169 pruebas aprobadas**.
- Tipos: `npm run test:typecheck` — **exit 0**.
- Build: `npm run build` — **Vite completó el build**, incluyendo comprobación de tipos.
- `git diff --check` — limpio.

## Revisión visual

Chrome local con datos sintéticos: el intervalo del 1 al 3 de octubre completa la salida del
4 de octubre y muestra tres noches sin enviar el formulario. En móvil (390 px), el documento
y el `body` mantienen `scrollWidth = clientWidth = 390 px`; el calendario conserva su propio
desplazamiento horizontal (`clientWidth = 356 px`, `scrollWidth = 1393 px`). Sin errores de
consola.

Capturas:

- [Calendario de escritorio](escritorio.png)
- [Calendario móvil](movil.png)

La verificación visual usa respuestas sintéticas en un servidor local. No modifica ni afirma
cambios en los datos del hotel en producción.
