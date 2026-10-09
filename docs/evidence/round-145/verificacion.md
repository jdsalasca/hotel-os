# Ronda 145 - Sin venta, la búsqueda se deshabilita con motivo

## Cambio
- `PaginaInicio`: con `a_la_venta: false`, el botón Buscar se deshabilita y
  aparece "Sin habitaciones publicadas: la búsqueda no traerá nada todavía."
  (clase `campo__ayuda` existente). Si la lectura falla, todo sigue abierto
  (fail-open): nunca se bloquea por un error propio.
  Cero clases y cero estilos nuevos.

## TDD rojo-verde
- Mock con interruptores `sinVenta`/`fallaVenta` en `PaginaInicio.test`: sin el
  cambio falla "se deshabilita con motivo"; con él, 9/9 en el archivo. En el
  camino: `toBeDisabled` no existe sin jest-dom — se aserta `disabled` directo.

## Verificación (salida real, 2026-10-09)
- `vitest` full en verde; `tsc --noEmit` exit 0.
- Backend sin cambios en la ronda.
- Visual: captura post-deploy de la portada en prod (botón apagado + motivo) —
  ver `busqueda-apagada.png`.

## Despliegue
- Merge a `develop`, push y `compose up -d --build` en TopNUC con health OK.
