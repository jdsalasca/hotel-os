# Ronda 129 - Recibo imprimible en consulta y panel (Misión 1)

Fecha: 2026-10-08. Rama: `goal/round-129-recibo`.

## Misión (brainstorming, vía acotada)

Opciones: (a) recibo vía print nativo —bounded, cero deps—; (b) PDF con librería
(dep nueva por lo que el navegador ya hace); (c) calendario con arrastre
(alto/alto). Va (a): la misión 1 del plan sin inventar nada. El botón de
huésped ya existía (previo); faltaba el del panel y esconder lo que no es recibo.

## Cambio

- `PaginaAdminReservas.tsx`: botón "Imprimir comprobante" en el detalle;
  `no-imprimir` en filtros, tabla, formularios de acción, anular, chat, cerrar
  y alternar-nueva cuando hay detalle abierto (la lista y los botones no salen
  en papel; el recibo sí, con historial y cuenta).
- `PaginaConsulta.tsx`: el formulario se oculta en papel cuando hay comprobante.
- Sin CSS nuevo (convención `.no-imprimir` + `@media print` existentes).
- Tests: panel (botón imprime + lista oculta) y huésped (botón imprime + form
  oculto) en sus archivos.

## Verificación real

- Rojo→verde: 2/2 en archivos tocados (botón inexistente; form sin clase).
- Suite frontend: 18/19 archivos en verde; el único rojo es un test nuevo del
  colega en `PaginaInicio.test.tsx` ("hotel sin nada que vender…", su TDD en
  curso para hotel-vacío: intacto y fuera de mi commit, precedente ronda 81).
  Los 3 míos de ese archivo siguen verdes.
- `tsc --noEmit` y `tsc -p tsconfig.tests.json` exit 0.
- Visual en vivo con datos reales (H-D6948E80, origen OTRO, EUR 172): recibo de
  huésped con `@media print` emulado (formulario fuera, tarjeta completa) y
  detalle del panel en print (lista/filtros/chat/acciones fuera; historial con
  "creada como PENDIENTE por OTRO" y "→ CANCELADA por admin"). Capturas
  recibo-print, panel-detalle-screen y panel-detalle-print en esta carpeta.
- Backend sin tocar en la ronda.

## Trabajo en equipo

- Solo mis rutas en el commit (2 páginas + 2 tests + evidencia + plan). Lo del
  colega (OAuth/PuertaAdmin/hotel, rondas 124-125-128) intacto y fuera.
- `round-129/` nuevo y libre (la 125 sigue siendo suya en curso).

## Archivos

- Tocados: `web/src/paginas/PaginaAdminReservas.tsx`,
  `web/src/paginas/PaginaConsulta.tsx`,
  `web/src/paginas/PaginaAdminReservas.test.tsx`,
  `web/src/paginas/PaginaConsulta.test.tsx`.
- Nuevos: este archivo, 3 PNG. Plan: entrada de ronda 129.

## Pendiente

- H6; identidad por (subject, issuer); calendario con arrastre; expiración de
  pendientes (plazo del hotel); check-in/out.
