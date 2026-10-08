# Ronda 111 - WhatsApp con la reserva ya escrita

Fecha: 2026-10-08.

## Diseño (brainstorming, vía acotada)
La duda nace al confirmar ("¿a qué hora llego?") y el chat solo sirve con sesión.
Opciones: (a) botón wa.me con código precargado en la confirmación (cero backend, cero
deps, funciona en móvil y desktop), (b) chat anónimo (cuentas fantasma, spam), (c) solo
tel: (sin contexto). Va (a): `enlaceWhatsApp()` (móvil CO de 10 dígitos → 57, resto se
respeta) + `BotonWhatsApp` que se esconde sin teléfono configurado.

## Verificación real (TDD)
- `formato.test.ts` primero en rojo (5 fallos, sin export); con el helper, verde.
- `BotonWhatsApp.test.tsx`: enlace con código + invisible sin teléfono.
- **33/33 vitest en 10 archivos**, tsc con tests limpio.
- Despliegue + health `ok`; el botón con teléfono real se ve con datos del hotel.
- Archivos del otro agente intactos.

## Archivos
- Nuevos: `BotonWhatsApp.tsx`, `BotonWhatsApp.test.tsx`, `formato.test.ts`, este archivo.
- Tocados: `formato.ts`, `PaginaReserva.tsx`.
