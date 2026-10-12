# R241 — Tabla de reservas del panel: plan de implementación

> **Para agentes de ejecución:** seguir el ciclo TDD de este plan, conservar las reglas existentes y completar cada casilla con evidencia.

**Meta:** separar la tabla y las acciones por fila de `PaginaAdminReservas.tsx` en un componente comprobable sin cambios de comportamiento.

**Arquitectura:** `TablaReservas` será una vista controlada: recibe reservas, estado de impresión, conteos de mensajes y callbacks del padre. La página conserva las consultas y mutaciones; el componente conserva la presentación y sigue mostrando únicamente las transiciones que provee el servidor.

**Stack:** React 19, TypeScript 5.9, Vitest, Testing Library, Vite; sin dependencias nuevas y sin cambios de SCSS.

**Especificación:** [2026-10-12-r241-tabla-reservas.md](../specs/2026-10-12-r241-tabla-reservas.md)

## Restricciones

- No modificar API, persistencia, permisos, filtros ni reglas de dominio.
- Mantener `siguientes` como única fuente de acciones de transición.
- Escribir y ejecutar una prueba que falle antes de implementar el componente.
- Mantener estilos en SCSS; esta ronda no necesita estilos nuevos.
- Verificar pruebas, tipos, build y captura visual en escritorio y móvil.
- Commits nuevos con `JUAN DAVID SALAS CAMARGO <31742974+jdsalasca@users.noreply.github.com>`; integrar en `develop`, publicar en `origin/develop` y comprobar autoría GitHub.

## Archivos

- Crear `apps/web/src/paginas/TablaReservas.tsx`: contrato de fila y componente accesible de tabla.
- Crear `apps/web/src/paginas/TablaReservas.test.tsx`: pruebas aisladas de acciones autorizadas, fila cerrada, conteo de mensajes, callbacks y clase de impresión.
- Modificar `apps/web/src/paginas/PaginaAdminReservas.tsx`: delegar la lista al componente y conservar filtros, carga, acciones HTTP y detalle.
- Actualizar `docs/plan.md`: anotar R241 activa y luego su evidencia/cierre.
- Crear `docs/evidence/round-241/verificacion.md`: resultados de pruebas, medidas y revisión visual.

## Riesgos que debe fijar la cobertura

1. Una reserva sin transiciones recibidas no debe ofrecer botones locales: prueba `muestra «Sin acciones» cuando el servidor no permite transiciones`.
2. Con una transición permitida, solo se renderiza la acción recibida y transmite código/estado correctos: prueba `muestra y delega únicamente las transiciones autorizadas`.
3. Un conteo positivo tiene texto y nombre accesible ligados a la reserva; cero oculta la insignia: prueba `expone mensajes sin leer y abre la reserva correspondiente`.
4. Un huésped sin nombre conserva el correo como respaldo y la columna mantiene sus etiquetas semánticas: prueba `conserva datos y semántica de la fila`.
5. Al abrir detalle, solo cambia la clase de impresión de la envoltura: prueba `marca la tabla como no imprimible cuando el detalle está abierto`.

---

### Tarea 1: fijar el contrato visible de la tabla

**Archivos:** crear `apps/web/src/paginas/TablaReservas.test.tsx`; por ahora no crear implementación.

- [x] Escribir las cinco pruebas enumeradas arriba usando `render`, `screen` y callbacks espía; usar una reserva sintética con `siguientes` controlable.
- [x] Ejecutar `npm run test -- src/paginas/TablaReservas.test.tsx` desde `apps/web` y confirmar que falla por faltar `TablaReservas`, no por un error de la prueba.

### Tarea 2: implementar la tabla controlada

**Archivos:** crear `apps/web/src/paginas/TablaReservas.tsx`.

**Interfaz:** `TablaReservas({ reservas, detalleAbierto, mensajesNuevos, onCambiar, onVerDetalle })`; `reservas` y `mensajesNuevos` son datos de solo lectura, y callbacks reciben el código de reserva y, para cambio, el estado autorizado.

- [x] Exportar los tipos de estado/reserva que todavía usa la página y la etiqueta de estado necesaria para el historial del detalle; no duplicar el contrato.
- [x] Mover al componente el markup de la tabla, el catálogo de acciones con sus textos y el tono visual; filtrar acciones por `siguientes` como hoy.
- [x] Ejecutar la prueba aislada y confirmar las cinco expectativas.

### Tarea 3: conectar la página existente

**Archivos:** modificar `apps/web/src/paginas/PaginaAdminReservas.tsx`.

- [x] Reemplazar solamente el markup de tabla por `<TablaReservas ... />`; conectar `cambiar` y `abrir` existentes y pasar reservas, detalle y mensajes.
- [x] Quitar de la página helpers/imports que pasan a la tabla; mantener `TEXTO_ESTADO` y tipos requeridos por el historial sin crear dependencias circulares.
- [x] Ejecutar `npm run test -- src/paginas/PaginaAdminReservas.test.tsx` y comprobar los casos existentes de mensajes, estados, callbacks y detalle.

### Tarea 4: verificar, medir y registrar evidencia

- [x] Ejecutar `npm run test`, `npm run typecheck`, `npm run test:typecheck` y `npm run build` en `apps/web`.
- [x] Comparar el tamaño de `PaginaAdminReservas.tsx` con la línea base de 756 líneas físicas (719 con contenido); registrar también el tamaño nuevo del componente y explicar el desplazamiento, sin presentar una reducción neta del repositorio como beneficio.
- [x] Ejecutar la aplicación local con respuestas sintéticas y revisar/capturar escritorio 1440×1000 y móvil 390×844. Confirmar tabla legible, no desbordamiento horizontal y consola limpia.
- [x] Completar `docs/evidence/round-241/verificacion.md` y actualizar `docs/plan.md` con la verificación real, dejando integración/publicación pendientes.
- [ ] Revisar el diff, crear un commit pequeño, cherry-pick a `develop`, repetir verificación, push a `origin/develop`, comprobar que GitHub atribuye el commit a `jdsalasca` y limpiar únicamente el worktree/branch R241.
