# R249 — Estado de la ficha del hotel

**Fecha:** 12 de octubre de 2026
**Área:** panel administrativo, `/admin/hotel`
**Datos:** únicamente valores sintéticos `hotel.test`; no se consultó ni modificó producción.

## Cambio

- El panel resume diez campos del hotel como **Informado** o **Pendiente** y muestra cuántos aún no tienen valor.
- El resumen se oculta mientras la configuración carga o si no pudo leerse. No presenta valores vacíos como datos confirmados.
- Cada campo pendiente enfoca su control; la ubicación abre el selector del mapa.
- El estado de ubicación se calcula desde los valores actuales del formulario. Al informar ambas coordenadas, el aviso desaparece y aparece el control para cerrar el selector.
- El texto aclara que el resumen no decide si se puede vender ni valida la exactitud o aprobación del contenido.

## TDD y verificación

- Línea base dirigida: `PaginaAdminHotel.test.tsx`, **8/8**.
- RED: tres pruebas nuevas fallaron antes de implementar el resumen, el foco dirigido y la actualización del GPS; las ocho pruebas anteriores siguieron pasando.
- GREEN: `npm run test -- --no-file-parallelism --reporter=dot src/paginas/PaginaAdminHotel.test.tsx`, **11/11**.
- Suite web: `npm test -- --no-file-parallelism --reporter=dot`, **274/274 en 36 archivos**.
- `npm run test:typecheck`: código 0.
- `npm run build`: código 0; `tsc --noEmit` y Vite transformaron 77 módulos.
- `git diff --check`: código 0.

La suite muestra el `Error: pum` deliberado de la prueba del límite de React y el aviso conocido de `window.scrollTo()` en jsdom; termina con código 0.

## Navegador

La ruta `/admin/hotel` se abrió primero con el código de `develop` y luego con el cambio R249, usando el mismo servidor API local `api-sintetica.mjs`.

- Capturas de comparación, página completa: escritorio **1440×1000** y móvil **390×844**.
- Sin desbordamiento horizontal en los dos tamaños. En móvil, `document.documentElement.scrollWidth` fue 375 px para un viewport de 390 px.
- En la versión R249, “Completar teléfono de contacto” dejó `hotel-contacto_telefono` enfocado.
- Desde el botón de ubicación, mover el punto cambió el estado a **Informado** y mostró “Cerrar el mapa”.
- Consola: dos respuestas 401 esperadas de `/api/yo` por la sesión de huésped anónima sintética; cero advertencias y ninguna excepción JavaScript.
- No se pulsó Guardar ni se enviaron datos a producción.

### Capturas

| Antes (`develop`) | Después (R249) |
|---|---|
| [Escritorio](antes-escritorio.png) | [Escritorio](despues-escritorio.png) |
| [Móvil](antes-movil.png) | [Móvil](despues-movil.png) |

La API sintética reproducible está en [api-sintetica.mjs](api-sintetica.mjs).
