# Ronda 175 - El huésped ve las condiciones antes de confirmar

## El hueco
El hotel configura `hora_entrada`, `hora_salida` y `politica_cancelacion` desde el panel
(`POST /api/admin/hotel-config`), y el backend los publica en `GET /api/hotel`. Pero el
frontend **no llamaba a `/api/hotel` en ningún sitio**: el huésped no veía las condiciones
del hotel hasta llegar a `/terminos`, en el pie, o sea **después de decidir**.

En reserva directa esa es la pregunta que más mata la conversión ("¿y si quiero
cancelar?"), y es la que un huésped sí se hace antes de confirmar. Los sitios líderes del
dominio la muestran siempre en el paso de confirmación.

## Cambio
- `PaginaReserva`: pide `/api/hotel` al montar y pinta un bloque **"Antes de confirmar"**
  en la tarjeta del resumen, junto al precio y antes del botón. Si el hotel no ha
  configurado nada, el bloque no aparece: no se inventa ninguna condición.
- Un fallo al leerlas no impide reservar (son información, no requisito) y no muestra nada.
- `condiciones` en `_componentes.scss` con los tokens del proyecto: `$ocre` de filete,
  `$blanco-fachada` de fondo, `$tinta-suave` (6.9:1) para el texto y `$tinta-tenue`
  (3.6:1) solo para las etiquetas. Sin CSS suelto ni inline.
- Backend intacto: `/api/hotel` ya servía los datos.

## TDD rojo-verde
- `PaginaReserva.test.tsx`: sin el cambio FALLA con
  `Unable to find role="region" and name /condiciones/i`; con él 7/7.
- Los tres casos: se ven las condiciones configuradas, **no se inventa nada** si el hotel
  no configuró, y el bloque existe como región accesible (lo puede leer un lector).

## Verificación (real, 2026-10-09, contenedores)
- Frontend: `npm run test` **115/115**, `tsc --noEmit` y `test:typecheck` sin errores,
  `vite build` ok (372 kB / CSS 22 kB).
- Chrome contra el stack con datos reales: el bloque renderiza
  `Check-in y check-out desde las 15:00 · hasta las 12:00` y la política del hotel.
- `erroresJS=0`; móvil 390 px **sin desborde horizontal**.
- Capturas: `01-reserva-condiciones.png` (escritorio completo), `02-bloque-condiciones.png`
  (el bloque de cerca), `03-movil-condiciones.png`.

## Riesgo / limitación
- El texto de cancelación es **el que configuró el hotel**, palabra por palabra. No se
  reescribe ni se recorta: si el hotel escribe mal, se muestra mal. Es lo correcto (no
  inventar reglas comerciales), pero conviene que el panel guíe al escribirlo.
- Duplicación visible: la etiqueta "Cancelación" y el texto del hotel empiezan igual
  ("Cancelación gratuita hasta..."). No se recorta el texto del hotel por eso.
- Sin tocar archivos de recepción ni estilos compartidos: la ronda corre en paralelo con
  el trabajo de recepción de otra agente.

## Lo que sigue
- Las condiciones no se congelan en la reserva: si el hotel cambia la política después, el
  huésped ve la nueva en su próxima visita. Congelarlas exige decidir qué campos forman
  "las condiciones acordadas" (cuyo·importe ya se congela). Es la ronda siguiente.