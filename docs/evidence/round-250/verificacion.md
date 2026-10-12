# Ronda 250 — conservar coordenadas cero del hotel

## Hallazgo

Al abrir el selector del mapa, el código usaba `Number(coordenada) || respaldo`. JavaScript interpreta `0` como falso, así que una ubicación legítima de latitud y longitud cero se reemplazaba por el centro inicial de Boyacá (`5.65, -73.52`).

## Cambio y regresiones

La conversión ahora conserva cualquier número finito, incluido `0`. Solo el valor vacío o no numérico usa el centro de referencia. Se agregaron dos casos al test de `PaginaAdminHotel`: el punto `0,0` debe producir `marker=0%2C0`, y campos vacíos deben conservar el marcador de respaldo `5.65%2C-73.52`.

La primera regresión falló antes de corregir el código porque el mapa apuntaba a `5.65,-73.52`; tras el cambio pasó. Resultado final: prueba dirigida 13/13; suite web 276/276 en 36 archivos; `npm run test:typecheck` y `npm run build` aprobados (77 módulos Vite).

## Revisión visual

Se usó una API local y una ficha de hotel sintética en `/admin/hotel`; nunca se abrió producción ni se guardó el formulario.

| Captura | Viewport configurado | Ancho del documento | Resultado |
|---|---:|---:|---|
| `antes-escritorio.png` | 1440 × 1000 | 1425 px | El valor escrito es `0,0`, pero el iframe muestra el centro equivocado `5.65,-73.52`. |
| `antes-movil.png` | 390 × 844 | 375 px | Reproduce el mismo fallback en el selector móvil. |
| `despues-escritorio.png` | 1440 × 1000 | 1425 px | El iframe tiene `marker=0,0` y el marcador se mantiene en el punto. |
| `despues-movil.png` | 390 × 844 | 375 px | El iframe tiene `marker=0,0`; `scrollWidth` coincide con `clientWidth` (375 px), sin overflow horizontal. |

El ancho menor que el viewport configurado se debe a la barra vertical del navegador. En escritorio también se midió `scrollWidth === clientWidth` (1425 px). El punto `0,0` cae en el océano; la tesela de agua es esperable para esas coordenadas y el marcador verde aparece en el centro. No se pulsa «Usar este punto» ni «Guardar datos del hotel».

## Consola

No hubo advertencias ni excepciones JavaScript. Se observaron dos respuestas 401 de `/api/yo`, esperables porque la fixture no inicia una sesión de huésped; la ficha administrativa sintética sí cargó para poder inspeccionar el mapa.
