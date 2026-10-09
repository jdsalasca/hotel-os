# Selector de moneda compartido — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** En Datos del hotel, elegir moneda de una lista clara compartida con los planes tarifarios y evitar errores de digitación.

**Architecture:** Extraer el catálogo de nueve monedas que hoy vive dentro de `PaginaAdminInventario` a `src/dominio/monedas.ts`. Reutilizarlo en la página de inventario y en Datos del hotel. Si la configuración ya tiene un código no listado, mostrarlo como opción actual para conservarlo hasta que el hotelero seleccione otro. Si la lectura inicial falla, conservar la ruta de recuperación con edición manual del código de tres letras.

**Tech Stack:** React 19, TypeScript, Vite, Vitest, Testing Library y SCSS existente para controles nativos.

**Spec:** Decisión de diseño acotada registrada en esta conversación el 2026-10-09; opciones y tradeoffs abajo.

## Global Constraints

- No agregar dependencias.
- Mantener el contrato `POST /api/admin/hotel-config` y el campo ISO `moneda`.
- No perder un código ya guardado que no figure entre las nueve opciones.
- Mantener la accesibilidad nativa de `<label>` y `<select>`; no agregar estilos inline ni CSS suelto.
- Ejecutar TDD y verificar suite, chequeos de tipos y build antes de integrar.

## Review Focus

- Configuración vacía: el selector muestra una indicación clara y permite guardar otros datos sin imponer una moneda nueva.
- Código existente incluido: permanece seleccionado y conserva el valor de API.
- Código existente fuera del catálogo: aparece como opción actual, se puede mantener o reemplazar y nunca se borra por cargar la página.
- Lectura fallida: aparece el error, se mantiene la recuperación manual del código ISO de tres letras y el guardado exitoso restablece el selector.
- Selección nueva: el código elegido llega intacto al POST.
- Página de inventario: el traslado del catálogo no altera el selector de planes ni sus opciones.

---

## Misiones candidatas para rondas próximas

| Misión | Impacto | Esfuerzo | Estado |
|---|---:|---:|---|
| Resumen de reservas y pagos para huéspedes, con navegación clara | Muy alto | Medio | Coordinar solapamiento antes de tocar esa vista |
| Opciones de amenidades más claras para búsquedas y habitaciones | Alto | Medio | Pendiente de auditoría de flujo |
| Selector de moneda compartido entre hotel e inventario | Medio | Bajo | **Cerrada en R187** |
| Gestión de sitios turísticos desde administración y mapa | Alto | Alto | Pendiente de diseño del modelo operativo |
| Conversación huésped-hotel con estados y notificaciones | Alto | Alto | Nuevo subsistema, requiere diseño propio |

## Opciones consideradas

1. Copiar las nueve opciones en la página del hotel: cambio mínimo, pero deja dos listas que pueden divergir.
2. Extraer el catálogo a un módulo de dominio y reutilizarlo en ambas páginas; preservar códigos previos fuera de la lista: una única fuente, sin migración de datos. **Elegida.**
3. Crear un catálogo dinámico de todas las monedas y un buscador: más cobertura, pero introduce API y una interacción mayor sin que el producto actual la necesite.

## Task 1: Catálogo compartido y selector de Datos del hotel

**Files:**
- Create: `apps/web/src/dominio/monedas.ts`
- Modify: `apps/web/src/paginas/PaginaAdminInventario.tsx`
- Modify: `apps/web/src/paginas/PaginaAdminHotel.tsx`
- Test: `apps/web/src/paginas/PaginaAdminHotel.test.tsx`
- Update: `docs/plan.md` (solo en el bloque nuevo R185–R187, separado del WIP de R180)
- Evidence: `docs/evidence/round-187/`

**Interfaces:**
- Produce `export const MONEDAS` con `codigo` y `nombre`, idénticos a las nueve opciones actuales del inventario.
- Both pages consume that single export; no page owns a duplicate currency list.

- [x] **Step 1: Add a failing test** in `PaginaAdminHotel.test.tsx` asserting that Moneda is a labelled combobox, offers the existing `USD — Dólar estadounidense` option, and submitting after selecting `USD` sends `{ moneda: 'USD' }`.
- [x] **Step 2: Verify RED** with `npm test -- src/paginas/PaginaAdminHotel.test.tsx`; expected failure: no combobox named Moneda exists yet.
- [x] **Step 3: Add the shared catalog and consume it from both pages.** Render Moneda as a native select with an empty prompt, using only the nine existing catalog values.
- [x] **Step 4: Verify GREEN** for selecting and saving a supported currency with the focused test.
- [x] **Step 5: Add a failing regression test** proving an existing code outside the curated options remains selected and is sent unchanged if the hotel saves without replacing it; run the focused test and observe RED.
- [x] **Step 6: Preserve an out-of-list current code as a labelled option, then rerun the focused tests and confirm both cases pass.**
- [x] **Step 6b: After independent review found that a failed read made a legacy code impossible to re-enter, add a failing recovery test; show a three-letter text field only on read failure and switch back to the shared select after successful save.**
- [x] **Step 7: Run full frontend suite, `npm run test:typecheck`, `npm run typecheck`, and `npm run build`; save actual outcomes in the evidence report.**
- [x] **Step 8: Review desktop and mobile UI in Chrome at 1365×900 and 390×844; save screenshots and check there is no horizontal overflow or console error.**
- [x] **Step 9: Update only the new round entries and upcoming mission shortlist in `docs/plan.md`; keep the existing R180 agent diff intact.**
- [ ] **Step 10: Review diff, commit atomically, cherry-pick to a clean integration worktree from latest `origin/develop`, rerun required checks, push, verify remote SHA, then remove only this round's worktrees/branches and prune.**

## Self-review

- Every requested behavior maps to the test or verification steps above.
- The selected path adds no server endpoint or persisted schema; it reuses the existing options and preserves existing non-catalog data.
- The other agent's current `docs/plan.md` change is confined to the R176/R180 section near line 703; R187 will be added in a separate hunk near the completed UX rounds.
- No new dependency or style rule is introduced.
