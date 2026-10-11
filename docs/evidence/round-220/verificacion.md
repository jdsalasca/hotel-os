# Ronda 220 — La consulta distingue errores

**Fecha:** 2026-10-11  
**Base:** `develop` en `ae6e90f6e61c53e792a20712511fceebe64b48cb`

## Hallazgo

`/consulta` convertía cualquier error del endpoint de comprobante en «No encontramos una reserva con ese código y ese correo». Un error 500, una demora o la falta de conexión culpaban incorrectamente al huésped y no le indicaban que podía reintentar.

## Cambio

- Si la API responde 404, se mantiene el mensaje indistinguible que protege la existencia de la reserva.
- Si el cliente detecta falta de conexión o demora (`ErrorApi` con estado 0), se muestra su instrucción segura para recuperar la consulta.
- Para otros errores, se informa que la consulta falló temporalmente; no se expone el texto arbitrario que haya enviado el servidor.
- El formulario conserva el código y el correo para que la persona pueda volver a intentarlo.

## Verificación

- **TDD rojo:** con la regresión agregada antes del cambio, 2 casos fallaron como se esperaba (503 y conexión); el caso 404 y las 7 pruebas previas pasaron.
- **Prueba específica:** `npm test -- src/paginas/PaginaConsulta.test.tsx` — 10/10.
- **Suite frontend:** `npm test` — 31 archivos, 207/207 pruebas. Vitest mostró el aviso de jsdom `Window's scrollTo() method` sin fallos.
- **Tipos de pruebas:** `npm run test:typecheck` — correcto.
- **Tipos de producción y compilación:** `npm run build` — `tsc --noEmit` y Vite correctos.
- **Diff:** `git diff --check` — correcto.
- **Recorrido real:** Chrome en `http://127.0.0.1:5175/consulta`, con fallo de red simulado solo en la petición del comprobante. El aviso llegó como `role="alert"`; código y correo permanecieron visibles. Consola sin excepciones de JavaScript; los dos 401 de consulta de sesión corresponden a la visita sin autenticar.
- **Escritorio:** 1440 × 1000; `scrollWidth` 1440.
- **Móvil:** 390 × 844; `scrollWidth` 390 y alto de documento 872 px.

## Capturas

- `consulta-escritorio.png`
- `consulta-movil.png`
