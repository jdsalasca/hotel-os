# Despliegue en producción

Fecha: 2026-10-06. Verificado en Docker local; el despliegue real requiere un dominio y un disco
persistente que no se crean desde este repositorio.

## La respuesta corta a "¿SQLite aguanta con menos de 10 000 usuarios?"

**Sí, y con holgura.** Pero la pregunta relevante no es el número de usuarios, es dónde vive el
archivo. Medido en esta máquina (`EscaladoSqliteTest`, ejecutable, no de manual):

| Medición | Resultado |
|---|---|
| 20 hilos compitiendo por la misma habitación | 1 aceptada, 19 con 409, **0 sobreventas** |
| Rendimiento de escritura | 48 reservas/segundo |
| 50 000 reservas de historial | 7,5 MB de archivo |

Un hotel con 10 habitaciones no pasa de unas decenas de reservas al día. El techo medido (48/s) está
tres órdenes de magnitud por encima. **El límite no es SQLite: es que SQLite es un archivo y el
archivo necesita un disco que sobreviva a la reinstalación de la aplicación.**

## Frontend en Vercel

El frontend es estático y encaja en Vercel:

```bash
npm i -g vercel          # solo si no lo tienes
cd apps/web && vercel    # o desde la raíz del repositorio
```

`vercel.json` ya está en la raíz con:

- `buildCommand`: `cd apps/web && npm ci && npm run build`
- `outputDirectory`: `apps/web/dist`
- **Reescritura de `/api/*` hacia el backend** y fallback a `index.html` para las rutas de React
  Router. Sin ese fallback, `/admin/reservas` daría 404 al recargar.
- Cabeceras de seguridad y caché inmutable de `/assets`.

En Vercel, configura la variable de entorno con la URL del backend y ajústala en `vercel.json`:

| Variable en Vercel | Valor |
|---|---|
| `VITE_API_BASE` | `https://api.tu-dominio.com` |

Y en el frontend, el cliente debe usar esa base cuando no esté en el mismo origen.

## Backend: dónde sí (y dónde no)

### ❌ El backend NO puede ir en Vercel con SQLite

Las funciones serverless de Vercel tienen el sistema de archivos **efímero y de solo lectura entre
invocaciones**: cada arranque pierde lo escrito. Un `hotel.sqlite3` ahí no sobrevive ni a un
redespliegue, y Vercel no ofrece disco persistente. SQLite aquí no es un problema de escala: es que
no hay disco.

### ✅ Opciones válidas

| Opción | Cuándo |
|---|---|
| **VM con disco persistente** (Hetzner, DigitalOcean, OVH, AWS EC2 con EBS) | Opción recomendada: control total, respaldos claros, sin sorpresas de coste |
| **Fly.io con volumen persistente** | Más simple de operar; permite montar disco en una máquina |
| **Railway / Render con disco** | Si ya usas esas plataformas ycontratas volumen |
| Railway/Render **sin** disco + Postgres | Si se quiere serverless, el cambio es la base de datos, no la aplicación |

En todos los casos: **una sola instancia de escritura**. El backend está diseñado para eso.

## Puesta en producción (VM con disco)

```bash
git clone <repo> && cd hotel-os
cp .env.example .env && nano .env      # DOMAIN, ACME_EMAIL, ADMIN_INIT_TOKEN

# 1. La imagen compila el JAR dentro del contenedor: no hace falta Maven en el servidor.
docker compose -f compose.production.yaml build api

# 2. Respaldo ANTES de actualizar. Ver tools/operacion/respaldar.sh
docker compose -f compose.production.yaml run --rm --entrypoint \
  sh api /usr/local/bin/respaldar.sh /data/hotel.sqlite3 /backups

# 3. Despliegue
docker compose -f compose.production.yaml up -d

# 4. Crear el primer administrador (un solo uso)
curl -X POST https://tu-dominio.com/api/admin/init \
  -H 'Content-Type: application/json' \
  -d '{"token":"<ADMIN_INIT_TOKEN>","email":"admin@hotel","password":"<contraseña larga del hotel>"}'
```

> El Dockerfile de la API es multietapa: `maven:3.9-eclipse-temurin-25` compila y la imagen final
> solo lleva el JRE. Antes copiaba el JAR de `target/`, y eso ya costó un despliegue con la versión
> anterior: `docker compose up --build` levantaba sin error un `target/` viejo y los endpoints
> nuevos devolvían 403 con `Allow: POST`. Si alguna vez ves un endpoint que "no existe" en un
> contenedor recién construido, casi siempre es esto.

`compose.production.yaml` **falla al validar** si faltan `DOMAIN`, `ACME_EMAIL` o
`ADMIN_INIT_TOKEN`. Es intencionado: es mejor un despliegue que no arranca que uno que arranca sin
secrets.

