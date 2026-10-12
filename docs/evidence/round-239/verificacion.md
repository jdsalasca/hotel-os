# Ronda 239 — Identificación consistente de moneda en importes del huésped

## Cambio

El recibo de «Mis reservas» formateaba dinero por separado y mostraba `$ 200`, aunque la
tabla y el resumen compartido identificaban el importe como `COP 200`. Ahora el recibo usa
el mismo `monto` de `api/formato.ts`. Ese helper acepta valores opcionales y devuelve `null`
si falta el importe o la moneda; el recibo conserva sus textos «Sin precio» y «—» en esos
casos. También se retiró la envoltura redundante de «Mis reservas».

## Verificación automatizada

- **TDD:** antes del cambio fallaron las regresiones por importe ausente (`COP 0`) y por el
  símbolo ambiguo (`$ 200`); después, ambas pasaron.
- Suite frontend completa: **32 archivos, 253/253 pruebas aprobadas**.
- `npx tsc --noEmit -p tsconfig.tests.json`: aprobado.
- `npm run build` antes de integrar: aprobado; `tsc --noEmit` y Vite transformaron **76
  módulos** en 3 min 51 s.
- Después del merge en `develop`: suite frontend **253/253**, chequeo de tipos de pruebas y
  `npm run build` aprobados. El build transformó los mismos **76 módulos** y tardó 4 min 46 s;
  el bundle principal quedó en 406,91 kB (116,48 kB gzip).
- La salida de pruebas conserva avisos ya presentes: clave repetida `/admin/hotel` en una
  prueba del panel, `window.scrollTo()` no implementado por jsdom y el error deliberado
  `pum` del test del muro de errores. No impidieron las 253 aprobaciones.

## Revisión visual

Se revisó la ruta local `/mis-reservas` con respuestas sintéticas para sesión, hotel, reserva y
comprobante. El nombre, correo, código, habitación, fechas y valores son ficticios.

- **Escritorio:** viewport 1440×1000; ancho de contenido y documento 1425 px. El comprobante
  muestra `COP 200 · Estándar` y `Abonado: COP 100 · Pendiente: COP 100`.
- **Móvil:** viewport 390×844; contenido y documento 390 px. Los mismos importes quedan legibles
  en la tarjeta sin desbordamiento horizontal.
- La consola del navegador no mostró errores ni advertencias; solo los mensajes informativos de
  Vite y React DevTools.

Capturas de página completa:

- [Escritorio](escritorio.png)
- [Móvil](movil.png)
