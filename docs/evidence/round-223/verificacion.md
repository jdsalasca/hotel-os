# Ronda 223 — Restauración segura

Fecha: 2026-10-11. No se leyó ni modificó ninguna base o respaldo real. Las regresiones usaron
archivos SQLite sintéticos en contenedores efímeros. La comprobación con Flyway se hizo en un
volumen Docker nuevo y temporal, creado para esta ronda y eliminado después.

## Hallazgo reproducido antes de la corrección

Base de partida: `develop` en `1b31d926087c2ae5d67f3fadf58147bb961a03c6`.

Una base SQLite vacía devolvía `PRAGMA integrity_check = ok`; el restaurador anterior terminaba con
código 0, reemplazaba el destino sintético y anunciaba:

```text
restauracion completada
  integridad: ok
  tablas:     0
  reservas:   ?
```

La revisión posterior encontró fallas adicionales. Una base con historial Flyway de una sola
columna (`version='texto'`) y cinco tablas de una columna pasaba como esquema v0. Además, el
contenedor de operación corría como root y el archivo instalado quedaba inaccesible para la API,
que corre como `hotel` (UID 100, GID 101). Las pruebas de esquema, identidad y acceso fallaron con
el comportamiento previo; una fila Flyway `success=0` también se demostró aceptada antes de añadir
su rechazo.

## Cambio

- Comprueba integridad, columnas estándar del historial Flyway (incluido `checksum`), que no existan
  migraciones fallidas o sin checksum y que el historial SQL no tenga huecos ni versiones duplicadas.
  Compara cada checksum con el CRC32 de las migraciones de la misma versión empaquetadas en la imagen.
  Conserva bases válidas con un marcador `BASELINE` v1, permitido por `baseline-on-migrate` en la API.
- Exige las tablas y columnas incorporadas hasta la versión declarada por el historial Flyway; así
  un respaldo válido v20 conserva su esquema histórico y Flyway puede completarlo al arrancar.
  Rechaza versiones posteriores a v21, que esta imagen no conoce.
- Comprueba las reglas de reserva por versión, índices de ocupación y consulta, claves primarias y
  foráneas y restricciones de pago/chat introducidas por V19; `foreign_key_check` rechaza filas huérfanas.
- Verifica antes de modificar la base que `hotel` pueda crear archivos en el directorio destino;
  evita instalar una base si SQLite no podrá crear WAL/SHM.
- Las imágenes de API y operaciones fijan explícitamente el usuario `hotel` en UID/GID `100:101`,
  para que una actualización de Alpine no cambie silenciosamente la propiedad del archivo.
- Prepara la candidata en el mismo volumen, le asigna `100:101` y modo `660`, y guarda el estado
  previo en una ruta exclusiva creada con `mktemp`.
- Comprueba la base instalada como el usuario `hotel`: activa WAL y ejecuta DDL dentro de una
  transacción que se revierte.
- Ante una falla al limpiar WAL/SHM, instalar o validar, devuelve error, no informa éxito y trata de
  recuperar la copia previa. `INT` y `TERM` durante la sustitución hacen el mismo intento. La ruta de
  esa copia se conserva para recuperación manual.
- La imagen de operaciones crea el usuario de prueba `hotel` con el mismo UID/GID que la API.

## Regresiones y ciclo sintético

Comandos ejecutados desde el worktree aislado:

```text
docker build -f tools/operacion/Dockerfile -t hotel-ops-r223 .
exit code: 0

docker run --rm hotel-ops-r223 /usr/local/bin/probar-restaurar-segura.sh
exit code: 0
```

La última ejecución completa pasó los 25 bloques del arnés (`exit code: 0`, línea final
`PRUEBA DE RESTAURACION SEGURA VERIFICADA`). Cubrió la restauración válida con escritura WAL como
`hotel`; permisos del directorio y conservación del destino ante fallos; ciclo completo de
respaldo/destrucción/restauración; checksums, nombres, tipos, versiones y estructura del historial
Flyway; baseline v1, continuidad de migraciones y rechazo de versiones o metadatos inválidos; CHECK
ausentes o demasiado restrictivos; unicidad, PK simples y compuestas, `NOT NULL` según versión,
claves foráneas, índices y `AUTOINCREMENT`; compatibilidad de esquemas v1, v15 y v20; columnas
históricas ausentes; y recuperación de la base previa ante consulta fallida, limpieza parcial y
`SIGTERM` posterior a la sustitución. En los rechazos, el destino sintético permaneció intacto. El
ciclo acabó con integridad `ok`, 26 tablas y una reserva de prueba.

