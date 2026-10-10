# Ronda 193 - El mapa marca los sitios, no solo el hotel

## El hueco
La sección "Encuéntranos y explora" pintaba **un único marcador**: el del hotel. Los sitios
que el hotel recomienda aparecían solo en la lista de al lado, con su distancia y su
enlace "Cómo llegar". El mapa se titulara `Encuéntranos y explora` y no exploraba nada:
para ver dónde caía la Plaza de Bolívar había que leer el texto, no mirar el mapa.

El backend ya entregaba las coordenadas de cada sitio (`GET /api/lugares`) y OpenStreetMap
acepta varios marcadores separados por `;`. Solo faltaba unirlos.

## El cambio
- `PaginaInicio.tsx`: helper `marcadoresDelMapa(hotel, lugares)` que devuelve los puntos
  `lat,lng` separados por `;`, con el hotel primero. El `iframe` pasa a usar `marker=` con
  esa lista en vez del punto suelto del hotel.
- Un sitio que cae **exactamente** en el punto del hotel no se repite: dos marcadores
  idénticos se ven como uno y solo confunden.
- Sin estilos nuevos y sin tocar la lista, que sigue siendo la vía para leer la distancia
  y abrir la ruta real.

## TDD rojo-verde
- `el mapa marca el hotel y los sitios, no solo el hotel`: en rojo
  `expected 'https://www.openstreetmap.org/export/…' to contain 'marker=4%2C-74'`;
  en verde pide `marker=4%2C-74%3B4.01%2C-74.02` (hotel `;` sitio).
- `el mapa no duplica el marcador si un sitio cae en el hotel`: cuenta los `marker=` del
  `src` y exige **1** cuando el sitio coincide con el hotel. Verde en ambos.
- Archivo: 27/27. Frontend completo: **147/147**, `tsc --noEmit` limpio.

## Verificación real (contenedores, no mocks)
- Hotel en Villa de Leyva (5.6672, -74.0477) y dos sitios creados por la API.
- `GET /api/lugares` devuelve `Casona del Río` a 284 m y `Plaza de Bolívar` a 321 m.
- Playwright: **3 marcadores** en la URL del iframe
  (`5.6672,-74.0477`, `5.665,-74.049`, `5.67,-74.047`), `erroresJS=0`,
  sin desborde horizontal en 1440 y en 390 px.
- Capturas: `01-mapa-escritorio.png`, `02-mapa-movil.png`, salida en `marcadores.txt`.

## Limitación honesta (medida, no supuesta)
El `bbox` sigue siendo fijo (±0.03° alrededor del hotel, ≈6 km de lado). Con sitios a
300 m, los tres marcadores **se solapan visualmente**: en `01-mapa-escritorio.png` se ve un
solo pin. Los sitios siguen siendo alcanzables por la lista (distancia + "Cómo llegar"),
pero el mapa no los separa todavía. Ajustar el encuadre al conjunto de puntos es la
mejora siguiente; no se hizo aquí porque con un solo sitio el problema no se aprecia y
encoger el bbox sin un mínimo de escala hace el mapa inútil a zoom bajo.

## Nota de coordinación
- No se tocó `PaginaAdminLugares.tsx` ni `LugaresController.java` (R189 del colega).
- `npm run test:typecheck` falla por **dos errores preexistentes** en
  `PaginaAdminLugares.test.tsx` (propiedad `metros` fuera del tipo, líneas 95-96). Se
  comprobó con `git stash` que fallan igual en `origin/develop` limpio: son del colega y
  no se tocaron.