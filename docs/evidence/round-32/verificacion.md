# Ronda 32 — Los estados de una reserva tienen reglas, y nadie las había escrito

## Lo que hacía el panel

El panel de reservas ofrecía siempre los mismos tres botones —Confirmar, Cancelar, Rechazar—
menos el que ya era el estado. El servidor, por su parte, aceptaba **cualquier** estado nuevo sin
mirar el anterior:

```java
repo.actualizarEstado(codigo, nuevo);   // sin mirar actual.estado()
```

Eso significa que una reserva CANCELADA se podía volver a CONFIRMADA desde el panel. Y no es un
dato mal puesto: al volver a CONFIRMADA la reserva vuelve a ocupar la habitación (`vigente()`), con
lo que **el hotel se queda sin vender una fecha que ya había liberado**, porque el huésped ya se fue
y se la llevó otro.

## Las reglas, en el dominio

| Desde | Puede pasar a |
|---|---|
| PENDIENTE | CONFIRMADA, CANCELADA, RECHAZADA |
| CONFIRMADA | CANCELADA |
| CANCELADA | nada: es final |
| RECHAZADA | nada: es final |

Cerrar es final a propósito. Si el hotel se equivocó al cancelar, lo correcto es una reserva nueva,
que además tiene su propio código e historial, en vez de resucitar una que el huésped ya dio por
terminada. Y CONFIRMADA no vuelve a PENDIENTE porque desconfirmar no es una operación: el huésped
ya tuvo su confirmación.

Intentarlo devuelve **409** con el motivo, no un 500:

```text
no se puede pasar de CANCELADA a CONFIRMADA: ya está cerrada, crea una reserva nueva
no se puede pasar de CONFIRMADA a RECHAZADA
```

Un 400 sugeriría que el hotel escribió mal el estado y que reintentando vale.

## Dos bugs que aparecieron al escribir las pruebas

### RECHAZADA no se podía guardar: el CHECK de la base no la conocía

El enum Java lleva RECHAZADA desde V1, pero **V6 reconstruyó la tabla** para cambiar la clave de
idempotencia y en la copia se perdió del CHECK. Desde entonces rechazar una reserva era un 500 con
un `CHECK constraint failed`, no un estado. Nadie lo notó porque el camino feliz no lo tocaba.

Lo devuelve **V8**, que reconstruye la tabla con los cuatro estados.

### Y mi V8 se comió `usuario_id`, que había añadido V7

Al escribir V8 copié el esquema de V6, que es anterior a V7. Seis tests lo detectaron al perder la
columna: la tabla `usuarios` seguía ahí, pero `reservations` ya no colgaría de nadie. Corregido
copiando la columna y su índice.

Flyway, por su parte, se negó a arrancar después: había aplicado mi V8 roto a la base local y no
acepta que una migración aplicada cambie. Hizo bien. Como V8 nunca se commiteó ni se desplegó, la
única base afectada era la de desarrollo: se apartó como `hotel-roto-v8-sin-usuario_id.sqlite3` y
se reconstruyó desde cero. Flyway aplicó las 8 migraciones limpias:

```text
Successfully applied 8 migrations to schema "main", now at version "v8"
```

## Un límite que estaba a un test de romperse

`AdminReservasControllerTest` crea una reserva por test, y el límite público es de 10 por IP cada 15
minutos. La clase tenía **exactamente** 10 tests: añadir cuatro la tiró. El tope era
`private static final int MAX_POR_IP`, sin forma de ajustarlo.

Ahora es `hotel.limites.reservas`, con 10 por defecto. No es una abstracción para el futuro: en un
resort todos los huéspedes salen por la misma IP, y con diez de tope la tercera casa del pueblo que
reserva se encuentra un 429 sin haber hecho nada malo. La clase de test lo sube a 200; su propio
test del límite sigue comprobando que el 10 real corta.

## Los botones los decide el servidor

Mi primera versión de esto copió las reglas en TypeScript, y al mirarla ofrecía "Rechazar" en una
CONFIRMADA que el servidor ya iba a rechazar con 409. Dos listas de transiciones en dos lenguajes
se desincronizan; el CHECK de la base y el enum ya habían divergido una vez en este proyecto.

Así que el panel no deduce nada: la respuesta del servidor trae `siguientes`, y los botones son
exactamente los que hay en esa lista. Solo el texto de cada botón vive en el navegador.

```json
{ "estado": "CANCELADA", "siguientes": [] }
{ "estado": "CONFIRMADA", "siguientes": ["CANCELADA"] }
{ "estado": "PENDIENTE", "siguientes": ["CONFIRMADA", "CANCELADA", "RECHAZADA"] }
```

Una reserva cerrada muestra «Sin acciones» en vez de una celda vacía, en `$tinta-suave` (6.9:1) y no
en `$tinta-tenue` (3.6:1), que en los tokens es solo para texto no informativo: esto sí informa.

## Y el color por fin decía la verdad

RECHAZADA salía en el mismo amarillo que PENDIENTE, y son lo contrario: una espera respuesta del
hotel, la otra ya la tiene y no va a volver. Ahora el tono sigue la misma regla que los botones —si
no tiene salida, está muerta—:

| Estado | Color | Acciones |
|---|---|---|
| PENDIENTE | amarillo, esperando | Confirmar, Cancelar, Rechazar |
| CONFIRMADA | verde, viva | Cancelar |
| CANCELADA | rojo, cerrada | Sin acciones |
| RECHAZADA | rojo, cerrada | Sin acciones |

## Verificación

```text
.\mvnw.cmd test
Tests run: 225, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS

node tools/operacion/verificar-despliegue.mjs
despliegue: rutas de proxy, cabeceras y CSP coherentes   exit=0

34 capturas sin errores de consola ni de API
Successfully applied 8 migrations, now at version "v8"
```

Contra el servidor en marcha, con reservas reales:

| Operación | Antes | Ahora |
|---|---|---|
| RECHAZADA desde PENDIENTE | 500 CHECK constraint failed | 200 |
| CANCELADA → CONFIRMADA | 200 (robaba inventario) | 409 con motivo |
| CONFIRMADA → RECHAZADA | 200 | 409 con motivo |
| Estado inexistente | 400 | 400 (sin cambios) |

Capturas: `panel-estados.png` (escritorio) y `panel-estados-movil.png` (390 px, tarjetas con
etiquetas y sin desbordamiento).

## Aviso honesto

La base de desarrollo se reconstruyó desde cero en esta ronda, así que el inventario de demostración
se vuelto a sembrar por la API (4 habitaciones, un tipo, un plan, tarifas de octubre de 2026 a marzo
de 2027). Las 34 capturas se regeneraron sobre esa base y pasaron sin errores. El respaldo anterior
quedó en el volumen como `hotel-antes-de-restaurar-20261006T202540Z.sqlite3` y el que tenía el
esquema roto, en `hotel-roto-v8-sin-usuario_id.sqlite3`.