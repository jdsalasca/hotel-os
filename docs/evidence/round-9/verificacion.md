# Ronda 9 — Estilos en SCSS y tipos reales

Fecha: 2026-10-06. Rama `develop`.

## El typecheck no se ejecutaba nunca

`npm run build` era `vite build` a secas: Vite transpila sin comprobar tipos, así que el frontend
compilaba aunque estuviera entero en `any`. Al añadir `npm run typecheck` al build aparecieron, de
golpe, problemas reales:

```
src/api/useSesion.ts(12,37): error TS2304: Cannot find name 'urlApi'   x3
src/paginas/PaginaReserva.tsx(66,18): error TS18047: 'enCurso' is possibly 'null'  x5
src/principal.tsx(2,28): error TS7016: Could not find a declaration file for 'react-dom/client'
vite.config.ts(15,41): error TS2769: 'api' does not exist in type 'SassPreprocessorOptions'
```

Cuatro hallazgos, y dos eran de verdad:

1. **Falta la importación de `urlApi` en `useSesion.ts`.** La había añadido al empezar la ronda y
   no se aplicó. En el navegador eso es `ReferenceError` al comprobar la sesión: el panel se
   quedaba en «no autenticado» siempre. Solo lo detectó el typecheck; el build pasaba.
2. **Las declaraciones de tipos de React no existían.** No había `@types/react`, `@types/react-dom`
   ni `@types/node`: todo React era `any` implícito. Ahora están, y `tsconfig` declara
   `types: ["vite/client", "node"]`.
3. **`enCurso` posiblemente nulo** en el envío de la reserva. El guard `if (!enCurso) return` no
   estrecha el tipo dentro de `enviar`, que es un closure. Se fija `const eleccion = enCurso` tras el
   guard y se usa en el envío y en el resumen: además evita leer un estado que React ya limpió.
4. **`css.preprocessorOptions.scss.api` ya no existe** en Vite 7 (el compilador moderno es el
   único desde Vite 5.4). Era configuración muerta de una versión anterior; se elimina.

## Estilos: 57 `style={{...}}` fuera del código

Los 57 eran **estáticos**: ni uno solo dependía de un valor en runtime. Con eso, la solución corta
era un parcial de utilidades en vez de 57 clases distintas.

`apps/web/src/styles/_utilidades.scss` genera las separaciones en bucle desde los tokens:

```scss
$separaciones: ("1": t.$e-1, "2": t.$e-2, "4": t.$e-4, "5": t.$e-5, "6": t.$e-6);

@each $nombre, $valor in $separaciones {
  .mt-e#{$nombre} { margin-top: $valor; }
  .mb-e#{$nombre} { margin-bottom: $valor; }
  .gap-e#{$nombre} { gap: $valor; }
}
```

57 estilos → **21 clases** que ya salen de la escala de diseño. El nombre lleva el token (`mt-e6` es
`$e-6`), así que no hay números mágicos ni clases huérfanas.

Dos valores inline estaban **fuera de la escala** y se ajustaron al token más cercano:
`font-size: 1.25rem` → `$texto-xl` (1.375rem) y `margin: 2.5rem` → `$e-6` (2rem). Con eso la
tipografía vuelve a la escala en lugar de coexistir con valores sueltos.

**Error propio detectado durante la ronda**: el bucle generaba `.mt-e-6` mientras el TSX usaba
`.mt-e6`. Se comprobó en el CSS compilado (`grep` sobre `dist/assets/*.css`), no a ojo. Ahora el
bundle lleva `.mt-e6{margin-top:2rem}`, `.t-xl{font-size:1.375rem}`, `.pila--fila{...}`,
`.ancho-acceso{max-width:28rem;margin-inline:auto}` y `.lista-marcada{...}`.

## Verificación

```
docker build --target construccion apps/web   ->  tsc --noEmit limpio, vite build ✓ built in 2m53s
docker compose ... capturas                   ->  capturas completas sin errores de consola ni de API
```

18 capturas regeneradas en móvil y escritorio, revisadas a ojo: la extracción no movió ninguna
pantalla. Los botones de acción siguen a todo el ancho en móvil, que es lo correcto para el dedo; en
escritorio los botones secundarios dentro de una pila ocupan el ancho del contenedor, un defecto
**preexistente** (se veía igual antes de esta ronda) que queda anotado en vez de mezclarse aquí.

Un detalle de método: la primera corrida de capturas produjo archivos idénticos a los ya
versionados aunque el CSS había cambiado. La causa fue un contenedor `web` desactualizado, no las
capturas; se repitió con `--force-recreate` y entonces sí cambiaron 12 de las 18 imágenes.

## Pendiente

- Calendario de ocupación real en el panel: hoy hay una comprobación de disponibilidad por habitación
  para todo el mes, no una vista de calendario.
- Gestión de planes y tarifas desde el frontend: la API existe, la pantalla no.
- Mapeos por canal, conciliación y recepción real de reservas de las OTAs.
- Facturación/recibos, configuración del hotel y auditoría de acciones administrativas.
- Botones secundarios que se estiran en escritorio (defecto anterior a esta ronda).