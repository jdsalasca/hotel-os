# Ronda 192 — Próximas y en curso, separadas del historial

## Alcance

En «Mis reservas», las estadías confirmadas o pendientes cuya salida aún no ocurre se muestran
primero. Las que están en curso aparecen antes que las futuras; después, las próximas se ordenan
por llegada cercana. Reservas pasadas, canceladas, rechazadas o en otros estados no vigentes quedan
bajo «Historial y no vigentes», ordenadas por llegada más reciente. No cambian los endpoints, los
montos ni las acciones existentes.

## Verificación

- La prueba incluye una estadía en curso, dos futuras, una cancelada, una rechazada con fechas
  futuras y una anterior. Antes del ajuste, Vitest falló: la rechazada se clasificaba como próxima.
  Después, `npm test -- --run src/paginas/PaginaMisReservas.test.tsx`: **10 pruebas aprobadas**.
- `npm test -- --reporter=dot`: **27 archivos y 145 pruebas aprobadas**. La salida incluye el error
  intencional `pum` del test del límite de errores React y la advertencia de `scrollTo()` no
  implementado por jsdom; Vitest finalizó con código 0.
- `npm run test:typecheck`: aprobado.
- `npm run build`: aprobado, Vite transformó 71 módulos. CSS **23,69 kB** (gzip 5,02 kB), JS
  **379,36 kB** (gzip 109,15 kB).
- En Chrome local, escritorio a **1365 × 900**: grupos y orden visibles; `scrollWidth` del cuerpo
  fue 1350 px frente a 1365 px de viewport, sin desbordamiento horizontal.
- En Chrome local, móvil a **390 × 844**: `innerWidth`, `documentElement.scrollWidth` y
  `body.scrollWidth` fueron **390 px**; se revisaron los rótulos y todas las tarjetas.
- La revisión visual usó una API local temporal con seis reservas sintéticas que cubren los estados
  indicados. `/api/admin/sesion` devolvió 401 de forma esperada porque el fixture no inicia sesión
  administrativa; la lista de huésped sí cargó. No se usaron datos reales ni se comprobó despliegue.

Capturas completas de página revisadas:

- `escritorio.png` — 1350 × 1567.
- `movil.png` — 390 × 3625.

## Ajuste de compilación

La compilación de la copia limpia de `develop` se detenía durante el tree-shaking con Rollup 4.64.0.
La versión transitoria quedó en 4.64.3 en `apps/web/package-lock.json`; con esa versión `npm ci` y
el build de producción finalizaron. Las notas oficiales de [Rollup 4.64.3](https://github.com/rollup/rollup/releases)
describen una corrección de regresión de rendimiento del tree-shaking compatible con el problema
observado. El cambio solo actualiza Rollup y sus paquetes opcionales de plataforma en el lockfile.
