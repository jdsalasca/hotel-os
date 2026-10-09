# Ronda 185 — verificación de alta inicial del inventario

## Cambio revisado

Cuando la carga confirma que faltan tipos, habitaciones o planes, «Alta del hotel, paso a paso» se muestra antes del calendario. Con esos tres elementos disponibles, el calendario y la tabla conservan su prioridad. El mismo bloque de JSX se reutiliza y el enlace de habitaciones refleja si el alta está arriba o abajo.

## Pruebas

- Estado base: `npm test` — 24 archivos, **132 pruebas aprobadas**.
- Primero se ejecutó la nueva prueba contra el código base: el caso sin habitaciones falló porque el asistente estaba después del calendario; el caso configurado conservó el orden esperado.
- Después del cambio: `npm test -- src/paginas/PaginaAdminInventario.test.tsx -t "prioridad del alta inicial"` — **2 aprobadas**.
- Suite final: `npm test` — 24 archivos, **134 pruebas aprobadas**.
- `npm run test:typecheck` — salida 0.
- `npm run typecheck` — salida 0.
- `git diff --check` — sin errores.

## Build

- `npm run build` directo en Windows se detuvo: el proceso Vite llegó a aproximadamente 2.9 GB y no produjo `dist`.
- Ruta de build del proyecto: `docker build -t hotel-os-r185-inventario-web:verify .` desde `apps/web` — **terminó con código 0**. Vite 7.3.7 transformó 70 módulos; bundle JS 374.38 kB (107.80 kB gzip), CSS 22.32 kB (4.80 kB gzip).

## Revisión visual en Chrome

El servidor local Vite sirvió la app desde `http://127.0.0.1:5185/admin/inventario`. Las respuestas API fueron simuladas en el navegador; no se usaron datos ni credenciales de producción.

- Inventario vacío, escritorio: orden accesible asistente → calendario; captura en `alta-inicial-escritorio.png`.
- Inventario vacío, móvil 390 × 844: título, cuatro pasos y formulario visibles al inicio; ancho de documento 390 px y **sin desbordamiento horizontal**; captura en `alta-inicial-movil.png`.
- Inventario configurado, escritorio 1365 × 900: calendario (índice 0), habitaciones y asistente (índice 2); una habitación de prueba visible y **sin desbordamiento horizontal**; captura en `inventario-configurado-escritorio.png`.
- Consola Chrome: sin errores ni advertencias en el estado configurado.

## Límites

La revisión fue local con API simulada. No se desplegó a producción en esta ronda.
