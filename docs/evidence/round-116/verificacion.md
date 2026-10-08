# Ronda 116 - Sesiones honestas: rotación al entrar, verdad al salir (Etapa G)

Fecha: 2026-10-08. Rama: `goal/round-116-sesiones`.

## Misión (brainstorming, vía acotada)

Opciones: (a) H5+H4 sesiones —bounded, seguridad, cerrable—; (b) comprobante
descargable (medio/medio, >90 min con QA); (c) calendario con arrastre (alto/alto).
Va (a): cierra dos hallazgos del revisor con pruebas negativas exactas.

## Problemas (reproducidos antes de afirmar)

- **H5.** El login manual no rotaba el `JSESSIONID`: la estrategia de sesión del
  filtro nunca corre en el controlador, así que un id plantado antes del login
  seguía válido autenticado (fijación). Test nuevo con HTTP real: re-login con la
  cookie vieja → **el segundo login no emitía JSESSIONID: no hubo rotación**.
- **H4.** `salir()` declaraba el cierre aunque el servidor lo rechazara (403/500) o
  ni respondiera: la cookie seguía válida y la UI lo negaba. Tests nuevos
  (5, panel + huésped): `expected undefined to be true/false` y el rechazo de red
  propagándose sin manejar.

## Cambio

- `AdminAuthController.login`: `getSession(true)` + `changeSessionId()` tras
  autenticar (el camino `cambio_requerido` sigue sin abrir sesión). 2 líneas.
- `useSesion().salir()` y `useSesionHuesped().salir()`: `Promise<boolean>` — true
  solo con `r.ok`; false con 403/500, excepción de red o desmontaje, sin tocar el
  estado. Sin rechazos sin manejar.
- `App.tsx` (`CierreSesionPanel`): solo navega a `/admin/entrar` con true; con false
  muestra `nav__error` (nueva clase SCSS con tokens `$error`/`$texto-sm`,
  `role="alert"`).
- `PaginaMisReservas.tsx`: con false, `setError(...)` sobre el `MensajeError`
  existente. (Archivo compartido con la ronda 117 del colega: sus hunks de
  comprobante intactos; solo mi `onClick`.)

## Verificación real

- Backend `CookieSesionTest`: **3/3** (rotación: id nuevo ≠ viejo, viejo 401, nuevo 200).
- Frontend: **12 archivos / 42 tests en verde**, `tsc --noEmit` y `tsc -p
  tsconfig.tests.json` exit 0, `vite build` exit 0.
- Visual en vivo (stack dev reconstruido con el cambio, `web` reiniciado; `api`
  intacta): login admin/admin → `/admin/hoy`, logout interceptado a 403 → aviso
  `No se pudo cerrar la sesión: sigue abierta.` junto al botón, sin redirigir y con
  sesión válida (ruta protegida sigue entrando). Capturas desktop 1440 y móvil 390
  en esta carpeta. Ruta feliz: sin intercepción, salir lleva a `/admin/entrar`.
  (Los 401 de `/api/admin/sesion` y `/api/yo` en consola anónima son los sondeos
  iniciales esperados, no regresión.)

## Entorno (bloqueos reales, documentados)

- `node_modules` del host llegó sin el binario de rollup (`@rollup/...-msvc`
  declarado en lock pero ausente) y con workers de vitest colgados: reparado con
  `npm install --no-save` del binario exacto (manifiestos intactos). Tras una
  actividad npm concurrente el árbol quedó a medio escribir (faltaban jsdom,
  vitest y `typescript/lib`): renombrado a un lado y `npm ci` limpio (150
  paquetes, exit 0), luego eliminado el resto. Lección: un solo `npm` a la vez
  en la copia compartida.
- Vitest corre con `--pool=forks`: el pool de hilos se cuelga en este host cuando
  el árbol está roto; con el árbol sano el pool por defecto vuelve a ir.

## Trabajo en equipo

- La ronda 117 del colega (comprobante propio) barrió mi `onClick` de MisReservas
  al commitear y tomó el número 115 yendo yo ya pusheado; lo resolvió él mismo
  (115 mío intacto, suyo a 117). Yo restauré solo mi hunk y verifiqué de nuevo
  (`tsc` ×2 + salir 5/5). Solo mis rutas en mi commit.
- `docs/evidence/round-116/` solo trae lo mío (2 PNG + este archivo).

## Archivos

- Tocados: `admin/AdminAuthController.java`, `config/CookieSesionTest.java`,
  `web/src/api/useSesion.ts`, `web/src/api/useSesionHuesped.ts`,
  `web/src/App.tsx`, `web/src/paginas/PaginaMisReservas.tsx`,
  `web/src/styles/_componentes.scss`.
- Nuevos: `web/src/api/salir.test.tsx`, este archivo, 2 PNG. Plan: entrada 116.

## Pendiente

- H6 (rol `ADMIN` hardcodeado + STAFF sin permisos), identidad por
  (subject, issuer) (H2 de fondo). Misión candidata del plan: `/consulta` para
  logueados (reusa R115), comprobante descargable.
