# Ronda 157 - Cero vulnerabilidades en dependencias web

## Cambio
- `react-router-dom` 7.8.2 → 7.18.4 (+ lockfile): cierra XSS, open redirects y
  DoS del rango 6.0.0–7.17.0 (GHSA-2w69, GHSA-9jcx, GHSA-49rj, GHSA-8646…),
  varios tocando redirecciones, justo lo que este login usa.
- `vite` 7.1.7 → 7.3.7 (+ lockfile): cierra bypass de `server.fs.deny` y path
  traversal del dev server (solo-dev: nunca corre en prod, pero igual se cierra).

## Métrica
- `npm audit --omit=dev`: 2 high → **0**.
- `npm audit` total: 3 high → **0 vulnerabilidades**.

## Verificación (salida real, 2026-10-09)
- `tsc --noEmit` y `tsc -p tsconfig.tests.json`: exit 0.
- `vitest`: 24 archivos / 90 tests en verde.
- `vite build`: ok (el bundle que sube a prod).

## Despliegue
- Merge a `develop`, push y `compose up -d --build` en TopNUC con health OK.
