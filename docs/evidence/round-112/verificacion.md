# Ronda 112 - El hilo del chat trae los últimos 50 con su total

## Problema
`ChatRepository.hilo()` devolvía el hilo completo sin límite: una reserva con
años de conversación obligaba al navegador a traer miles de filas de una vez.

## Cambio
- `ChatRepository.hilo(id)` → últimos 50 (`ORDER BY id DESC LIMIT 50`, reordenados
  cronológico); nuevo `ChatRepository.total(id)` con el conteo completo.
- `ChatController` responde `{mensajes, total}` en los tres GET de hilo
  (huésped, admin y nuevos).
- `HiloMensajes.tsx` muestra `Mostrando los últimos N de M mensajes.` solo cuando
  `total > mensajes.length` (clase `campo__ayuda sin-margen` ya existente, sin
  estilos nuevos).
- Tests que ordenan la ejecución (`@Order`, orden 1-2-3) porque comparten la
  reserva CHAT01 y cuentan exacto; el test nuevo usa su propia reserva CHAT02.

## TDD rojo-verde (evidencia real)
- Rojo: test nuevo contra componente sin cambiar →
  `Test Files 1 failed (1) / Tests 1 failed | 3 passed (4)`.
- Verde: con el cambio → `Test Files 9 passed (9) / Tests 30 passed (30)`.

## Verificación (salida real, 2026-10-08)
- Backend: `Tests run: 383, Failures: 0, Errors: 0, Skipped: 1` + `BUILD SUCCESS`
  (el omitido es `PermisosDeVolumenTest`, `@DisabledOnOs(WINDOWS)` preexistente).
- Frontend: `vitest run` 9 archivos / 30 tests en verde; `tsc --noEmit` y
  `tsc -p tsconfig.tests.json` exit 0; `vite build` → `✓ built`.
- Visual: la línea nueva solo aparece con >50 mensajes (imposible de provocar por
  UI por el tope de 30/hora de la ronda 108); el test jsdom afirma el texto
  exacto renderizado en el DOM con la clase ya usada en el resto del formulario.
  Sin captura de navegador por esa razón; sin regresión visual (cero estilos nuevos).

## Despliegue
- Merge a `develop`, push y `compose up -d --build` en TopNUC con
  `GET /api/health → {"estado":"ok"}` verificado después.
- Post-merge en `develop`: `Tests run: 388, Failures: 0, Errors: 0, Skipped: 1`
  + `BUILD SUCCESS` (383 de la ronda + 5 del trabajo paralelo de indicadores,
  todos verdes juntos).
- Nota: al cerrar había cambios sin commitear en
  `apps/api/.../indicadores/IndicadoresRepository.java` e
  `IndicadoresServiceTest.java` que NO son de esta ronda (trabajo paralelo de
  otro agente): se dejaron intactos y fuera del commit.