La revisión independiente detectó que exigir siempre el esquema v21 rechazaría respaldos anteriores
válidos; la regresión v20 se observó fallar antes de aplicar la compatibilidad por versión y pasó después.
También falló antes la regresión de checksum ausente. La prueba de señal confirma que `TERM` tras la
sustitución no anuncia éxito y devuelve la base previa.

## Migraciones reales y estado de la API

```text
docker build -t hotel-api-r223 apps/api
exit code: 0
```

Se inició la imagen sobre el volumen nuevo `hotel-r223-schema-final3`, sin credenciales externas ni
datos reales. Los logs confirmaron que Flyway validó y aplicó las 21 migraciones hasta v21. La API
respondió:

```text
{"base":"accesible","tablas":"4","estado":"ok"}
```

La base de ese volumen fue aceptada por el restaurador, copiándola únicamente a `/tmp` dentro del
contenedor efímero:

```text
respaldo verificado: esquema v21, 25 tablas, 0 reservas, integridad ok
restauracion completada
  archivo:   /tmp/hotel-r223-migrated.sqlite3
  integridad: ok
  esquema:   v21 (25 tablas)
  reservas:  0
exit code: 0
```

El contenedor y el volumen temporal se eliminaron después de comprobar el resultado.

También se probó la ruta completa de compatibilidad con un respaldo Flyway v20 real, sin datos de
producción. La imagen arrancó con `SPRING_FLYWAY_TARGET=20`; la copia tenía versión 20 y no tenía la
columna `lugares_interes.categoria`. Con la API detenida, se restauró esa copia a `/data/hotel.sqlite3`.
Al arrancar después la imagen normal, Flyway aplicó la migración v21, la API volvió a salud `ok` y la
base reportó versión 21 con la columna `categoria`:

```text
API v20 healthy: {"estado":"ok","tablas":"4","base":"accesible"}
v20 backup checks (version, category column): 20 0
respaldo verificado: esquema v20, 25 tablas, 0 reservas, integridad ok
restauracion completada
API migrated v20 to v21: {"estado":"ok","base":"accesible","tablas":"4"}
final checks (version, category column): 21 1
exit code: 0
```

Se reconstruyó `hotel-api-r223` con los UID/GID fijados; `id hotel` devolvió `100:101` tanto en
`hotel-api-r223` como en `hotel-ops-r223`.

En una ejecución final, la API arrancó sobre un volumen temporal nuevo y respondió
`{"estado":"ok","tablas":"4","base":"accesible"}`. Tras detenerla, la consulta de solo lectura
confirmó versión Flyway `21` y `0` filas en `PRAGMA foreign_key_check`; se retiraron el contenedor y
el volumen de esta prueba.

La base v21 creada por la API coincidió con el manifiesto CRC32 de la imagen de operaciones en sus
21 migraciones SQL: cada par `aplicado/esperado` fue igual y el rango fue `1..21`.

La suite completa del backend se ejecutó como UID 1000 sobre una copia Linux temporal escribible;
la caché de Maven usó un volumen Docker. Así las pruebas de permisos se ejecutaron como usuario sin
privilegios sin intentar reescribir `target/` sobre el montaje de Windows:

```text
mvn -B -Dmaven.repo.local=/home/ubuntu/.m2/repository test
Tests run: 477, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Total time: 14:34 min
exit code: 0
```

## Alcance pendiente

Las pruebas ejercitan SQLite real, las migraciones actuales, el ciclo de respaldo/restauración,
permisos de UID/GID y el reinicio de la API tras restaurar un v20. No prueban una instalación en
producción; la guía continúa exigiendo detener la API, restaurar y comprobar `/api/health` al
reiniciar. La integración a `develop` requiere repetir la verificación sobre esa rama.
