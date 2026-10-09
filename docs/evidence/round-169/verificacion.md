# Ronda 169 - El huésped cambia su grupo sin llamar al hotel

## El hueco
La ronda 166 dejó `POST /api/mis-reservas/{codigo}/huespedes` probado en el servidor
(dueno cambia / ajena 404 / sin sesion 401), pero `PaginaMisReservas` no lo exponia:
el huesped que llega con mas gente de la que habia reservado tenia que llamar a
recepcion. Es la mitad "self-service" de la reserva directa.

## Cambio
- `PaginaMisReservas`: boton "Cambiar huespedes" por fila en reserva vigente, y un
  formulario con el grupo actual precargado. Mismo esqueleto que el de "Cambiar
  fechas" que ya habia (`abriendoGrupo` guarda codigo + valor tecleado). Al guardar
  se relee la lista para mostrar lo que quedo guardado de verdad, no lo tecleado.
- Sin estilos nuevos: reutiliza `boton--fantasma--chico`, `tarjeta`, `campo`.
- Sin cambios en el servidor: el endpoint ya existia de la ronda 166.

## TDD rojo-verde
- 2 tests nuevos en `PaginaMisReservas.test.tsx`:
  1. abre el formulario con el grupo actual (1), envia `{huespedes: 2}` a la ruta
     propia, y la fila releida muestra 2 (no lo tecleado a ciegas);
  2. si el servidor rechaza por capacidad, avisa y la fila sigue con 1.
- El mock de `post` se ramifica por URL para no romper el test de fechas existente.
- Verde: 4/4 en el archivo, 111/111 en el frontend.

## Verificacion (2026-10-09)
- Frontend: `npm run test` 111/111; `typecheck` y `test:typecheck` sin errores;
  `vite build` ok (371 kB).
- Backend (de la ronda 166, ya verde): `HuespedFechasTest` cubre dueno / ajena 404 /
  sin sesion 401; `AdminReservasControllerTest` cubre capacidad 409 y cerrada 409.
- Backend completo: 444 tests, 0 fallos, 1 omitida.

## Lo NO verificado (honesto)
- **No hay verificacion visual en navegador de este formulario**: entrar como
  huesped requiere el flujo OAuth2 de Google, que necesita `GOOGLE_CLIENT_ID` /
  `SECRET` (dependencia externa, no disponible en este entorno y fuera del alcance
local). El unico login demo es el de admin (`HOTEL_DEMO_ADMIN`), que no entra por
  `/mis-reservas`.
- El comportamiento del formulario esta cubierto por tests de componente con el
  cliente HTTP simulado (envio correcto, recarga de lista, aviso al rechazar), pero
  **no por una pasada real en Chrome**. Queda pendiente para cuando haya credenciales
  de OAuth configuradas.

## Lo que sigue abierto
- El huesped puede mover fechas y cambiar huespedes; no puede **anular** un abono ni
  pedir una factura; el cobro es solo del lado del hotel (correcto por diseno: el
  dinero entra por el hotel, no por el huesped).
- Roundtrip completo del huesped con Google real: bloqueado por credenciales.