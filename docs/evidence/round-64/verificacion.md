# Ronda 64 - Chat huésped ↔ hotel por reserva

Fecha: 2026-10-07.

## Dolor
Sin forma de hablar: el huésped con dudas llamaba o desistía, y el hotel no tenía dónde
responder dentro de la reserva.

## Cambio
- Migración V15: `mensajes` (reserva, autor HUESPED/HOTEL, texto ≤1000, visto). Sin borrado:
  la conversación es historial de la reserva.
- Huésped (solo sus reservas, 404 si no es suya): hilo + envío en Mis reservas con botón
  "Mensajes (N)"; contadores leídos al abrir (el otro lado se marca visto).
- Panel: bloque de conversación en el detalle de la reserva + `GET /api/admin/mensajes/nuevos`.
- Componente compartido `HiloMensajes` (propio a la derecha) y parcial SCSS `_chat.scss`;
  nada de CSS suelto ni inline.

## Verificación real
- `ChatTest` nuevo (ida y vuelta, ajeno 404, vacía/kilométrica 400, sin sesión 401,
  contadores que se apagan al leer): verde.
- `tsc` + `vite build` en el build Docker del servidor.
- Despliegue: `git reset --hard origin/develop` + `up -d --build`; V15 aplicada; hilo
  servido en bundle; el chat con sesión se prueba con cuenta real (ver nota).
- Archivos del otro agente intactos: paquete nuevo `co.hotel.chat`, `SecurityConfig` solo
  amplía un matcher, páginas tocadas fuera de inventario.

## Archivos
- Nuevos: `V15__chat_reserva.sql`, `ChatRepository.java`, `ChatController.java`,
  `ChatTest.java`, `HiloMensajes.tsx`, `_chat.scss`, este archivo.
- Tocados: `SecurityConfig.java`, `PaginaMisReservas.tsx`, `PaginaAdminReservas.tsx`,
  `indice.scss`.
