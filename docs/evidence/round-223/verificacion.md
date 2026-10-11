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

- Comprueba integridad, columnas estándar del historial Flyway, que no existan migraciones fallidas
  y que haya una migración SQL exitosa con versión numérica.
- Exige las 23 tablas del producto y columnas esenciales de las tablas de reservas, inventario,
  tarifas, usuarios, chat, indicadores y auditoría.
- Verifica antes de modificar la base que `hotel` pueda crear archivos en el directorio destino;
  evita instalar una base si SQLite no podrá crear WAL/SHM.
- Prepara la candidata en el mismo volumen, le asigna `100:101` y modo `660`, y guarda el estado
  previo en una ruta exclusiva creada con `mktemp`.
- Comprueba la base instalada como el usuario `hotel`: activa WAL y ejecuta DDL dentro de una
  transacción que se revierte.
- Ante una falla al limpiar WAL/SHM, instalar o validar, devuelve error, no informa éxito y trata de
  recuperar la copia previa. La ruta de esa copia se conserva para recuperación manual.
- La imagen de operaciones crea el usuario de prueba `hotel` con el mismo UID/GID que la API.

## Regresiones y ciclo sintético

Comandos ejecutados desde el worktree aislado:

```text
docker build -t hotel-ops-r223 tools/operacion
exit code: 0

docker run --rm hotel-ops-r223 /usr/local/bin/probar-restaurar-segura.sh
exit code: 0
```

La salida final comprobó ocho casos: restauración válida con escritura WAL como `hotel`; rechazo
de directorio sin permiso para el usuario API sin cambiar la base previa; rechazo de esquema e
historial parciales; rechazo de migración fallida; rechazo de base vacía conservando archivo, WAL y
SHM; recuperación ante fallo de consulta posterior; recuperación tras falla parcial de limpieza de
sidecars; y ciclo completo de respaldo, destrucción sintética y restauración. El ciclo acabó con
integridad `ok`, 25 tablas y una reserva sintética.

## Migraciones reales y estado de la API

```text
docker build -t hotel-api-r223 apps/api
exit code: 0
```

Se inició la imagen sobre el volumen nuevo `hotel-r223-schema-probe`, sin credenciales externas ni
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

## Alcance pendiente

Las pruebas ejercitan SQLite real, las migraciones actuales, el ciclo de respaldo/restauración y
permisos de UID/GID. No prueban una restauración operativa sobre `/data` ni una instalación en
producción; la guía continúa exigiendo detener la API, restaurar y comprobar `/api/health` al
reiniciar. La integración a `develop` y el push quedan pendientes de revisión y verificación final.
