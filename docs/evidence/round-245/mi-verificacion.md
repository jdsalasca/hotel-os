# Ronda 245 — Integraciones tampoco debe tumbar el panel

## De dónde sale

La ronda 242 arregló el mismo crash en `PaginaAdminIndicadores` y `PaginaAdminHoy`, pero lo
hizo pantalla por pantalla. Aplicando la causa raíz, busqué el patrón en el resto:

```
canal.variablesFaltantes.length   canal.requisitosPendientes.length
canal.mapeos.map                  canal.ultimasSincronizaciones.map
planes.map                        recursos.map
```

`PaginaAdminIntegraciones` tenía **seis** accesos sin guarda. Y no es un caso raro: es
exactamente el estado `NO_CONFIGURADO` de producción, donde los OTAs no tienen credenciales.

## Comprobado antes de afirmarlo

En el navegador, con un canal declarado pero sin sus listas:

```
TypeError: Cannot read properties of undefined (reading 'length')
```

Resultado: **`rota: true`**, sin `h1`, "Algo se rompió". La pantalla se caía igual que las
otras dos.

## Cambio

`apps/web/src/paginas/PaginaAdminIntegraciones.tsx`:

- `variablesFaltantes` y `requisitosPendientes` → `?? []`.
- `habitaciones`, `tipos` y `planes` → `Array.isArray(...) ? ... : []` al guardarlas. Guardaban
  lo que llegara sin mirar qué era, así que un cuerpo que no es array acababa rompiendo
  `planes.map` en el formulario de mapeos.
- `recursos` → guarda defensiva sobre el valor ya normalizado.
- Test nuevo (`PaginaAdminIntegraciones.test.tsx`), que no existía.

## Verificación

### TDD

RED confirmado. Ojo al proceso: la **primera** versión de mis tests pasaba sin arreglar nada,
porque solo comprobaba que no apareciera "Algo se rompió" — y esa pantalla, renderizada sin
el muro antipánico, deja el DOM **vacío** en lugar de mostrar ese texto. Los tests eran
demasiado flojos. Se reforzaron para exigir que la pantalla pinte su encabezado y el nombre
del canal, y entonces sí fallaron (3/3) antes del fix.

### Pruebas

| Qué | Resultado |
| --- | --- |
| Dirigida `PaginaAdminIntegraciones.test.tsx` | 3/3 |
| Navegador, respuesta parcial | antes `rota: true` → después `rota: false`, `h1: Integraciones con canales`, canal visible |
| Móvil 390×844 | `rota: false`, sin desbordamiento |

Capturas: `integraciones-parcial-escritorio.png`, `integraciones-parcial-movil.png`.

Un error de procedimiento que conviene dejar dicho: la primera comprobación en el navegador
dio `rota: true` **con el bundle antiguo**, porque el contenedor se había levantado antes de
las ediciones. Hubo que reconstruirlo para que la medición valiera algo.

## Pendiente

`PaginaAdminInventario.tsx` (1406 líneas) tiene accesos del mismo tipo —`nochesCalendario`,
`filasCalendario` con `fila.noches.map` en la línea 365— que no se han revisado todavía. Es la
siguiente candidata natural.
