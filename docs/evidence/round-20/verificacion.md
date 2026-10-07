# Ronda 20 — La clave de idempotencia filtraba datos de otros huéspedes

## El defecto

`ReservaRepository.codigoPorClave` buscaba en la tabla **solo por la clave de idempotencia**, sin
mirar de quién era la reserva:

```java
// antes
"SELECT codigo FROM reservations WHERE idempotencia=?"
```

Y `ReservaController.crear` devolvía la reserva completa con esa respuesta:

```java
String codigo = svc.crear(datos);
return ResponseEntity.status(201).body(ReservaResp.de(svc.buscar(codigo).orElseThrow(), ...));
```

`ReservaResp` lleva **correo, nombre, fechas, huéspedes y total**. La ruta de fuga era entera:

1. Ana reserva y su navegador manda `Idempotency-Key: <uuid>`.
2. Bruno envía la misma clave. `codigoPorClave` encuentra la reserva de Ana y devuelve su código.
3. El controlador responde con `svc.buscar(codigo)`, que **no filtra por correo**: Bruno recibe el
   nombre, el correo y el total acordado de Ana.

Lo reproducido en el test, antes del arreglo:

```text
la segunda peticion no puede devolver la reserva de la primera
   ==> expected: not equal but was: <H-FDF8AD34>
```

Las dos reservas salían con el mismo código.

## El arreglo, en la raíz

La clave de idempotencia identifica un **intento de reserva**, no a una persona. Si dos peticiones
comparten clave, son dos reservas distintas. El correo entra en la búsqueda:

```java
"SELECT codigo FROM reservations WHERE idempotencia=? AND lower(email)=lower(?)"
```

`lower()` porque el correo se compara sin distinguir mayúsculas en toda la aplicación
(`ReservaService.consultar` ya lo hacía con `equalsIgnoreCase`).

### La restricción de base de datos también tenía que cambiar

Arreglar solo la consulta no bastaba: `V1__esquema_base.sql:44` declaraba
`idempotencia TEXT UNIQUE NOT NULL`. Con esa restricción, la reserva de Bruno daba
`SQLITE_CONSTRAINT_UNIQUE` y el endpoint respondía 500 en vez de crear la reserva.

`V6__idempotencia_por_huesped.sql` reconstruye la tabla con `UNIQUE (idempotencia, email)`.
SQLite no permite alterar una restricción UNIQUE, así que la migración copia, cambia el esquema y
renombra, reconstruyendo el índice `idx_reservations_llegada` que `DROP TABLE` se lleva consigo.

Detalle que salió al verificar: la primera versión de la migración usaba
`PRAGMA foreign_keys=OFF`, y **Flyway la rechazó**:

```text
Detected both transactional and non-transactional statements within the same migration
Offending statement found at line 14: CREATE TABLE reservations_v6
```

SQLite no tiene DDL transaccional, así que la mezcla no es válida. La migración va sin el PRAGMA:
el borrado no dispara los `REFERENCES` de `reservation_items` ni de `reservation_history`, y por eso
el orden de la copia es primero las tablas hijas y después el `DROP`.

## Verificación contra los contenedores reales

La migración se aplicó sola sobre la base de desarrollo que ya tenía reservas y respaldos:

```text
Successfully validated 6 migrations
Migrating schema "main" to version "6" - idempotencia por huesped
Successfully applied 1 migration to schema "main", now at version v6
```

Y el comportamiento, por HTTP contra el stack levantado:

```text
Ana,   clave compartida "k-4830": H-AF6A8D9D
Bruno, MISMA clave:                H-AD5A5390   distinto: True
Carla, reintenta su propia clave:  H-AF6A8D9D   igual a su primera: True
```

Las tres propiedades de un golpe: dos huéspedes distintos con la misma clave obtienen reservas
distintas, y un reintento del mismo huésped no duplica ni devuelve otra cosa.

```text
.\mvnw.cmd test
Tests run: 182, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS

docker compose ... capturas
capturas completas sin errores de consola ni de API (34 capturas)
```

`FugaPorIdempotenciaTest` afirma lo contrario de lo que hacía antes: comprueba que el cuerpo de la
respuesta de Bruno **no contiene** ni `"Ana Perez"` ni `"ana@example.com"`, y que hay dos filas en
`reservations`. Un test que solo mirara los códigos se habría dado por bueno.

## Lo que NO cambia

- Un reintento del mismo huésped sigue devolviendo su reserva. La idempotencia era correcta para lo
  que sirve: evitar el doble envío por doble clic o por reintento de red.
- La restricción sigue impidiendo dos filas con la misma clave **para el mismo correo**, que es el
  duplicado que hay que evitar.
- Cambió el esquema de la tabla, pero no su API: `codigo`, `email`, `estado` y el resto siguen
  igual, así que no hubo que tocar frontend ni consultas.