# Ronda 204 - Un fallo en la cabecera ya no deja la pagina en blanco

## Cómo se descubrió el hueco (y por qué el diagnóstico inicial era erróneo)
El primer intento de esta ronda fue añadido y **descartado**: noté que no había ningún
`ErrorBoundary` y supuse que un fallo de render dejaba la pantalla en blanco. Era falso.
`MuroAntipanico` (en `componentes/Estado.tsx`) ya envuelve a `<Routes>` y ya tiene mensaje
y salida, y `Estado.test.tsx` lo comprueba. No se añadimos un segundo muro.

Lo que si quedaba fuera: `MuroAntipanico` envuelve **solo `<Routes>`**. La cabecera
(`SaludoSesion`, `BadgeMensajes`, `CierreSesionPanel`), el componente `VolverArribaAlNavegar`
y el pie quedan por fuera. Y React **no propaga hacia arriba el error de un `useEffect`**: al
lanzarse fuera de todo muro, desmonta el arbol entero.

La reproduccion no fue una suposicion, salio de la traza real. Al hacer fallar un efecto del
shell, React apunto a `commitHookEffectListMount`, que esta por encima de cualquier
componente, y la pagina se quedo vacia.

## TDD rojo-verde
`ShellRoto.test.tsx`, sobre el `App` real (no sobre un componente aislado):
- `un fallo del shell ya no deja la pantalla en blanco, da una salida`: rojo con la traza
  `Error: fallo en la cabecera` desmontando el árbol; verde con el aviso y el botón.
- `sin fallos, la cabecera y las rutas siguen mostrando su contenido`: no deja que el muro
  exterior rompa las rutas de siempre.

## La solucion: el mismo muro, mas arriba
No un boundary nuevo. `MuroAntipanico` envelopa ahora tambien el shell, con una variante
`sutil` que ofrece **Recargar** en lugar de Volver al inicio: cuando lo que se rompio es la
cabecera, el enlace "Volver al inicio" vive justo en lo que fallo, asi que no serviria.

`App.test.tsx` sigue en 9/9: envolver el shell no rompio ninguna ruta.

## Verificacion visual (contenedores reales, fallo inyectado de verdad)
Con `window.scrollTo` reventando antes de cargar la pagina (es lo que usa "volver arriba"):

| Medida | Valor |
|---|---|
| Alertas visibles | 1 |
| Botones | `["Recargar la página"]` |
| Muestra aviso de error | si |
| **Pagina en blanco** | **no** |
| Errores JS | 0 |

Capturas: `01-error-shell-escritorio.png` (1440 px) y `02-error-shell-movil.png` (390 px),
sin desborde en ninguna. Antes de este commit, esa misma pantalla era completamente blanca.

## Pruebas
- 183/183 frontend (`--no-file-parallelism`), `tsc --noEmit` y `test:typecheck` limpios,
  `vite build` correcto, `git diff --check` limpio.

## Nota de coordinacion
Rama `goal/round-204-error-fatal`, rebasada sobre `ae1856d` (plan y verificacion de la
auditoria ya integrados). 0 conflictos. `compose.ronda204.yaml` es andamiaje local de puertos
y no va commiteado.
