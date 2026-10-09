# Ronda 189: edición de lugares turísticos

Fecha: 2026-10-09.

## Resultado

El panel permite abrir cualquier lugar existente en el formulario de alta, editar nombre,
descripción y coordenadas, guardar con el `PUT` existente o cancelar. Al entrar en edición el foco
pasa al nombre; el estado visible/oculto se conserva al guardar. Mientras se edita, las acciones
de la tabla quedan deshabilitadas. Los botones se distribuyen en varias líneas si el ancho móvil
lo requiere.

## Verificación automatizada

- TDD: las dos pruebas nuevas fallaron antes de implementar porque faltaba la acción de edición;
  después pasaron.
- `npm test -- src/paginas/PaginaAdminLugares.test.tsx`: 2 pruebas aprobadas.
- `npm test`: 26 archivos y 142 pruebas aprobados, exit 0. Vitest mostró el aviso preexistente de
  jsdom sobre `window.scrollTo()`; no impidió la ejecución.
- `npm run typecheck`: exit 0.
- `npm run test:typecheck`: exit 0.
- `npm run build`: exit 0; Vite transformó 71 módulos (JS 377.78 kB, CSS 22.76 kB).
- `git diff --check`: exit 0.

## Revisión visual y flujo

Revisada en Chrome con escritorio de 1365 × 900 y móvil de 390 × 844. En ambas capturas se ve el
formulario de edición, los datos cargados y el foco en el campo de nombre. En la revisión de
móvil, ventana, documento y cuerpo midieron 390 px de ancho, sin desbordamiento horizontal. Se
editó un lugar, se guardó, la fila mostró el nombre actualizado y el formulario volvió a “Nuevo
lugar”. No se observaron errores ni avisos de consola después de interceptar las respuestas.

Las APIs de sesión, hotel y lugares se simularon en un contexto aislado de Chrome. El `PUT` y la
respuesta que actualiza la lista fueron simulados; esta revisión demuestra el flujo del cliente,
no una escritura en el backend o en producción.

Capturas conservadas junto a esta verificación:

- `edicion-escritorio.png`: formulario y acciones en escritorio.
- `edicion-movil.png`: formulario y lista apilada en móvil.

## Integración

El commit `3676087` se aplicó sin conflictos en un worktree limpio creado desde `origin/develop`
(`b509328`) y se publicó como `58e061f`. Después del cherry-pick:

- `npm ci`: 150 paquetes añadidos, 0 vulnerabilidades. npm dejó bloqueados los scripts de
  instalación de `@parcel/watcher` y `esbuild`; pruebas y build funcionaron con la instalación
  disponible.
- `npm test`: 26 archivos y 142 pruebas aprobados.
- `npm run typecheck` y `npm run test:typecheck`: exit 0.
- `npm run build`: exit 0; 71 módulos, JS 377.78 kB y CSS 22.76 kB.
- `git diff HEAD^ HEAD --check`: exit 0.
