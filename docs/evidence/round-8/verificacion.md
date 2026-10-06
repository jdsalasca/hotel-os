# Ronda 8 — Despliegue, operación y respuesta sobre SQLite

Fecha: 2026-10-06. Rama `develop`.

## ¿Sirve SQLite para menos de 10 000 usuarios?

**Sí, con holgura, pero la pregunta relevante es dónde vive el archivo.** Medido en esta máquina
con `EscaladoSqliteTest` (pruebas ejecutables, no cifras de manual):

```
CONCURRENCIA: 20 hilos -> aceptadas=1 rechazadas=19 registros=1
RENDIMIENTO:   300 reservas en 6,27 s -> 48 reservas/segundo
HISTORIAL:     50000 reservas -> 7500 KB de archivo SQLite
```

- **Integridad**: 20 hilos compitiendo por la misma habitación → una sola reserva, 19 con 409,
  **cero sobreventas**. Es el requisito de aceptación, medido.
- **Rendimiento**: 48 escrituras/segundo. Un hotel de 10 habitaciones no llega a unas decenas de
  reservas al día: tres órdenes de magnitud de margen.
- **Tamaño**: 50 000 reservas ocupan 7,5 MB. El historial no es un problema.

**El límite real**: SQLite es un archivo y el archivo necesita un disco que sobreviva a un
redespliegue. En Vercel las funciones serverless tienen el sistema de archivos efímero, así que
ahí el problema no es de escala sino de existencia: no hay disco. El backend va en VM o servicio
con volumen persistente; el frontend sí puede ir en Vercel.

## Respaldo y restauración: probados, no supuestos

`tools/operacion/respaldar.sh` usa la API `.backup` de SQLite, **no `cp`**. Copiar el archivo a
peso con una escritura en curso produce un archivo inconsistente: el WAL contiene cambios que el
archivo principal aún no tiene. El script verifica la integridad del respaldo, borra el archivo si
no la pasa y aplica la retención configurada.

**Ciclo de desastre ejecutado de verdad** sobre el volumen real:

```
=== Respaldo consistente ===
respaldo creado: /backups/hotel-20261006T202240Z.sqlite3 (176.0K, 18 tablas, integridad ok)

=== Restauración ===
respaldo verificado: integridad ok
estado actual guardado en /data/hotel-antes-de-restaurar-20261006T202540Z.sqlite3
restauracion completada
  integridad: ok    tablas: 18    reservas: 2

=== App tras restaurar ===
health: {"estado":"ok","base":"accesible","tablas":"4"}
reservas recuperadas: H-F824583F, H-DF01BF78
```

## Hallazgo operativo: Flyway no repara tablas borradas

Al borrar una tabla a mano, Flyway **no** la recrea (el historial dice que la migración ya corrió).
El healthcheck lo detecta y lo dice en vez de mentir:

```
{"estado":"degradado","base":"esquema incompleto","tablas":"3"}
```

La recuperación es restaurar un respaldo, no reinstalar. Por eso `HealthController` consulta el
esquema real de SQLite y no responde `ok` a ciegas.

## Compose de producción

- Falla al validar si faltan `DOMAIN`, `ACME_EMAIL` o `ADMIN_INIT_TOKEN`. Verificado:
  `error while interpolating ... ADMIN_INIT_TOKEN: required variable is missing`.
- El backend **no publica puertos**: solo lo ve el proxy. Exposición mínima.
- `HOTEL_AMBIENTE=produccion` por defecto: con el usuario demo activo la app se niega a arrancar.
- HTTPS con Caddy (certificado automático, HSTS y redirección), volúmenes para datos, respaldos y
  certificados TLS.
- Healthchecks que consultan el esquema real, no `java -version`.

## Vercel

- `vercel.json` con `installCommand`/`buildCommand` que entran en `apps/web` (el lockfile está
  ahí; `npm ci` en la raíz del repositorio fallaría), `outputDirectory: apps/web/dist`, reescritura
  de `/api/*` al backend y **fallback a `index.html`**, sin el cual `/admin/reservas` daría 404 al
  recargar.
- `VITE_API_BASE` para el caso de frontend y backend en dominios distintos, aplicado en el cliente
  HTTP y en el enlace de descarga del CSV.
- Build verificado con Node 22 en Docker, que es exactamente el toolchain de Vercel:
  `dist/` con `index.html`, `assets/` y las rutas del router presentes.

**El backend no puede ir en Vercel con SQLite**: filesystem efímero. Alternativas documentadas en
`docs/operations/despliegue-respaldos.md` (VM con disco, Fly.io con volumen, Railway/Render con
disco, o serverless cambiando a Postgres).

## Pruebas

```
.\mvnw.cmd test  ->  Tests run: 138, Failures: 0, Errors: 0, Skipped: 0
```

## Pendiente antes de operar

1. Dominio y correo del hotel.
2. `ADMIN_INIT_TOKEN` generado en el servidor (`openssl rand -hex 32`).
3. Credenciales de las OTAs, que además exigen ser aceptado como partner. Sin eso los tres canales
   quedan en `NO_CONFIGURADO`, que es el estado correcto y visible.
4. Inventario y tarifas reales cargados por el panel. Sin ellos la web no muestra habitaciones:
   no se muestra un precio que el hotel no ha fijado.
5. Copia de respaldos externa configurada y probada.