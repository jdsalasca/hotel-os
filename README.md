# Sistema de reservas y gestión hotelera · Villa de Leyva y Sáchica

Monolito modular en un solo repositorio: web pública en español para huéspedes y panel
administrativo para el personal del hotel, con inventario centralizado, canales de venta,
indicadores y despliegue en una sola instancia con disco persistente.

> **Estado real:** el código está implementado y verificado con **285 pruebas** de backend,
> `tsc --noEmit` limpio en el frontend y ejecución real en Docker. Las integraciones con
> Booking.com, Despegar y Airbnb están **implementadas pero sin conexión validada**: faltan
> credenciales de partner. Los tres canales aparecen `NO_CONFIGURADO` con el bloqueo exacto, que es
> el estado honesto. Ver `docs/integrations/ota-estado.md`.

## Arquitectura

```
apps/api/     Java 25 · Spring Boot 4.1.1 · Spring Security · Spring JDBC · SQLite · Flyway
apps/web/     React 19 · TypeScript · Vite · SCSS con tokens
tools/        Utilidades de operación (respaldos) y verificación (capturas)
docs/         Arquitectura, integraciones, operaciones, plan y evidencia por ronda
```

Módulos del backend: `config`, `seguridad`, `admin`, `reservas`, `inventario`, `disponibilidad`,
`ota`, `indicadores`, `auditoria`, `correo`.

## Ejecutar en local

```bash
docker compose up --build -d
```

- Web: http://localhost:5173
- API: http://localhost:8080 (health en `/api/health`)

Con usuario de demostración (solo en desarrollo):

```bash
HOTEL_DEMO_ADMIN=true docker compose up --build -d
# Panel: http://localhost:5173/admin/entrar  ·  admin / admin
```

En producción ese usuario **no se puede activar**: con `HOTEL_AMBIENTE=produccion` la aplicación se
niega a arrancar. El administrador real se crea una sola vez con `ADMIN_INIT_TOKEN`.

## Configurar

```bash
cp .env.example .env      # y completa los valores
```

Lo que el hotel debe aportar: datos de sus habitaciones y tipos, sus planes tarifarios con precios
y moneda, sus tarifas por fecha, y el remitente de correo si quiere activar los avisos. **Sin
inventario y tarifas, la web no muestra habitaciones**: no se muestra un precio que el hotel no ha
fijado.

Para el envío de correo con Google (OAuth 2.0 + Gmail API), el paso a paso está en
`docs/operations/correo.md`. Los valores van en el `.env` real, nunca en el repositorio.

## Pruebas y verificación

```bash
cd apps/api && ./mvnw.cmd test          # 264 pruebas
```

Capturas de verificación (móvil y escritorio, con Playwright en Docker):

```bash
docker compose -f compose.yaml -f compose.capturas.yaml up --build --abort-on-container-exit capturas
```

Las capturas quedan en `docs/screenshots/` (30 en total). El guion **falla** si hay un error de consola o una
respuesta de API ≥ 400, para que una captura nunca documente una pantalla rota.

## Despliegue

- Guía completa: `docs/operations/despliegue-respaldos.md`
- El frontend puede desplegarse en **Vercel** (`vercel.json` listo).
- El backend **no puede ir en Vercel con SQLite**: las funciones serverless tienen el sistema de
  archivos efímero. Va en una VM o servicio con disco persistente (Fly.io con volumen, Railway o
  Render con disco, o una VPS con Docker).
- HTTPS con Caddy, volumen nombrado para la base y respaldos verificados con restauración probada.

> ⚠️ `docker compose down -v` **borra los volúmenes**: se pierden las reservas. Para parar sin
> borrar datos: `docker compose down`.

## ¿Sirve SQLite para este hotel?

Sí, con holgura, y medido en `EscaladoSqliteTest`:

| Medición | Resultado |
|---|---|
| 20 hilos compitiendo por una habitación | 1 aceptada, 19 con 409, **0 sobreventas** |
| Rendimiento de escritura | 48 reservas/segundo |
| 50 000 reservas de historial | 7,5 MB |

El límite no es el motor: es que el archivo necesita un disco que sobreviva al redespliegue.

## Documentación

| Documento | Contenido |
|---|---|
| `docs/plan.md` | Rondas, criterios y estado real |
| `docs/architecture/overview.md` | Arquitectura, concurrencia, versiones verificadas |
| `docs/architecture/modelo-datos.md` | Esquema y regla anti-doble-reserva |
| `docs/integrations/ota-estado.md` | Requisitos y bloqueo exacto de cada OTA |
| `docs/operations/despliegue-respaldos.md` | Despliegue, HTTPS, respaldos, recuperación |
| `docs/operations/correo.md` | Configuración del correo con Google |
| `docs/evidence/round-N/verificacion.md` | Evidencia de cada ronda |