# Ronda 158 - Muro antipánico en toda la app

## Hallazgo
Sin ruta que falle no hay red: cualquier excepción al renderizar dejaba cabecera
+ pie con el hueco mudo (el mismo síntoma del 404 antes de la R147). React Router
no pone red solo.

## Cambio
- `MuroAntipanico` en `Estado.tsx` (ErrorBoundary): aviso con salidas (inicio)
  en vez del blanco; el error sigue a consola para diagnosticar.
- Envuelve las `Routes` con `key` por ruta: al navegar se resetea y un roto no
  persigue a la siguiente. Cero estilos nuevos.

## TDD rojo-verde
- Hijo que explota + hijo sano: sin el componente, export indefinido; con él,
  2/2 (fallback con enlace + passthrough).

## Verificación (salida real, 2026-10-09)
- `vitest` full en verde; `tsc --noEmit` exit 0. Backend sin cambios.
- Visual: el muro es invisible sano; captura post-deploy de la portada sin
  regresión — ver `home-sana.png`.

## Despliegue
- Merge a `develop`, push y `compose up -d --build` en TopNUC con health OK.
