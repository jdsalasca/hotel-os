# Ronda 49 — Respaldos programados que funcionan sin el agente

## Lo que faltaba

Los scripts de respaldo existían pero nada los ejecutaba: las copias solo ocurrían si alguien se
acordaba. La continuidad del producto exige que funcionen sin el agente delante.

## Lo que se entrega

Servicio `respaldo` en `compose.yaml` y `compose.production.yaml`: la imagen de herramientas
ejecutando `programar.sh`, un bucle con sleep entre copias (sin demonio cron que configurar,
supervisar y loguear aparte). Una copia diaria con retención de 30 días, ajustable con
`BACKUP_INTERVAL_SECONDS` y `BACKUP_RETENTION_DAYS`. La doc (`despliegue-respaldos.md`) ya no
recomienda un cron suelto.

## Dos defectos que encontró la prueba viva

1. **El `:ro` rompe SQLite**: con el volumen en solo lectura, `.backup` falla con «unable to
   open database file» porque el motor necesita sus archivos de bloqueo hasta para leer en modo
   WAL. El servicio monta sin `:ro`, documentado en el compose.
2. **El script aceptaba respaldos vacíos**: un archivo de 0 bytes ES una base válida y pasaba
   `integrity_check`. Ahora exige tamaño > 0 y la tabla `reservations`, o borra y falla.

Sin el endurecimiento, el programador habría creado «respaldos» de 0 bytes cada 45 segundos
dando todo por bueno: el bucle informaba el fallo y reintentaba, pero nada lo habría notado.

## Verificación

```text
docker compose -f compose.yaml config              # valida
docker compose -f compose.production.yaml config   # valida (con .env de mentira)
# Bucle vivo 110 s con intervalo de 45 s contra desarrollo:
respaldo creado: /backups/hotel-20261007T184855Z.sqlite3 (300.0K, 20 tablas, integridad ok)
respaldo creado: /backups/hotel-20261007T184940Z.sqlite3 (300.0K, 20 tablas, integridad ok)
respaldo creado: /backups/hotel-20261007T185025Z.sqlite3 (300.0K, 20 tablas, integridad ok)
# Ronda sin cambios de interfaz ni de Java propios: la suite conjunta sigue en 264 en verde.
```

La copia verificada traía integridad ok y 13 reservas. Artefactos de la prueba retirados del
volumen; desarrollo intacto y sano.

## Convergencia con el otro agente

A mitad de la ronda, el otro agente commiteó (`3ce300f`) el mismo servicio `respaldo` en
`compose.production.yaml`, pero sin el script `programar.sh`, sin el Dockerfile, sin el compose
de desarrollo y sin el endurecimiento: su servicio habría entrado en crash-loop. Esta ronda
aporta justo las piezas que faltaban, así que su servicio ahora funciona; no se tocó nada suyo
y la suite conjunta (264 pruebas, 7 suyas) está verde.

## Nota de git

Sin push por regla vigente. Solo archivos de esta ronda en el commit.