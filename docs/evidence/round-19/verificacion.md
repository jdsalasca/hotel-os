# Ronda 19 — El backend deja de correr como root

## El problema

El backend arrancaba como `uid=0`. En un servidor público eso no es un detalle de estilo: cualquier
vulnerabilidad de ejecución de código en la aplicación o en sus dependencias arranca como root en
la VM.

El `Dockerfile` anterior tenía un comentario que lo daba por inevitable:

> El proceso corre como root dentro del contenedor […] evita el fallo real que se vio al intentar
> bajar el privilegio: el volumen nombrado nace con propietario root y, si el proceso corre como
> otro usuario, SQLite no puede abrir el archivo (SQLITE_CANTOPEN).

## Por qué era inevitable según ese comentario, y por qué no lo era

El razonamiento era correcto en su conclusión pero la causa estaba equivocada. Antes `/data` solo
existía **dentro del volumen**, y un volumen vacío nace de root: entonces cualquier usuario distinto
de root se quedaba sin poder escribir.

La solución es crear `/data` en la **imagen**, con el propietario del usuario de la aplicación:

```dockerfile
RUN apk add --no-cache curl \
  && addgroup -S hotel \
  && adduser -S -G hotel -h /app hotel \
  && mkdir -p /data \
  && chown hotel:hotel /data
USER hotel
```

Docker copia la propiedad del directorio de la imagen al volumen nombrado cuando lo monta por
primera vez, así que **un volumen nuevo ya nace escribible**. Verificado:

```text
contenedor con volumen NUEVO:  uid=100(hotel) gid=101(hotel)
                               /data → drwxr-xr-x 100 101
                               /api/health -> 200
```

## El caso que sí rompe: un volumen ya existente

Un despliegue que actualiza desde una versión anterior tiene el volumen de root. Ahí el arranque
falla, y fallaba con `SQLITE_READONLY_DIRECTORY` enterrado en un stack trace de Spring: inaccionable.

Ahora `SqliteDataSources` comprueba la escritura al construir el DataSource y el mensaje dice qué
ejecutar. Verificado contra el volumen real del proyecto:

```text
# antes del arreglo
[SQLITE_READONLY_DIRECTORY] Process does not have permission to create a journal file
# después
no se puede escribir en /data, así que SQLite no abrirá la base.
  docker run --rm -v NOMBRE_VOLUMEN:/data alpine chown -R 100:101 /data
```

Y aplicando el remedy, sobre el volumen que ya tenía datos y respaldos:

```text
$ docker run --rm -v hotel-os_hotel-data:/data alpine chown -R 100:101 /data
$ docker run ... hotel-os-api:latest
Successfully validated 5 migrations
Schema "main" is up to date. No migration necessary.
Started HotelApplication in 4.342 seconds
estado: running
```

Las instrucciones quedaron en `docs/operations/despliegue-respaldos.md`, que es lo primero que
leerá quien despliegue sobre un servidor.

## Verificación de que nada se rompió al bajar el privilegio

Bajar privilegios rompe cosas de verdad si hay rutas de escritura escondidas. Comprobado contra
los contenedores reales, que es donde se ve:

```text
docker exec hotel-os-api-1 id -u                  -> 100
/api/health                                       -> {"estado":"ok","tablas":"4"}
POST /api/admin/login                             -> 200
POST /api/admin/tipos                             -> 201
GET  /api/admin/auditoria (escritura real en SQLite, modo WAL) -> fila presente
docker compose ... capturas
capturas completas sin errores de consola ni de API (34 capturas)
uid api tras capturas                             -> 100
```

```text
.\mvnw.cmd test
Tests run: 179, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS
```

El `Skipped: 1` es `PermisosDeVolumenTest`, que necesita permisos POSIX: en el host de desarrollo
(Windows) se salta, y corre en Linux, que es donde el backend se despliega. La misma comprobación
se verificó además en el contenedor real, que es la evidencia que importa.

## Lo que NO cambia

- La base sigue siendo la misma: no se recreó ni se perdió ningún dato. Los respaldos de la ronda
  anterior siguen en el volumen.
- El proceso sigue siendo PID 1 y `docker stop` sigue llegando por señales: el `exec` interior de
  `ENTRYPOINT` no se tocó.
- `JAVA_OPTS` sigue expandiéndose, que era el arreglo de la ronda anterior.
- El contenedor no tiene shell interactiva ni monta el socket de Docker: eso ya lo daba el diseño
  y no cambia.