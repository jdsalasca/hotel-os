# Ronda 238 — Saldo visible en recepción

## Resultado

El parte administrativo de llegadas, salidas y huéspedes en casa presenta el abono acumulado, el saldo pendiente y la moneda usando la misma regla de saldo que el comprobante. La interfaz muestra `PAGADA` cuando no queda saldo y etiqueta el pendiente con la moneda configurada. Si la reserva no tiene precio y moneda acordados, no presenta un saldo ficticio.

La API añadió `abonadoCents`, `pendienteCents` y `moneda` a las filas de ocupación diaria cuando existe un precio acordado. El formato visible reutiliza el formateador monetario compartido.

## Verificación en el `develop` integrado

- API: `mvnw -f apps/api/pom.xml test` — 487 pruebas, 0 fallos, 0 errores, 1 omitida; `BUILD SUCCESS` (6:50). Registro completo: [api-full-postintegration.log](api-full-postintegration.log).
- Regresión focal de API: `OcupacionDiaTest`, 7/7.
- Frontend: `npm run test` — 33 archivos y 253 pruebas aprobadas.
- Tipos de aplicación: `npm run typecheck` — aprobado.
- Tipos de pruebas: `npm run test:typecheck` — aprobado.
- Build de producción: `npm run build` — aprobado, 76 módulos transformados.
- `git diff --check` — sin errores de espacios.

## Revisión visual

Revisión local en Chrome con datos sintéticos; no se usaron datos de producción. En escritorio (1440 × 1000) y móvil (390 × 844), las filas muestran los casos de saldo pendiente, pagada y reserva sin precio. No hubo desbordamiento horizontal ni errores de consola.

- [Captura de escritorio](escritorio.png)
- [Captura móvil](movil.png)

## Integración

El cambio funcional quedó integrado en `develop` mediante cherry-pick antes de ejecutar la suite completa posterior a la integración. El commit integrado es `7d810a9a91d4affb3beb05e62d7fb2fabaf32dfa` (`Muestra los saldos en el parte de recepción`). La verificación de GitHub confirmó el usuario `jdsalasca` y la identidad del autor del commit corresponde a JUAN DAVID SALAS CAMARGO.

Siguiente trabajo del plan: R213, outbox durable de correo. Su diseño está aprobado; el plan de implementación requiere aprobación explícita antes de empezar a programar. El tema claro/oscuro permanece aplazado hasta que el resto del producto esté terminado.
