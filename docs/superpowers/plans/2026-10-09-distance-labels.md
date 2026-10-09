# Etiquetas claras para distancias Implementation Plan

> **For agentic workers:** Use the steps below in order; each test must demonstrate the behavior before the implementation changes.

**Goal:** Make nearby places read as meters and far places as kilometers in the public map, with Spanish-Colombian number formatting.

**Architecture:** Keep the existing coordinate-based distance calculation in the public page, return rounded meters from one helper, and format them at the rendering boundary. Explain that this is a straight-line distance and keep the route link as the way to navigate. Do not add an API contract or duplicate server-side calculation.

**Tech Stack:** React 19, TypeScript, Vitest, Testing Library, Vite.

**Spec:** Bounded in-chat design for the ongoing HOTEL-OS usability goal; retain the existing map and its straight-line distance meaning.

## Global Constraints

- Keep presentation styles in SCSS.
- Explain in the page that distance is straight-line orientation, not the route.
- Do not touch the in-progress R176 backend files or migration.
- Keep dependencies and manifests unchanged.

## Review Focus

- A place under 1,000 m must display a rounded integer in meters.
- A place at or above 1,000 m must display kilometers with at most one decimal and a Spanish comma.
- Zero distance must remain readable as 0 m.
- The visible note distinguishes the straight-line estimate from the route.
- Hide the note when the hotel point is missing and no distance is shown.
- The Cómo llegar link still opens the existing route URL and does not wrap on mobile.

---

### Task 1: Distance label behavior

**Files:**
- Modify: apps/web/src/paginas/PaginaInicio.test.tsx
- Modify: apps/web/src/paginas/PaginaInicio.tsx
- Modify: apps/web/src/styles/_componentes.scss
- Modify: docs/plan.md
- Create: docs/evidence/round-178/verificacion.md

**Interfaces:**
- distanciaMetros(desdeLat, desdeLng, hastaLat, hastaLng): number returns haversine distance rounded to the nearest meter.
- formatearDistancia(metros): string returns N m below 1,000 m and localized N,N km at or above 1,000 m.

- [ ] Add map fixtures and tests for a nearby location (44 m), zero distance, a far location (1,1 km), the straight-line note, its hidden state without a hotel point, and the route URL.
- [ ] Run the focused page test and confirm it fails because the current page says 0 km for the nearby place and uses a dot for the far one.
- [ ] Implement the minimal helpers and use them in the existing map label.
- [ ] Run the focused test, all frontend tests, and the frontend production build.
- [ ] Verify desktop and mobile map labels, note, and unbroken route link in a real browser; capture screenshots and record viewport checks.
- [ ] Update the round tracker with the result, commit the functional change, cherry-pick to an isolated integration checkout based on origin/develop, rerun verification, and push to origin/develop.
