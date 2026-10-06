# Ronda 11 — Calendario de ocupación noche por noche

Fecha: 2026-10-06. Rama `develop`.

## Entregable

El panel tiene un calendario mensual de ocupación con una fila por habitación y una casilla por
noche. Cada noche dice `LIBRE`, `OCUPADA`, `BLOQUEADA`, `MANTENIMIENTO` o `FUERA_DE_SERVICIO`. Las
noches ocupadas llevan el código de la reserva; no llevan nombres ni correos de huéspedes.

El intervalo es semiabierto: el día de salida cuenta como libre. Una reserva del 1 al 3 ocupa las
noches del 1 y del 2; el 3 queda libre.

## Separación de capas

La pregunta era si el controlador mezclaba lógica de servicio. Respuesta con código, no con
opinión:

- El servicio (`InventarioService.ocupacion`) decide el estado de cada noche y la prioridad.
- El repositorio devuelve filas crudas de reservas vigentes y bloqueos.
- El controlador solo adapta transporte: convierte parámetros y traduce errores a HTTP.

Por eso se eliminó el ayudante `fechas()`: lanzaba `DatosInvalidosException`, del dominio, para
un fallo de parseo HTTP. Ahora Spring convierte `String` a `LocalDate` con `@DateTimeFormat`.

## Defectos encontrados y corregidos

1. **Los GET nuevos respondían 201.**
   `ok()` es una política de escritura. Se dividió en `ok()` para crear y `lectura()` para leer.
   Prueba: `Status expected:<200> but was:<201>` pasó a 200.

2. **Una fecha mal escrita parecía fallo de sesión.**
   Spring resolvía el parámetro como 400, pero el reenvío interno a `/error` estaba denegado y la
   respuesta final era 403. Se permitió solo `DispatcherType.ERROR`, sin abrir ningún endpoint.
   Antes: `fecha mala: 403`. Después: `fecha mala: 400`.

3. **El panel hacía N consultas públicas por habitación para decir “mes completo”.**
   Esa columna desapareció. Ahora hay una llamada a `/api/admin/calendario`, que distingue reserva
   vigente, bloqueo, mantenimiento y habitación retirada.

## Verificación real

```text
.\mvnw.cmd test
Tests run: 150, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

```text
npx tsc --noEmit
TSC_SIN_ERRORES
```

```text
docker compose config --quiet
compose dev: exit=0
docker compose -f compose.production.yaml config --quiet
compose prod: exit=0
```

```text
docker compose ... capturas
capturas completas sin errores de consola ni de API
```

HTTP contra contenedores reales:

```text
calendario bueno: 200
GET tarifas: 200
fecha mala: 400
sin sesión: 401
```

Cobertura del calendario en servicio:

- Reserva confirmada ocupa las noches 1 y 2, pero no el día de salida.
- Reserva cancelada no retiene la habitación.
- Bloqueo por habitación y bloqueo global se aplican a las noches correctas.
- Habitación retirada aparece como no vendible todas las noches.
- Periodo invertido o mayor de un año se rechaza.

## Interfaz verificada a mano

- `docs/screenshots/escritorio-07c-calendario.png`: cuadrícula completa, fines de semana
  sombreados y noches ocupadas marcadas en rojo con forma sólida.
- `docs/screenshots/movil-07c-calendario.png`: la región conserva desplazamiento horizontal sin
  scroll lateral de la página; el guion centra la primera ocupación antes de capturar.
- Marcas distintas por forma además de color: círculo hueco, círculo sólido, cuadrado y cruz.
- Cada celda tiene nombre accesible con habitación, fecha, estado y código de reserva.
- La leyenda usa las mismas formas y el resumen cuenta noches ocupadas, bloqueadas y no vendibles.

## Límite conocido para la ronda 12

Entrada mal escrita en cuerpos POST todavía puede responder 500:

```text
bloqueo fecha mala: 500
tarifa fecha mala: 500
reserva pública sin roomId y fecha mala: 500
```

El fallo está documentado y reproducido, no declarado como corregido.