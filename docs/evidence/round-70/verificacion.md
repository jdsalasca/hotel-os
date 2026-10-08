# Ronda 70 — Respuestas viejas no pintan y el runner de tests frontend existe (Etapa E/L)

Fecha: 2026-10-08.

## Dolor

1. La búsqueda principal no tenía la protección del calendario: Buscar A, Buscar B
   antes de que vuelva A, B responde primero y A después → quedaba visible A.
2. El detalle se cacheaba solo por habitación: al cambiar fechas se reutilizaba el del
   viaje anterior. `elegir()` mezclaba la tarjeta vieja con el formulario editado.
3. `cliente.ts`: un 200 con cuerpo no-JSON terminaba como `null` tipado `T`.
4. Sin `signal`, sin id de petición, y una cancelación deliberada se convertía en
   "sin conexión". `sessionStorage` se leía con `JSON.parse` + cast.
5. `package.json` sin tests ni lint de frontend.

## Cambio

- `api/cliente.ts`: `get/post/put/del` aceptan `{ signal }` hasta `fetch`; `AbortError`
  se propaga por nombre (cada reino trae su `DOMException`); 200 con cuerpo no-JSON
  es `ErrorApi` de protocolo (el 204 vacío sigue siendo `null` legítimo).
- `api/usePeticion.ts`: número de petición (solo la última pinta), `abortar()` que
  silencia lo en camino, y al desmontar no se toca estado.
- `PaginaInicio`: `buscarCon` con id de petición (mismo patrón del calendario);
  caché de detalle por `habitación|fechas|huéspedes`; `elegir` usa el snapshot de la
  búsqueda que produjo la oferta (`busqueda`, con respaldo al formulario).
- `PaginaReserva.leerEnCurso`: valida la forma antes de usarla; lo ajeno se descarta.
- Infra: `npm test` → `vitest run` (vitest 5 + jsdom + testing-library, lock
  actualizado) + `vitest.config.ts`. Tests: `cliente.test.ts` (5) y
  `usePeticion.test.tsx` (3, incluye la carrera A/B y el abortar).

## Verificación real

```text
npm test (node:22-alpine, Docker): Test Files 3 passed, Tests 11 passed
(5 cliente + 3 usePeticion + 3 SaludoSesion del colega, intactos y verdes).
docker compose build web → tsc --noEmit limpio + vite build ok.

Prueba viva (web:5174 + api:8080 reconstruidos):
- día 8-oct del calendario → 4 ofertas; detalle abierto con desglose
  «Plan Flexible · −20 % (antes EUR 90), 8 de oct EUR 90»
- consola: solo 401 de chequeos sin sesión (esperados).
Captura: busqueda-detalle-desktop.png (esta carpeta)
```

## Archivos

- Tocados: `cliente.ts`, `usePeticion.ts`, `PaginaInicio.tsx`, `PaginaReserva.tsx`,
  `package.json`, `package-lock.json`, `docs/plan.md`.
- Nuevos: `vitest.config.ts`, `cliente.test.ts`, `usePeticion.test.tsx`, esta carpeta.

## Riesgos y límites

- Queda Etapa E: claves de caché del resto de pantallas admin, estados de
  error/carga compartidos y `useSesion` centralizado. Esta ronda cierra búsqueda,
  detalle, selección y protocolo del cliente.
- `node_modules` en Docker va en volumen anónimo (`hotel-r70-nm`): el bind de
  Windows rompe `npm ci` dentro del contenedor (EIO en esbuild). El `build` del
  Dockerfile no se ve afectado (copia, no monta).
- Trabajo del colega respetado: `SaludoSesion.test.tsx` y Ronda 65 intactos.
