# Ronda 195 — saldo pendiente y estados de reserva

## Entrega

En «Mis reservas», se muestra un resumen de saldos pendientes para estadías próximas y en curso,
con una suma independiente por moneda. El resumen excluye estadías anteriores, canceladas,
rechazadas y no presentadas. Si no hay saldo pendiente ni datos incompletos, no ocupa espacio; si
faltan datos de pago o moneda, informa cuántas estadías no pudo incluir. `RECHAZADA` y
`NO_PRESENTADA` usan el tono visual de los estados finales. La API no cambió.

## Verificación automatizada

- TDD: antes de implementar, fallaron los casos de resumen, aviso por datos faltantes y tonos de
  los dos estados finales. Tras el cambio, la página quedó en **14/14** pruebas.
- `npm test -- --reporter=dot` sobre el `develop` actualizado: **27 archivos, 156/156 pruebas
  aprobadas** (incluye las pruebas integradas en R194 y R180 antes de esta ronda).
- `npm run test:typecheck`: aprobado.
- `npm run build`: aprobado; `tsc --noEmit` y Vite transformaron 71 módulos.
- La suite conserva dos mensajes esperados de pruebas existentes: el `Error: pum` del límite de
  errores y la advertencia de `window.scrollTo()` no implementado por jsdom; no causaron fallas.

## Revisión visual

Se abrió la versión local con una respuesta simulada para las rutas de sesión y reservas. Los
códigos, importes, fechas y huésped de la captura son sintéticos; no se usaron datos de huéspedes.

- Escritorio: viewport **1440 × 1000**; ancho del documento **1440 px**, sin desbordamiento
  horizontal. El resumen separa COP y USD y queda antes de la tabla.
- Móvil: viewport **390 × 844**; ancho del documento **390 px**, sin desbordamiento. La tarjeta
  distribuye los importes verticalmente y la tabla existente se presenta como tarjetas apiladas.
- La consola mostró el `401` esperado de `/api/admin/sesion`, usado para simular que no hay sesión
  de administrador; el flujo del huésped y las reservas cargaron correctamente.

Capturas de página completa:

- `escritorio.png`
- `movil.png`
