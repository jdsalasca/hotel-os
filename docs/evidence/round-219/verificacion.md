# Ronda 219 - la dirección se ve donde el huésped busca

## Corrección a mi propia premisa

Partí de que `direccion` "solo se usaba en el panel y no salía en ningún sitio público".
**Era falso, y a medias**: `App.tsx:152` ya la mete en el contacto del pie de todas las
páginas, desde la ronda 14 (identidad operativa administrable y visible).

Lo que sí era cierto: esa línea del pie es texto tenue, dentro de una tira de contacto que
además lleva el teléfono y el correo. La dirección estaba ahí, pero **no donde el huésped mira
cuando quiere saber dónde está el hotel**.

## El hueco real

El huésped que pregunta "dónde es esto" mira la sección "Encuéntranos y explora". Ahí había un
pin y ningún texto. La dirección solo aparecía si bajaba hasta el pie.

Y hay un caso peor: si el hotel **no ha puesto su pin**, la sección entera no se pintaba
(`mapa.hotel.ubicado || mapa.lugares.length > 0`). Con dirección escrita a mano y sin
coordenadas, el huésped no veía nada de nada.

## Lo que se hizo

1. La dirección se muestra sobre el mapa, con el tamaño del resto de la sección.
2. La sección se abre **también** si solo hay dirección, aunque no haya pin ni lugares.
3. En blanco o ausente, no se pinta nada: ni "Dirección:" vacío ni dato inventado.

## Duplicación, dicha claro

Ahora la dirección aparece **dos veces** en la misma pantalla: en la sección y en el pie.

Se mantiene a propósito. El pie es una tira de contacto que está en todas las páginas y lleva
teléfono y correo; la sección es donde el huésped busca la ubicación. Quitarla del pie habría
requerido tocar `App.tsx`, que es de otra ronda, y la habría borrado de las demás páginas. Si
se quiere evitar el duplicado, la decisión es de diseño y toca ese archivo.

## Verificación

Capturas contra los contenedores reales, con dirección configurada y **sin pin** (que es el
caso que antes no mostraba nada):

- Sección presente: sí.
- Bloque de dirección: sí, con el texto `Dirección: Calle 12 # 3-45, Villa de Leyva, Boyaca`.
- Sin desborde horizontal ni en 1440 px ni en 390 px.
- 2 errores JS: los 401 de `/api/yo`, el sondeo de sesión que `useSesionHuesped` maneja.

Capturas: `01-direccion-escritorio.png`, `02-direccion-movil.png`,
`03-seccion-completa.png` (se ve la sección con dirección y sin mapa).

## Pruebas

222 frontend (`--no-file-parallelism`), `tsc --noEmit` y `test:typecheck` limpios,
`vite build` correcto. 5 pruebas nuevas, todas con rojo previo confirmado.
