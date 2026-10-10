# Ronda 179 - El panel del hotelero ve la distancia de cada sitio

## El hueco
R177 dio la distancia al mapa público y R178 al endpoint del panel, pero el
**panel no la mostraba**: el hotelero seguía viendo solo `5.635, -73.525`. La web
pública sí la enseñaba (otro agente lo hizo), así que el dato existía en un lado
y se desperdiciaba en el otro. Sin esto, nadie puede verificar que un sitio
"queda cerquita" antes de publicarlo.

## Cambio
- `Lugar` acepta `metros?: number` y la tabla del panel añade una columna
  **Distancia**: metros exactos bajo el kilómetro, kilómetros con un decimal por
  encima ("240 m", "3.450 km"). Sin `metros` (hotel sin ubicar) dice
  "Ubica el hotel" en vez de un guion mudo.
- Sin estilos nuevos: reusa `.cifra` y `.campo__ayuda` que ya existen.

## TDD rojo-verde
- `"el panel dice a qué distancia queda cada sitio del hotel"`: **RED** sin el
  cambio (`Unable to find an element with the text: 240 m`), **GREEN** con él.
  Comprueba los dos formatos, no solo que exista el texto.
- `PaginaAdminLugares.test.tsx`: 3/3.
- Suite frontend completa: **27 archivos / 145 tests**, `Type Errors: no errors`.

## Nota de entorno
Hecha en worktree limpio. `origin/develop` traía R190/R191 al integrarse; la
distancia no toca APIs ni rutas de reservas.