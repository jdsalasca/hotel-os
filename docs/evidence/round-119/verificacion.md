# Ronda 119 - El panel ignora las lecturas viejas (Etapa E)

Fecha: 2026-10-08. Rama: `goal/round-119-panel-vigente`.

## Misión (brainstorming, vía acotada)

Opciones: (a) guardas de vigencia en el panel —bounded, mismo patrón ya probado
en búsqueda y parte diario—; (b) comprobante descargable (zona activa del colega);
(c) calendario con arrastre (alto/alto). Va (a): E10 podía tarifar sobre otro plan.

## Problemas (reproducidos con tests antes de afirmar)

- **E10.** Tarifas sin guarda: cambiar plan/mes/tipo con la anterior en vuelo
  pintaba las noches viejas, y `cambiosDelMes()` compararía contra el plan
  equivocado al guardar el lote. Rojo: input con `100` del plan viejo tras pedir
  el nuevo (`200`); mes nuevo vaciado a `''` por el viejo.
- **E11.** `elegirTipoServicios` sin guarda ni bloqueo: A→B→A dejaba los marcados
  de B. Rojo: casilla Desayuno marcada con el tipo B en pantalla.
- Lección de test: afirmar "sigue el valor nuevo" con `waitFor` da falso verde (la
  primera pasada aún ve lo viejo); se resolvió la tardía, se esperaron 300 ms y
  recién se afirmó. Igual que en producción: la vigencia se juzga DESPUÉS.

## Cambio (`PaginaAdminInventario.tsx` + test nuevo)

- Contadores `peticionTarifas` / `peticionServicios` (mismo patrón de la búsqueda):
  respuesta vieja se ignora en éxito y en error; limpiar el plan también invalida
  lo en vuelo. E9 (`cargar` por mes) queda documentado para la próxima ronda.
- Test `PaginaAdminInventario.test.tsx` (3, promesas diferidas con orden invertido).

## Verificación real

- Rojo→verde: 3/3 en el archivo (rojo verificado contra el código sin fix vía
  `git stash`); suite frontend **14 archivos / 48 tests**; `tsc --noEmit` y
  `tsc -p tsconfig.tests.json` exit 0.
- Visual en vivo (stack dev reconstruido con el cambio): login admin, inventario
  con datos reales (bloqueos + calendario), grilla de octubre del plan Flexible
  con 31 noches `Fijada`. Capturas desktop y móvil 390px en esta carpeta. Sin
  cambios visuales (la guarda no pinta nada nuevo): regresión intacta.
- Backend sin tocar en la ronda.

## Trabajo en equipo

- Solo `PaginaAdminInventario.tsx` + su test en el commit (el colega va en
  comprobante/chat: intactos). `round-119/` nuevo y libre.
- Incidente del colega leído (R117: su `git add` barrió mi handler a medias y
  rompió su deploy TS1345 hasta mi merge): de mi lado, `git add` siempre por
  rutas explícitas + diff revisado antes del commit.

## Archivos

- Tocados: `web/src/paginas/PaginaAdminInventario.tsx`.
- Nuevos: `web/src/paginas/PaginaAdminInventario.test.tsx`, este archivo, 2 PNG.
- Plan: entrada de ronda 119.

## Pendiente

- E9 (`cargar()` por mes) + E8 (indicadores por período) con el mismo patrón;
  H6 (rol real en `/api/admin/sesion`); identidad por (subject, issuer).
