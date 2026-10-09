# Ronda 148 - El panel avisa si el respaldo se enfría

## Cambio
- `/api/admin/preparacion` trae el punto `respaldo`: hecho solo si hay una copia
  `hotel-*.sqlite3` de menos de 36 h en `${hotel.respaldos-dir:/backups}` (los
  respaldos corren a diario). Sin pantalla que lo arregle, el punto no lleva
  url: avisa en texto, no finge una puerta.
- `ListaPreparacion`: `url: string | null`; sin url pinta el título en texto en
  vez de reventar el `Link`. Existentes intactos (todos llevan url).

## TDD rojo-verde
- `PreparacionTest`: tamaño 5→6 + `respaldoReciente` (vacío→falso, fresco→cierto,
  viejo de 40 h→falso) fallan sin el punto; con él, 2/2. Cazó orden-dependencia
  (base compartida): limpieza al inicio de cada lado.
- `ListaPreparacion.test`: url nula revienta el `Link` sin el guardián; con él,
  4/4.

## Verificación (salida real, 2026-10-09)
- Backend: `PreparacionTest` 2/2; suite 435/440 (5 fallos SOLO en TDD en rojo
  del colega: `HuespedFechasTest` 1 + `AdminReservasControllerTest` 4, cero
  errores, cero fallos míos).
- Frontend: 22 archivos / 80 tests en verde; `tsc --noEmit` exit 0.
- En prod los respaldos están FRESCOS (último hace minutos, bucle diario vivo):
  el punto saldrá hecho. El panel exige sesión: sin captura de navegador, la
  prueba es el contrato (`clave/hecho/url:null`) en tests.

## Despliegue
- Merge a `develop`, push y `compose up -d --build` en TopNUC con health OK.
