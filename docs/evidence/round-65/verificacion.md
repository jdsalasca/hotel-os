# Ronda 65 - El admin también reserva

Fecha: 2026-10-07.

## Dolor
El personal con sesión del panel que reservaba quedaba en el limbo: su reserva no se
enganchaba a nadie y "Mis reservas" le pedía entrar con Google estando ya dentro.

## Cambio
- Al reservar con sesión ADMIN y el mismo correo, se crea su identidad de huésped
  (`panel:{correo}`) y la reserva queda vinculada: la ve en Mis reservas sin perder el panel.
- Si reserva para otro correo (recepción), no se engancha a nadie: sigue consultable por
  código + correo, como siempre. Sin sesión, nada cambia.
- `/api/yo` y `/api/mis-reservas` con sesión pero sin fila responden vacío (200), no 401:
  ya está dentro, solo que aún no tiene nada. Sin sesión sigue siendo 401 desde el filtro.
- Sin frontend nuevo: el flujo público, Mis reservas y el saludo ya sirven a ambos roles.

## Verificación real
- `AdminReservaPropiaTest` nuevo (propia se ve + sub `panel:`, ajena no se engancha, anónima
  intacta): verde. Vecinas `HuespedGoogleTest` + `ReservaVinculadaTest`: 14/14.
- Despliegue: `git reset --hard origin/develop` + `up -d --build`; health `ok`.
- Archivos del otro agente intactos: `ReservaController` y `HuespedController` no los toca;
  `ReservaServiceHuesped` es archivo propio de la R61.

## Archivos
- Tocados: `ReservaServiceHuesped.java`, `ReservaController.java`, `HuespedController.java`.
- Nuevo: `AdminReservaPropiaTest.java`, este archivo.
