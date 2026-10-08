# Ronda 136 - Al entrar se recarga y el salir vive en la cabecera

## Reporte
El dueño entra con contraseña y llega al panel SIN saludo y SIN botón de salir
visible (captura del reporte). El salir existía, pero enterrado en el pie tras
una página larga.

## Causa raíz
- El saludo montado en `/admin/entrar` (sin sesión) nunca volvía a preguntar:
  al entrar con clave la app solo navegaba (SPA) y el "sin sesión" quedaba
  clavado hasta recargar a mano.
- El salir vivía solo en el pie (`CierreSesionPanel` en el footer): con sesión
  válida sí existía, pero había que bajar toda la página para verlo.

## Cambio
- `PaginaLoginAdmin`: tras entrar (clave o cambio de clave), recarga completa a
  `/admin` en vez de navegar: estado fresco garantizado en cabecera y páginas,
  igual que ya hacía el salir hacia `/admin/entrar`.
- `CierreSesionPanel` (ahora exportado) junto al saludo en la cabecera
  (`cabecera__sesion` + `cabecera__salir` con tokens, en `_componentes.scss`);
  fuera del pie. Se borró el `.nav__salir`/`.nav__error` muerto.
- El contacto del vacío (R133 en curso) sigue en la rama, sin commitear.

## TDD rojo-verde
- `PaginaLoginAdmin.test`: "recarga al panel con estado fresco" falla sin el
  cambio (navega, no asigna); con él, 3/3.
- `SaludoSesion.test` + `CierreSesionPanel`: presencia con sesión, ausencia sin
  ella, clic que POSTea `/api/admin/logout` y redirige a `/admin/entrar`.

## Verificación (salida real, 2026-10-08)
- Frontend: `vitest` 20 archivos / 71 tests en verde (pool limitado por memoria).
- `tsc --noEmit` y `tsc -p tsconfig.tests.json`: exit 0.
- Backend: 5 fallos SOLO en archivos en vuelo del colega (`HuespedFechasTest` 1,
  `AdminReservasControllerTest` 4: su TDD en rojo, endpoints aún no commiteados).
  Ningún fallo en archivos míos; esta ronda no toca backend.
- Visual: el saludo+salir exigen sesión (no automatizable sin credenciales);
  jsdom afirma presencia, orden y redirecciones. Captura post-deploy del panel
  sin sesión (sin regresión) — ver `panel-sin-sesion.png`.

## Despliegue
- Merge a `develop`, push y `compose up -d --build` en TopNUC con health OK.
