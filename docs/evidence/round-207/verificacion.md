# Ronda 207 - Cada pagina tiene su propio titulo en la pestana

## El hueco
`index.html` traia un `<title>` fijo: `Hotel Eridu Â· Reserva directa`. En una SPA eso
significa que **todas** las pantallas comparten el nombre. Quien abre `/mis-reservas` en una
pestana nueva, la guarda como favorito o comparte el enlace, se queda sin saber donde esta; y
la ruta inexistente se presenta como si fuera la portada.

Es el P2 "titulo identico en todas las paginas" del plan de auditoria.

## La decision
Un mapa ruta â†’ titulo en `tituloRuta.ts` y un componente minimo que sincroniza
`document.title` en cada cambio de ruta.

- **No** un middleware por pagina: tocar 19 componentes para un `document.title` es mas
  diff que el problema.
- **No** solo al cargar: en una SPA el titulo se queda pegado al navegar, que es
  justamente cuando mas importa. Por eso el efecto depende de la ruta, no del montaje.
- **No** herencia por prefijo: se probÃ³ y se descartÃ³. Las rutas son exactas y el wildcard
  atiende el resto, asÃ­ que heredar harÃ­a pasar por pÃ¡ginas del panel a rutas que hoy son 404
  (`/admin/hotelaria` se hacÃ­a pasar por Hotel). La regla es exacta: esta en el mapa, o es
  "no encontrada".

El nombre del hotel va delante: es lo que identifica la pestana de un vistazo.

## TDD rojo-verde
`TituloRuta.test.tsx`, 7 pruebas:
- `la portada conserva el titulo del hotel` / `mis reservas se anuncia como tal y no como la
  portada` / `una ruta que no existe no se presenta como si fuera la portada` / `la consulta
  de una reserva por codigo tiene su propio titulo`: rojo con `expected '' to contain ...`
  (el documento no tenia titulo en el entorno de pruebas).
- `cualquier ruta desconocida dice que no existe, sin heredarse de otra` y `no se confunde una
  hoja con una ruta distinta que empieza igual`: rojo con `expected 'Hotel Eridu Â· Hotel'` en
  `/admin/hotelaria`, que es exactamente el defecto que motivo la correccion posterior.

Un rodeo mio: `TITULOS[ruta]` con `Record<string, string>` devuelve `string | undefined` para
el indice, y `noUncheckedIndexedAccess` lo hace visible. Se resolvio leyendo el valor y
comparando, no con un cast.

## Verificacion visual (navegador real)
| Ruta | Titulo en la pestana |
|---|---|
| `/` | `Hotel Eridu Â· Reserva directa` |
| `/consulta` | `Hotel Eridu Â· Consulta tu reserva` |
| `/mis-reservas` | `Hotel Eridu Â· Mis reservas` |
| `/privacidad` | `Hotel Eridu Â· Privacidad` |
| `/no-existe` | `Hotel Eridu Â· PÃ¡gina no encontrada` |
| `/admin/hotelaria` | `Hotel Eridu Â· Panel` |
| `/consultaXYZ` | `Hotel Eridu Â· PÃ¡gina no encontrada` |

Ademas, navegando dentro de la SPA (que antes de era el caso roto):
`"Hotel Eridu Â· Reserva directa"` â†’ `"Hotel Eridu Â· Mis reservas"`.

0 errores JS. Capturas: `01-mis-reservas-titulo.png` y `02-mis-reservas-movil.png`.

## Correccion posterior: el titulo ya no se hereda por prefijo

La primera version (commit `8ac2df0`) heredaba el titulo con `ruta.startsWith(clave)`. Eso
hacia que rutas que **hoy son 404** se presentaran como paginas reales:

| Ruta | Con `startsWith` a secas | Ahora |
|---|---|---|
| `/admin/hotelaria` | `Hotel Eridu Â· Hotel` | `Hotel Eridu Â· PÃ¡gina no encontrada` |
| `/admin/promociones` | `Hotel Eridu Â· Panel` | `Hotel Eridu Â· PÃ¡gina no encontrada` |
| `/admin/inventario/1` | `Hotel Eridu Â· Inventario` | `Hotel Eridu Â· PÃ¡gina no encontrada` |
| `/consultaXYZ` | `Hotel Eridu Â· Consulta tu reserva` | `Hotel Eridu Â· PÃ¡gina no encontrada` |

Un intento intermedio limito la herencia al segmento (`clave + '/'`), pero `/admin/hotelaria`
seguia heredando "Panel" y `/admin/inventario/1` "Inventario". Tampoco era correcto: las rutas
de esta SPA son exactas y el wildcard atiende todo lo demas.

**Semantica final: solo coincidencia exacta.** Si la ruta esta en el mapa, recibe su titulo;
cualquier otra â€”incluidas las que empiezan como una existenteâ€” es una pagina que no existe y
se anuncia como tal. No hay herencia en ningun sentido.

Comprobado en el navegador tras el cambio: las 8 rutas medidas dan el titulo correcto, la
navegacion dentro de la SPA mueve el titulo, y 0 errores JS.

## Alcance: esto NO arregla el 200 del fallback

R207 corrige **solo el titulo en el cliente**. La ruta inexistente sigue respondiendo el
documento con **estado HTTP 200** â€”eso lo sirve el `try_files` del nginx de la SPAâ€”, asi que un
crawler o un monitor sigue viendo una pagina valida. Queda pendiente y no se ha tocado.

## Pruebas
- 190/190 frontend (`--no-file-parallelism`), `tsc --noEmit` y `test:typecheck` limpios,
  `vite build` correcto.
- Verificado sobre `a47e599` (R206 ya integrada), sin conflictos.

## Nota de coordinacion
No se toco `docs/auditoria-producto/plan-mejora-integral-2026-10.md`: lo esta actualizando
otro agente con los resultados de R206. `compose.ronda207.yaml` es andamiaje local de puertos
y no va commiteado.
