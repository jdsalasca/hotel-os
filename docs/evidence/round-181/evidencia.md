# Ronda 181 — El dueño también reserva: el panel devuelve al sitio público

## El problema, medido en el navegador

Con sesión de administrador abierta en `/admin/hoy`, la navegación del panel tenía
**nueve enlaces, todos administrativos**:

```
["Panel","Hoy","Reservas","Inventario","Hotel","Integraciones","Indicadores","Actividad","Mapa"]
```

Cero salida al sitio público. El hotelero es también visitante, pero desde el panel no
tenía forma de volver a la web pública ni de consultar sus propias reservas: tenía que
adivinar la URL. Es el punto que el usuario reportó como *"no veo que un usuario que sea
admin y sea visitante tenga opción de apartar habitaciones"*.

El logo sí enlazaba a `/`, pero un logo sin etiqueta no es un acceso: no dice que ahí se
reserva y en móvil queda fuera del recorrido de lectura.

## El cambio

`apps/web/src/App.tsx` — la rama `enPanel` con sesión repite los dos accesos del
huésped, **Reservar** y **Mis reservas**, antes de los del panel.

`apps/web/src/styles/_componentes.scss` — `.nav__corte`, una línea divisoria con los
tokens `$borde` y `$e-1` que separa "sitio público" del panel. Sin ella, "Reservar" y
"Reservas" quedan uno al lado del otro y suenan casi igual: el primero abre el booking
público y el segundo el listado de gestión. En móvil el separador se oculta
(`max-width: 39.99rem`) porque ahí la nav ya está apilada y la línea no aporta nada.

## Pruebas

TDD RED → GREEN, sobre el test que ya cubría la navegación del panel:

```
× con sesión restaura la navegación completa del panel
✓ con sesión restaura la navegación completa del panel
```

Las dos aserciones nuevas (`a[href="/"]` y `a[href="/mis-reservas"]`) fallan sin el
cambio. Suite completa:

```
Test Files  27 passed (27)
Tests       156 passed (156)
Type Errors no errors
```

Build:

```
dist/assets/index-BYZp7XMI.js   384.15 kB │ gzip: 110.52 kB
✓ built in 2m 58s
```

## Verificación visual

`nav-escritorio.png` —Once accesos. La nav mide `scrollWidth 892 = clientWidth 892`:
no desborda ni parte en dos líneas a 1280 px.

`nav-movil.png` — a 390 px los once enlaces se apilan y `getComputedStyle` del separador
devuelve `display: none`, como estaba previsto.

En ambos casos "Reservar | Mis reservas" queda separado del bloque del panel, de modo que
el dueño distingue de un vistazo dónde está trabajando y dónde va a reservar.

## Nota

Los enlaces de siempre funcionan igual que antes; solo se añaden dos accesos que no
existían. No se tocó la lógica de sesiones ni el backend.