# Ronda 120 - El informe y la carga ignoran el período viejo (Etapa E)

Fecha: 2026-10-08. Rama: `goal/round-120-periodo-vigente`.

## Misión (brainstorming, vía acotada)

Opciones: (a) guardas E8+E9 con el patrón ya probado (búsqueda, parte, tarifas);
(b) comprobante descargable (zona activa del colega); (c) calendario con arrastre
(alto/alto). Va (a): el informe pinta el mes que ya no está en pantalla.

## Problemas (reproducidos con tests antes de afirmar)

- **E8.** Informe sin guarda: cambiar de período con la lectura en vuelo pintaba el
  mes viejo; además el error viejo sobrevivía a una relectura que sí funcionó
  (`setError` nunca se limpiaba). Rojo: `Ocupación de octubre` pintada tras pedir
  noviembre; `se cayó la red` visible junto al informe nuevo.
- **E9.** `cargar()` del inventario sin guarda: cambiar de mes dejaba el
  calendario del mes viejo. Rojo: `VIEJA-101` en filas tras pedir noviembre.
- Lección de la ronda 119 que volvió a morder: el mock de `../api/cliente` debe
  exportar `urlApi` (lo usa `useSesion`); sin él, `fetch` ni se invoca y el gate
  dice "Sesión requerida". Diagnosticado con probe temporal (luego borrado).

## Cambio

- `PaginaAdminIndicadores.tsx`: contador `peticionInforme` (ignora tardías en
  éxito, error y `finally` de carga) + `setError(null)` al releer.
- `PaginaAdminInventario.tsx`: contador `peticionCarga` en `cargar()` con la misma
  disciplina (las 5 lecturas viajan en un `Promise.all`, una sola guarda).
- Tests: `PaginaAdminIndicadores.test.tsx` nuevo (2, diferidas con orden
  invertido) + caso de carga en `PaginaAdminInventario.test.tsx` (1).

## Verificación real

- Rojo→verde: 3/3 en archivos nuevos (rojo verificado contra código sin fix vía
  `git stash`: firmas `h3 octubre`, `se cayó la red` persistido y `VIEJA-101`).
- Suite frontend **16 archivos / 52 tests en verde** (48 previos + 2 de informe +
  1 de carga + 1 del colega). A mitad de la ronda, `Estado.test.tsx` del colega
  estaba en su rojo TDD (`PuertaAdmin` aún no exportado) y `tsc -p
  tsconfig.tests.json` caía por él; al cerrar, su componente existe y todo está
  en verde. Su WIP quedó intacto y fuera de mi commit.
- `tsc --noEmit` exit 0; `tsc -p tsconfig.tests.json` exit 0; `vite build` exit 0.
- Visual en vivo (stack dev reconstruido): informe de noviembre coherente
  (Ocupación 2/120, cancelaciones SIN_DATOS con motivo, CSV por período).
  Capturas desktop (completa) y móvil 390px en esta carpeta.
- Backend sin tocar en la ronda.

## Trabajo en equipo

- Solo mis rutas en el commit (2 páginas + 2 tests + evidencia + plan). Lo del
  colega (`Estado.*`, comprobante, chat) intacto.
- `round-120/` nuevo y libre.

## Archivos

- Tocados: `web/src/paginas/PaginaAdminIndicadores.tsx`,
  `web/src/paginas/PaginaAdminInventario.tsx`,
  `web/src/paginas/PaginaAdminInventario.test.tsx`.
- Nuevos: `web/src/paginas/PaginaAdminIndicadores.test.tsx`, este archivo, 2 PNG.
- Plan: entrada de ronda 120.

## Pendiente

- H6 (rol real en `/api/admin/sesion` + STAFF sin permisos); identidad por
  (subject, issuer); `/consulta` para logueados; comprobante descargable.
