# Ronda 103 - El chat no se deja inundar

Fecha: 2026-10-08.

## Diseño (brainstorming, vía acotada)
El hilo aceptaba mensajes sin límite: un bucle llena la base. Opciones: (a) reusar
`LoginThrottle` (límites y contadores equivocados para conversar), (b) tope por
reserva y hora contado en la base (sobrevive reinicios, exacto), (c) rate limit en
Cloudflare (clic del dueño, no código). Va (b): 30/hora sumando ambos lados; el front
muestra el mensaje tal cual, sin cambios allá.

## Verificación real (TDD)
- Test `topePorHoraYReserva`: 30 insertados → 31º 429 con "minuto" en `$.error`.
  Primero falló con 201 (sin tope), luego con 429 sin cuerpo JSON (excepción en vez de
  `ResponseEntity`); verde tras responder como el resto de la API.
- Vecinas de huésped: **11/11, BUILD SUCCESS**.
- Despliegue + health `ok`; el 429 real no se provoca en prod a propósito.
- Archivos del otro agente intactos.

## Archivos
- Tocados: `ChatRepository.java`, `ChatController.java`, `ChatTest.java`.
