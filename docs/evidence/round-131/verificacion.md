# Ronda 131 - El calendario dice vacío en vez de fingir lleno (frontend)

(Nota de numeración: se trabajó como 129 y 130 sin saber que el colega ya había
tomado esos números. Siguiente número libre: 131.)

## Reporte
La portada mostraba octubre entero "Lleno". La R124 (backend) probó con copia de
prod que no hay nada que vender (`rooms=rates=0`) y dejó `GET /api/hotel/venta`.
Faltaba la cara visible: esta ronda la pone.

## Cambio
- `PaginaInicio`: una lectura a `/api/hotel/venta` al abrir. Con
  `a_la_venta: false`, la rejilla se reemplaza por `Vacio` ("Este hotel aún no
  publica habitaciones…"); con `true`, error o tardanza, todo como siempre.
  Cero clases y cero estilos nuevos.

## TDD rojo-verde
- `PaginaInicio.test`: mock de `/api/hotel/venta` con interruptor. Sin el
  cambio: "lo dice en vez de fingir un mes lleno" falla (1069 ms esperando el
  mensaje), "con venta muestra el calendario" y los 3 de detalle pasan. Con el
  cambio: 5/5.

## Verificación (salida real, 2026-10-08)
- Frontend: `vitest` 19 archivos / 64 tests en verde (con `--maxWorkers=2`; el
  pool sin límite mata workers por memoria y miente el conteo).
- `tsc --noEmit` y `tsc -p tsconfig.tests.json`: exit 0.
- Backend sin cambios en la ronda; suite completa 414/414 en el árbol mergeado.
- Visual: captura post-deploy de la portada en prod con el mensaje honesto —
  ver `vacio-desktop.png` (prod sigue sin inventario: el mensaje es el correcto).

## Despliegue
- Merge a `develop`, push y `compose up -d --build` en TopNUC con health OK.
