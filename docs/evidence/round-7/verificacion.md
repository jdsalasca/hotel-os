# Ronda 7 — Frontend, calidad visual y capturas verificadas

Fecha: 2026-10-06. Rama `develop`.

## Sistema SCSS

Tokens (`_tokens.scss`), mixins (`_mixins.scss`), base y componentes (`_componentes.scss`), todo
importado desde un único punto (`indice.scss`). **Cero estilos inline** para construir interfaz; los
`style={{}}` que quedan son ajustes de rejilla puntual, no construcción de componentes.

Paleta con contraste verificado contra `#fbf9f5`: tinta 12.6:1, tinta suave 6.9:1. Los acentos
(teja `#9c4526`, arcilla, ocre, verde andina, cielo) se usan con moderación como acento, no como
fondo dominante. Sin imágenes remotas: `HuecoImagen` reserva el espacio para la fotografía que el
hotel autorice.

## Panturas

Público: inicio con búsqueda, disponibilidad con precios, formulario, confirmación con código,
consulta por código+correo.
Panel: login, reservas con historial, inventario y bloqueos, integraciones, indicadores.

## Bugs reales encontrados y corregidos (todos por verificación visual, no por lectura)

| Bug | Síntoma | Causa |
|---|---|---|
| La confirmación nunca se pintaba | Tras reservar con éxito salía "No hay una reserva en curso" | `enCurso` se releía en cada render; al borrar `sessionStorage` tras el éxito quedaba en null y ganaba el estado vacío |
| El flujo de reserva se rompía sin HTTPS | No navegaba al formulario | `crypto.randomUUID()` solo existe en contextos seguros; en HTTP local es `undefined` |
| Tabla ilegible en móvil | Fechas partidas en una palabra por línea | Sin `min-width`, las celdas se comprimían |
| Scroll horizontal en toda la página | `scrollWidth` 630 con viewport 390 | `min-width` de la tabla ensanchaba el documento pese a `overflow-x:auto` en el contenedor |

El último se resolvió midiendo, no adivinando: `tools/capturas/diagnostico.mjs` informa
`scrollWidth` por ruta y localiza el culpable por ocultación. Todas las rutas quedan en
`scrollWidth=390` con viewport 390.

La solución en móvil no es una tabla desplazable sino **tarjetas apiladas** con `data-label`: en un
panel que se revisa desde el teléfono, apilar es más legible que obligar a arrastrar.

## Capturas

18 capturas verificadas en `docs/screenshots/` (9 de escritorio + 9 de móvil), generadas con
Playwright dentro de Docker (no hay Node en el host). El guion **falla si hay un error de consola o
una respuesta de API ≥400**, así que una captura nunca puede documentar una pantalla rota.

## Verificación

```
.\mvnw.cmd test    ->  Tests run: 135, Failures: 0, Errors: 0, Skipped: 0
docker compose up --build   ->  api healthy, web healthy
```

Flujo completo verificado contra los contenedores:

```
POST /api/reservas          -> 201 {"codigo":"H-F824583F","estado":"PENDIENTE",...}
GET  /api/admin/reservas    -> la reserva aparece con origen WEB
docker compose restart api  ->  health ok y las reservas siguen ahí
```

**Persistencia tras reiniciar contenedores (requisito de aceptación):**

```
reservas tras reiniciar:
  H-F824583F WEB PENDIENTE 2026-12-10->2026-12-13
  H-DF01BF78 WEB PENDIENTE 2026-12-10->2026-12-13
```

## Bug de despliegue corregido

`SQLITE_CANTOPEN` en el contenedor: `JDBC_URL` llega como `jdbc:sqlite:/data/hotel.sqlite3` y la
fábrica de DataSource añadía el prefijo otra vez. Las pruebas no lo detectaban porque pasaban rutas
limpias; solo aparecía en Docker. Corregido con `SqliteDataSourcesTest` (7 pruebas) que fija ambas
formas de ruta.

## Cómo reproducir

```powershell
docker compose up --build -d
$env:HOTEL_DEMO_ADMIN="true"; $env:WEB_PORT="5174"
docker compose -f compose.yaml -f compose.capturas.yaml up --build --abort-on-container-exit capturas
```