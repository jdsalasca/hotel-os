# Ronda 196 - Los sitios se listan de mas cerca a mas lejos

## El hueco
La lista de sitios de la portada salia `ORDER BY nombre`: alfabetica. Y el panel ya recibia
la distancia a cada sitio desde la API (rondas 177-178), pero **la portada la ignoraba y
volvia a medir en el navegador** con su propia copia del haversine.

Las dos cosas juntas son un solo defecto de producto: quien llegaba a "Encuéntranos y explora"
veía primero el sitio que empieza por A aunque estuviera a 2 km, y el de 300 m al final. La
sección promete cercanía y entregaba un índice alfabético.

## Las opciones que seConsideraron
- **A. Ordenar por cercanía en el servidor y que la pantalla use ese mismo número.** El
  backend ya calcula `metros`; faltaba ordenar y borrar la copia del cliente.
- **B. Solo ordenar.** Diff más pequeño, pero deja dos fórmulas de distancia que pueden
  divergir: es el tipo de duplicación que un día muestra "a 33 m" y ordena de otra forma.
- **C. Categorías y filtros de sitios.** Más alcance, y necesita un modelo de curaduría que
  no cabe en una ronda corta.

Se tomó **A**: cierra la curaduría del mapa (la misión que el plan tenía abierta desde R189)
y de paso elimina código duplicado.

## TDD rojo-verde
Backend, `LugaresTest`:
- `los sitios salen de mas cerca a mas lejos, no por nombre`: nombres puestos al revés de la
  cercanía ("C al lado" a 300 m, "B 900 m", "A 2 km"). Rojo:
  `expected:<C al lado> but was:<A 2 km>` — el más lejano primero. Verde tras
  `porCercania(...)`.
- `sin hotel ubicado el orden alfabetico se mantiene`: sin punto del hotel no hay `metros`
  con qué ordenar, así que se respeta el nombre. Fija ese techo para que nadie lo lea como
  "siempre ordenado por distancia".

Frontend, `PaginaInicio.test.tsx`:
- `muestra la distancia que calculó el servidor, no la que rehace el navegador`: fixture con
  coordenadas que darían 111 m y `metros: 40`. Rojo: `Unable to find an element with the
  text: a 40 m` — la pantalla mostraba su propio 111 m. Verde.
- `omite la distancia si el servidor no la manda`: sin `metros` no se inventa un número al
  lado del sitio (conserva la regla que el backend ya aplicaba).
- Los tres tests previos de formato se actualizaron al contrato real del servidor: pasaban
  coordenadas y dependían del recálculo, no de lo que la API manda.

## Verificacion real (contenedores, no solo unit tests)
Hotel ubicado en Villa de Leyva (5.6672, -74.0477) y tres sitios con nombres en orden
alfabetico **inverso** al de cercania:

| En pantalla | Distancia | Posicion alfabetica |
|---|---|---|
| Z Calonge | 33 m | 3o |
| M a 900 m | 912 m | 2o |
| A a 2 km | 2 km | 1o |

Respuesta cruda de `GET /api/lugares` en el orden nuevo, igual que la tabla. Capturas en
`01-lista-ordenada.png` (1440 px) y `02-lista-movil.png` (390 px): mismo orden en ambas, sin
desborde horizontal.

**Los 2 errores JS que reporta el guion son 401 de `/api/yo`**, el sondeo de sesión de
visitante anónimo: `useSesionHuesped.ts` hace `if (!r.ok) return null`. Es el comportamiento
esperado, no un defecto de esta ronda; se documenta en vez de borrarse del informe.

## Pruebas
- Backend: 458 pruebas, 0 fallos, 0 errores, 1 omitido.
- Frontend: 158 pruebas, `tsc --noEmit` limpio, `test:typecheck` limpio, `vite build` correcto.

## Nota de coordinacion
Esta ronda se integro sobre `develop` despues de que la R195 del panel quedara integrada
(`4d340cd`), via rebase: 0 conflictos. No toca `PaginaMisReservas` ni `_componentes.scss`.
`compose.ronda195.yaml` es andamiaje local de puertos y no va commiteado.