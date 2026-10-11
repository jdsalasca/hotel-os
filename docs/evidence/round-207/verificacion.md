# Ronda 207 - Cada pagina tiene su propio titulo en la pestana

## El hueco
`index.html` traia un `<title>` fijo: `Hotel Eridu · Reserva directa`. En una SPA eso
significa que **todas** las pantallas comparten el nombre. Quien abre `/mis-reservas` en una
pestana nueva, la guarda como favorito o comparte el enlace, se queda sin saber donde esta; y
la ruta inexistente se presenta como si fuera la portada.

Es el P2 "titulo identico en todas las paginas" del plan de auditoria.

## La decision
Un mapa ruta → titulo en `tituloRuta.ts` y un componente minimo que sincroniza
`document.title` en cada cambio de ruta.

- **No** un middleware por pagina: tocar 19 componentes para un `document.title` es mas
  diff que el problema.
- **No** solo al cargar: en una SPA el titulo se queda pegado al navegar, que es
  justamente cuando mas importa. Por eso el efecto depende de la ruta, no del montaje.
- Una ruta nueva que no este en el mapa **hereda el titulo de su seccion** en vez de quedarse
  en blanco o mostrar el de la portada. Ese es el detalle que evita que el mapa vuelva a
  quedar viejo sin que nadie se acuerde.

El nombre del hotel va delante: es lo que identifica la pestana de un vistazo.

## TDD rojo-verde
`TituloRuta.test.tsx`, 5 pruebas:
- `la portada conserva el titulo del hotel` / `mis reservas se anuncia como tal y no como la
  portada` / `una ruta que no existe no se presenta como si fuera la portada` / `la consulta
  de una reserva por codigo tiene su propio titulo`: rojo con `expected '' to contain ...`
  (el documento no tenia titulo en el entorno de pruebas).
- `una ruta nueva que no este en el mapa hereda el titulo de su seccion`: fija el techo para
  que anadir una ruta manana no reintroduzca el problema.

Un rodeo mio: `TITULOS[ruta]` con `Record<string, string>` devuelve `string | undefined` para
el indice, y `noUncheckedIndexedAccess` lo hace visible. Se resolvio leyendo el valor y
comparando, no con un cast.

## Verificacion visual (navegador real)
| Ruta | Titulo en la pestana |
|---|---|
| `/` | `Hotel Eridu · Reserva directa` |
| `/consulta` | `Hotel Eridu · Consulta tu reserva` |
| `/mis-reservas` | `Hotel Eridu · Mis reservas` |
| `/privacidad` | `Hotel Eridu · Privacidad` |
| `/no-existe` | `Hotel Eridu · Página no encontrada` |

Ademas, navegando dentro de la SPA (que antes de era el caso roto):
`"Hotel Eridu · Reserva directa"` → `"Hotel Eridu · Mis reservas"`.

0 errores JS. Capturas: `01-mis-reservas-titulo.png` y `02-mis-reservas-movil.png`.

## Pruebas
- 188/188 frontend (`--no-file-parallelism`), `tsc --noEmit` y `test:typecheck` limpios,
  `vite build` correcto.
- Verificado sobre `a47e599` (R206 ya integrada), sin conflictos.

## Nota de coordinacion
No se toco `docs/auditoria-producto/plan-mejora-integral-2026-10.md`: lo esta actualizando
otro agente con los resultados de R206. `compose.ronda207.yaml` es andamiaje local de puertos
y no va commiteado.
