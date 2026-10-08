# Ronda 114 - Cargar anteriores en el hilo del chat

## Problema
La ronda 112 avisaba `Mostrando los últimos 50 de N` pero era un callejón sin
salida: no había forma de ver la historia vieja.

## Cambio
- `GET .../mensajes?antes_de=<id>` (huésped y panel): los 50 con id menor, en
  cronológico, más `hay_mas` (queda historia por debajo de la página).
- `HiloMensajes`: botón `Cargar anteriores` sobre la lista cuando `hay_mas`;
  antepone la página (`cargar(primerId)`) y el botón desaparece al llegar al
  inicio. Las lecturas paginadas no re-marcan vistos (la primera página ya marcó
  todo lo del otro lado).
- `PaginaMisReservas` y `PaginaAdminReservas` pasan `antes_de` y usan el tipo
  compartido `Hilo` (antes tipaban solo `{mensajes}`, con lo que el botón jamás
  habría aparecido en prod).

## TDD rojo-verde (evidencia real)
- Backend: `hiloPaginaAnteriores` → NPE por `hay_mas` ausente; con el cambio,
  `ChatTest` 4/4.
- Frontend: `carga anteriores y los pone arriba del hilo` → botón no encontrado;
  con el cambio, `HiloMensajes` 5/5. Un `tsc` intermedio cazó
  `mensajes[0]` posiblemente indefinido (`noUncheckedIndexedAccess`): corregido
  con guarda explícita.

## Verificación (salida real, 2026-10-08)
- Backend: `Tests run: 389, Failures: 0, Errors: 0, Skipped: 1` + `BUILD SUCCESS`
  (omitido preexistente `@DisabledOnOs(WINDOWS)`).
- Frontend: `vitest` 10 archivos / 35 tests en verde; `tsc --noEmit` y
  `tsc -p tsconfig.tests.json` exit 0; `vite build` → `✓ built`.
- Visual: el botón solo existe con >50 mensajes (tope antispam de 30/hora lo hace
  inalcanzable por UI normal); jsdom afirma botón, orden antepuesto y
  desaparición al final, con clases de botón ya existentes. Sin captura de
  navegador por esa razón; cero estilos nuevos.

## Despliegue
- Commit en `develop`, push y `compose up -d --build` en TopNUC con
  `GET /api/health → {"estado":"ok"}` verificado después.
