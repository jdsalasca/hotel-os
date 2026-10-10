# Ronda 200 — Estados de lectura y navegación del chat

## Cambio

Cada mensaje propio indica si la otra parte lo leyó (`Visto por el hotel` / `Visto por el huésped`) o si aún espera lectura. Usa el campo `visto` que ya entrega la API. Al abrir el hilo y después de enviar se muestra el mensaje más reciente. Al actualizar mientras se revisan mensajes antiguos, se conserva la posición; al traer historia anterior, se mantiene el ancla visual. No hubo cambios en API ni base de datos. Las notificaciones proactivas y la actualización automática siguen pendientes.

## Pruebas

- TDD: antes de implementar, las pruebas de recibos fallaron por no mostrar estado; las pruebas de scroll fallaron por perder el ancla o no bajar al último mensaje. Después: `HiloMensajes.test.tsx` pasó con 9 casos.
- Suite frontend completa: `npm test -- --reporter=dot` — **27 archivos, 173 pruebas aprobadas, exit 0**. El resultado incluye ruido preexistente del test de límite de errores (`Error: pum`) y `jsdom` no implementa `scrollTo()`; no impidieron la aprobación.
- Chequeo de tipos de pruebas: `npm run test:typecheck` — **exit 0**.
- Build: `npm run build` — `tsc --noEmit` y Vite completados, **exit 0**.
- `git diff --check` — sin errores.

## Verificación visual

Chrome local con conversación sintética: en escritorio (1440 px) el documento conserva su ancho y el nuevo recibo queda visible tras enviar. En móvil (390 × 844 px), `document` y `body` mantienen 390 px; la lista conserva su ancho interno de 308 px sin desbordar la página. El envío muestra el estado pendiente. La consola de la página limpia no reportó errores ni advertencias.

- [Captura de escritorio](escritorio.png)
- [Captura móvil](movil.png)
- [Mediciones y consola](playwright-checks.txt)
- [Salida de la suite frontend](frontend-tests.txt)
- [Salida del chequeo de tipos](test-typecheck.txt)
- [Salida del build](build.txt)

La vista y el envío son un fixture con datos sintéticos; esto no acredita despliegue ni cambios en el servidor de producción.
