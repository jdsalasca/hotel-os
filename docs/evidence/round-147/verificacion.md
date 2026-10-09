# Ronda 147 - Ruta falsa con salidas, no un hueco mudo

## Hallazgo en prod
`/ruta-que-no-existe-xyz`: cabecera + pie con NADA entre ellos (verificado con
navegador). Enlace roto, marcador viejo o dedo mal puesto = callejón sin salida.

## Cambio
- `PaginaNoEncontrada` + `path="*"` al final del router: `Vacio` ("Esa página no
  existe") con botones a inicio y consulta. Clases existentes, cero estilos.

## TDD rojo-verde
- Test a nivel App (ruta falsa real): sin la ruta, "Unable to find"; con ella,
  1/1 con los dos enlaces.

## Verificación (salida real, 2026-10-09)
- `vitest` full en verde; `tsc --noEmit` exit 0. Backend sin cambios.
- Visual: captura post-deploy de la ruta falsa en prod — ver `no-encontrada.png`.

## Despliegue
- Merge a `develop`, push y `compose up -d --build` en TopNUC con health OK.
