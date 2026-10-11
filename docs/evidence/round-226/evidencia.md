# Ronda 226 — Los huéspedes se eligen de una lista, no se escriben a mano

## El problema

Era una de las quejas literales del usuario: *"veo campos abiertos que deberían ser checkbox,
desde habitaciones para el visitante"*. En el buscador público, "Huéspedes" era:

```tsx
<input id="huespedes" type="number" min={1} max={20} step={1} ... />
```

Un campo numérico abierto. Se podía escribir `0`, dejarlo vacío (que da `NaN`) o `99`: el
único aviso era un mensaje de error **después** de intentar buscar. Y en móvil, para elegir
entre 1 y 20 había que teclear.

## El cambio

`PaginaInicio.tsx` — selector nativo con las 20 opciones. `HUESPEDES_POSIBLES`,
`HUESPEDES_MINIMO` y `HUESPEDES_MAXIMO` reemplazan los números mágicos que estaban sueltos
en la validación, así que el rango vive en un solo sitio.

La validación **no se ha tocado y sigue haciendo falta**: aunque el selector ya no deja
elegir un valor inválido, la búsqueda se puede restaurar desde el estado de navegación con
cualquier número (el test de "más de 20" lo cubre y sigue pasando). Quitar la guarda habría
sido optimizar de más.

Sin SCSS: el selector toma el estilo de `campo` que ya tienen los demás campos del buscador.

## Pruebas

TDD RED → GREEN:

```
AssertionError: expected 'INPUT' to be 'SELECT'
```

El test comprueba que la etiqueta apunta a un `SELECT`, que las 20 opciones son exactamente
`1` a `20`, y que el valor por defecto es `2`. Tu que corregir el test dos veces: primero
usé `.locator()` sobre un elemento en vez de un locator, y el segundo error fue mío, no del
componente.

```
Test Files  32 passed (32)
Tests       218 passed (218)
Type Errors no errors
```

Build:

```
dist/assets/index-dIPQkYeI.js   399.82 kB │ gzip: 114.89 kB
✓ built in 7m 54s
```

## Verificación visual

`campo-huespedes.png` — el selector renderiza como control nativo con "2" elegido.

`campo-huespedes-movil.png` — a 390 px mide `x:16, width:343`: ancho completo con sus
márgenes, dentro del mismo criterio de medida que las rondas anteriores.

En móvil el beneficio es concreto: al tocar sale la rueda del sistema operativo, no un
teclado numérico, y no se puede introducir un valor fuera de rango.

## Nota

Es un cambio pequeño a propósito: convierte un campo que el usuario señaló en el control que
corresponde, sin tocar validación, estilos ni la lógica de búsqueda.