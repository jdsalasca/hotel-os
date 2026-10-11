# Ronda 216 - la suite se degrada bajo carga (diagnostico y mejora segura)

## El problema medido

Linea base sobre `develop` (365f2e7), sin cambios:

- 204 pruebas, 0 fallos.
- Duracion de la suite: 15,40 s.
- **Entornos creados: 31 (uno por archivo), 50,11 s acumulados**: un 3,3x el tiempo real
  de la suite. El 75% del trabajo era levantar jsdom, no ejecutar pruebas.

Con Docker y los procesos de los otros agentes en marcha, ese coste se dispara. Es el
origen de los timeouts intermitentes que se veian de vez en cuando (5 s superados en
`PaginaInicio` y `PaginaReserva`) y que no se reproducen con la maquina tranquila.

## Las dos salidas que anuncia Vitest, y por que no se tomaron

**1. `pool: 'vmThreads'`** - "create it once per worker (keeps per-file isolation)".

Se probo. Revientan 7 pruebas en 4 archivos (PaginaLoginAdmin x4, SaludoSesion,
PaginaAdminPanel, App) con:

    TypeError: Cannot redefine property: location

Bajo ese pool jsdom deja `window.location` **y sus metodos** no redefinibles, asi que los
stubs `Object.defineProperty(window, 'location', ...)` no funcionan. Estubar solo el
metodo (`window.location.assign`) falla igual: `Cannot redefine property: assign`.

No es un fallo de esas pruebas: es un contrato distinto del pool. Usarlo exigiria que el
codigo de produccion dejara de depender de `window.location` directamente (un navegador
inyectado), que es otra ronda.

**2. `isolate: false`** - comparte el entorno entre archivos.

Mas rapido, pero un `window` modificado en un test aparece modificado en el siguiente y el
fallo sale en un archivo que no tiene la culpa. Queda vigilado por un test.

## Lo que sí se aplico

Los tres tests de logica pura no necesitan DOM y se marcaron `@vitest-environment node`:

    src/api/cliente.test.ts
    src/api/formato.test.ts
    src/api/sesion.test.ts

Medido en A/B, ejecutando solo esos tres archivos (carga comparable):

    SIN node:  1,82 s   (jsdom creado)
    CON node:  0,85 s   (sin jsdom)

Y en la suite completa, consecutivas para que la carga fuese parecida:

    SIN node:  79,34 s de entorno acumulado, 24,65 s de pared
    CON node:  75,49 s de entorno acumulado, 25,36 s de pared

**Honestidad sobre el numero**: el ahorro real es ~3,85 s de entorno acumulado, un 5%, y el
tiempo de pared **no mejora** porque los workers lo absorben. Es una mejora pequena y
segura, no una solucion al problema. Lo que esta ronda deja de verdad es el diagnostico
cerrado para que nadie repita la investigacion.

## Pruebas

205 pruebas, 32 archivos, 0 fallos. `tsc --noEmit` limpio, `test:typecheck` limpio,
`vite build` correcto.
