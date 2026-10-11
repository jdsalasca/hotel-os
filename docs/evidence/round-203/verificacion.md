# Ronda 203 - Cada reserva dice a que hora es la entrada

## El hueco
«Mis reservas» mostraba la fecha de llegada y la de salida, y nada mas. El hotel ya
publicaba `hora_entrada` y `hora_salida` como datos administrables desde la ronda 14, pero
no aparecian en la unica pagina donde al huesped le sirven: la que revisa el mismo dia en
que llega. Tenia la fecha y ninguna hora.

Es el dato que evita el planton en recepcion, y era el ultimo hueco visible de la pagina.

## La decision que no era obvia
`reservations` ya trae sus propias columnas `hora_entrada` y `hora_salida`, congeladas al
reservar (ronda 180). La tentacion era pintar siempre el horario global del hotel, que es
una sola llamada y ya es publico.

**Eso seria una mentira**: si al huesped le prometieron «desde las 14:00» al confirmar,
mostrarle 15:00 es contradecir lo acordado. Asi que la hora congelada de la reserva manda,
y el horario del hotel es solo el respaldo cuando la reserva no trae el suyo.

## TDD rojo-verde
- `cada reserva dice a que hora es la entrada y la salida`: rojo
  `Unable to find an element with the text: Check-in desde las 15:00`.
- `si la reserva congelo su propia hora, esa gana sobre la del hotel`: rojo con
  `14:00` — la pantalla seguia enseñando el 15:00 global. Este es el test que fija la
  precedencia y sin el la pantalla habria mentido justo en el caso que mas importa.

Un rodeo mio durante el rojo: `sembrar()` en el fixture borra y reemplaza la lista, asi
que asignar la lista antes de llamar a `sembrar()` no hacia nada. Se corrigio aplicando la
hora sobre `estado.lista[0]` despues de sembrar.

## Verificacion visual
La siembra de la base local resulto inviable (el volumen SQLite del contenedor queda en
solo lectura al copiar el archivo por fuera: `SQLITE_READONLY`, y `room_types` noidia). Se
documenta como **criterio no comprobable por backend**: la verificacion visual se hizo
interceptando `/api/yo`, `/api/hotel` y `/api/mis-reservas` con respuestas simuladas, sobre
el componente real y el bundle construido. Lo medido es la UI, no la API.

Lectura de las capturas (`01-mis-reservas-escritorio.png`, `02-mis-reservas-movil.png`):

| Reserva | Hora propia | En pantalla |
|---|---|---|
| H-DEMO1 | ninguna | Check-in desde las 15:00 / Check-out hasta las 11:00 |
| H-PROPIA | 14:00 / 10:00 | Check-in desde las 14:00 / Check-out hasta las 10:00 |

- Sin desborde horizontal en 1440 px ni en 390 px.
- En movil la tabla colapsa a tarjetas y la hora se lee igual de clara bajo la fecha.
- El 1 error JS es el 401 de `/api/yo`, el sondeo de sesion que `useSesionHuesped` maneja con
  `if (!r.ok) return null`. Es esperado, no un defecto de esta ronda.

## Pruebas
- `PaginaMisReservas.test.tsx`: 16/16 (los 14 previos + los 2 nuevos).
- Frontend completo con `--no-file-parallelism`: **181/181**.
- `tsc --noEmit` y `test:typecheck` limpios; `vite build` correcto.

**Sobre la suite en paralelo**: con los 28 archivos en paralelo y Docker activo aparecen
timeouts de 5 s en `PaginaInicio` y `PaginaReserva`. Comprobado con `git stash` que
`develop` limpio **falla igual**, asi que es agotamiento de recursos del entorno, no un
defecto introducido aqui. Documentado en vez de oculto.

## Nota
`compose.ronda203.yaml` es andamiaje local de puertos para no chocar con el stack de la
R202 y **no va commiteado**.
