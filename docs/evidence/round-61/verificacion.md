# Ronda 61 - Mis reservas muestra lo pagado y lo pendiente

Fecha: 2026-10-07.

## Dolor
"Mis reservas" no decía si la reserva estaba paga: solo el total acordado, sin abonos.

## Cambio
- `ReservaServiceHuesped.de()` trae por fila `abonado_cents` (suma de pagos vigentes, el
  anulado no suma) y `pendiente_cents` (total menos abonos; NULL si no hay precio acordado).
  El saldo se calcula al leer, igual que el comprobante del panel; sin migración.
- `PaginaMisReservas`: columnas Abonado y Pendiente; `PAGADA` en verde cuando el pendiente
  llega a cero; "Sin precio" cuando no hay total acordado.

## Verificación real
- `MisReservasPagosTest` nuevo: abonos suman, anulado excluido, sin abonos pendiente = total,
  sin precio pendiente NULL. Verde.
- Vecinas de huésped/pagos por correr en el deploy (build Docker compila front+back).
- Despliegue: `git reset --hard origin/develop` + `up -d --build` en TopNUC; health `ok`;
  columnas visibles en `/mis-reservas` con sesión.
- Archivos del otro agente intactos: solo se commitearon los de esta ronda.

## Archivos
- Tocados: `ReservaServiceHuesped.java`, `PaginaMisReservas.tsx`.
- Nuevo: `MisReservasPagosTest.java`, este archivo.
