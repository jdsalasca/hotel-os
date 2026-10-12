# Verificación integrada — Ronda 240

**Alcance:** sondeo de `/admin/hoy` cada 30 segundos, solo con la pestaña visible; conservación y aviso del último parte ante un error de red. No cambia la API, la base de datos, SCSS ni el tema del producto. El tema claro/oscuro continúa aplazado.

## Código

- `useActualizacionPeriodicaVisible` vuelve a consultar el parte sin recargar la página.
- No sondea durante la carga inicial ni solapa dos peticiones de fondo.
- Una respuesta de una fecha anterior se descarta al cambiar la fecha.
- Un error de fondo conserva las filas y publica un aviso accesible `role=status`; el detalle técnico no reemplaza los datos operativos.
- La siguiente lectura correcta actualiza las filas y limpia el aviso.

## Pruebas y compilación

- Prueba dirigida de `PaginaAdminHoy.test.tsx`: **7/7**.
- Suite frontend: **266/266 en 35 archivos**.
- `npm run test:typecheck`: código de salida 0.
- `npm run build`: código de salida 0; TypeScript limpio; Vite transformó 77 módulos. CSS 26,73 kB; JS 406,18 kB.
- `git diff --check`: limpio.

Durante una ejecución que lanzó Vitest, typecheck y build simultáneamente falló una vez la prueba del sondeo por avanzar relojes falsos fuera de `act`. El test se sincronizó con `act` y `advanceTimersByTimeAsync`; la dirigida y la suite completa pasan en la corrida final serial. Vitest todavía imprime `Not implemented: Window's scrollTo()` desde un test preexistente de jsdom; la suite termina con código 0.

## Revisión visual en navegador

Playwright con respuestas sintéticas verificó éxito y fallo en escritorio (1440×900) y móvil (390×844).

- En éxito aparecen la llegada nueva y la salida sin navegación del documento; la fila de una persona ya alojada permanece. Para acortar la captura visual el reloj del navegador se aceleró a 1 s; la suite dirigida avanza los 30 s del intervalo real.
- En fallo se inyectó 503 al sondeo: las tres filas anteriores permanecen y aparece “No pudimos actualizar el parte / Los datos pueden estar desactualizados”. Una lectura correcta posterior despeja el aviso.
- En 1440 px el documento mide 1440 px y la vista principal va de x=144 a x=1296; en 390 px mide 390 px y la vista principal va de x=0 a x=390. No hay desbordamiento horizontal. Con datos representativos, las tres listas y el aviso quedan dentro de la vista inicial móvil.
- `pageerror`: cero en ambos tamaños. El smoke también registra el 503 intencional y dos 401 de `/api/yo`, esperables porque no se abrió una sesión de huésped.

Capturas:

- `refresco-escritorio-integrado.png`
- `refresco-movil-integrado.png`
- `integracion-escritorio.png`
- `integracion-movil.png`

## Límites de la evidencia

El navegador usó nombres, habitaciones y sesiones sintéticos y un Vite local; esto verifica comportamiento y presentación del frontend, no integración con una API real, producción ni datos del hotel.