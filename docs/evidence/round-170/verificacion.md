# Ronda 170 — verificación de navegación con ventas pausadas

Fecha: 2026-10-09.

## Resultado

La portada explica que no se reciben nuevas reservas, conserva un acceso a las reservas
existentes y retira la búsqueda, el calendario y los pasos de reserva mientras la venta esté
pausada. Con la venta activa, la búsqueda y el calendario siguen disponibles. La cabecera ofrece
un único enlace de huésped a `Mis reservas`; allí siguen disponibles Google y la consulta por
código/correo. En `/consulta`, `Mis reservas` mantiene `aria-current="page"`.

## Estado real consultado

El 2026-10-09 se consultó en modo de solo lectura
[`GET /api/hotel/venta`](https://hotel.eridu.top/api/hotel/venta): `a_la_venta=false`.
No se enviaron escrituras a producción.

## Verificación visual local

La aplicación se abrió con Playwright en `http://127.0.0.1:5174`. Para comprobar los dos estados
se usó una API fixture aislada en `127.0.0.1:18080`, sin conexión de escritura a producción.

- Pausada, escritorio (1440 × 1000): el hero anuncia la pausa; el aviso aparece antes del collage;
  no hay formulario ni navegación/calendario mensual. Captura: `01-reservas-pausadas-escritorio.png`.
- Pausada, móvil (viewport solicitada 390 × 844): el mensaje y el siguiente paso se apilan sin
  desbordamiento horizontal; las opciones de huésped siguen visibles. Captura:
  `02-reservas-pausadas-movil.png`.
- Activa, escritorio (1440 × 1000): vuelven «Ver disponibilidad», el formulario y el calendario;
  el fixture muestra dos habitaciones desde COP 150.000. Captura:
  `03-venta-activa-escritorio.png`.
- `/mis-reservas`: snapshot de navegador confirmó las opciones «Entrar con Google» y
  «Consultar una reserva sin entrar». `/consulta`: el formulario por código y correo permanece
  disponible; la prueba automatizada confirma el estado activo del enlace de cabecera.

Las solicitudes de `/api/yo` y `/api/admin/sesion` devuelven 401 en la fixture, como corresponde a
una sesión no autenticada; no se inició sesión ni se probaron credenciales externas.

## Pruebas y build

- TDD: antes de implementar, las pruebas añadidas para pausa, destino del huésped y estado activo
  fallaron por los comportamientos ausentes (4 fallidas, 20 aprobadas en los archivos focalizados).
- `npm test -- --reporter=dot`: 24 archivos, 107 pruebas aprobadas. Salida completa:
  `frontend-tests.txt`.
- `npm run test:typecheck`: exit 0. Salida: `typecheck.txt`.
- `npm run build`: exit 0; Vite transformó 70 módulos. CSS 21,74 kB y JavaScript 369,20 kB
  (gzip: 4,68 kB y 106,49 kB). Duración: 3 min 30 s. Salida: `build.txt`.
- `git diff --check`: sin errores.