## HTTPS

Caddy obtiene y renueva el certificado Let's Encrypt automáticamente. Solo hay que:

1. `DOMAIN` apuntando por DNS a la IP de la VM.
2. Puertos 80 y 443 abiertos (el 80 es necesario para el desafío ACME).
3. `ACME_EMAIL` para avisos de renovación.

Caddy redirige HTTP a HTTPS y añade HSTS por su cuenta.

## ⚠️ Sobre `docker compose down -v`

**Nunca uses `-v` en producción.** Elimina los volúmenes nombrados: se van las reservas, las
tarifas, los usuarios y los respaldos que estén en ese volumen.

```powershell
docker compose -f compose.production.yaml down      # ✅ para, conserva datos
docker compose -f compose.production.yaml down -v   # ❌ BORRA LOS DATOS
```

## Respaldo y restauración (probado, no supuesto)

`tools/operacion/respaldar.sh` usa la API `.backup` de SQLite, no `cp`. Copiar el archivo a pelo
con una escritura en curso produce un archivo inconsistente: el WAL contiene cambios que el archivo
principal aún no tiene. Además el script **verifica la integridad del respaldo** y borra el archivo
si no la pasa, y aplica la retención configurada.

**Ciclo probado de verdad** (destrucción y recuperación, salida real):

```
=== 2. Respaldo consistente ===
respaldo creado: /backups/hotel-20261006T202240Z.sqlite3 (176.0K, 18 tablas, integridad ok)

=== restauracion ===
respaldo verificado: integridad ok
estado actual guardado en /data/hotel-antes-de-restaurar-20261006T202540Z.sqlite3
restauracion completada
  integridad: ok
  tablas:     18
  reservas:   2

=== app tras restaurar ===
health: {"estado":"ok","base":"accesible","tablas":"4"}
reservas recuperadas: H-F824583F, H-DF01BF78
```

Restaurar con la aplicación **detenida**. El orden correcto: parar → sustituir → arrancar →
comprobar `/api/health`. La razón está en el script: restaurar sobre un archivo con una escritura
en curso deja el WAL inconsistente.

Para automatizar la prueba del ciclo:

```bash
docker compose -f compose.production.yaml stop
docker build -t hotel-ops tools/operacion
docker run --rm -v hotel-os_hotel-data:/data -v hotel-os_hotel-backups:/backups hotel-ops
```

## Hallazgo operativo: Flyway no repara tablas borradas

Si alguien borra una tabla a mano, Flyway **no** la recrea (el historial dice que la migración ya se
aplicó). El healthcheck lo detecta y lo dice:

```
{"estado":"degradado","base":"esquema incompleto","tablas":"3"}
```

La recuperación es restaurar un respaldo, no "reinstalar". Por eso `HealthController` consulta el
esquema real y no solo responde `ok`.

## Copia de respaldos fuera de la VM

El volumen vive en el disco de la VM. Si se pierde la VM o el disco, se pierden las reservas.
**Los respaldos deben copiarse a otro sitio**, por ejemplo:

```bash
# Con rclone hacia almacenamiento remoto (S3, Backblaze B2, Google Drive...)
docker run --rm -v hotel-os_hotel-backups:/backups rclone/rclone \
  sync /backups remote:hotel-respaldo

# O simplemente por SSH a otra máquina
rsync -az --delete user@otra-maquina:/respaldos/hotel-os/ /backups/
```

Recomendación: copia diaria automática (`cron`) más rotación mensual en un destino distinto.

## Actualización con respaldo previo

```bash
git pull                                                                     # 1. código nuevo
docker compose -f compose.production.yaml build api                          # 2. compila (con sus pruebas)
docker compose -f compose.production.yaml run --rm --entrypoint \
  sh api /usr/local/bin/respaldar.sh /data/hotel.sqlite3 /backups            # 3. respaldo
docker compose -f compose.production.yaml up -d                              # 4. reinicia
curl -fsS https://tu-dominio.com/api/health                                  # 5. verifica
```

Si la salud no vuelve a `ok`, se restaura el respaldo del paso 2.

## Qué falta antes de declarar el sistema en operación

1. **Dominio y correo** del hotel (para avisos de Caddy y el remitente de los correos).
2. **`ADMIN_INIT_TOKEN`** generado en el servidor: `openssl rand -hex 32`.
3. **Credenciales de las OTAs**, que además requieren ser aceptado como partner/channel manager.
   Sin eso, los tres canales quedan en `NO_CONFIGURADO` y así se muestran: es correcto, no es un fallo.
4. **Inventario y tarifas reales** cargados por el panel. Sin ellos la web pública no muestra
   habitaciones, a propósito: no se muestra un precio que el hotel no ha fijado.
5. **Copia de respaldos externa** configurada y probada una vez.