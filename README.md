# Hotel Villa de Leyva y Sáchica — Sistema de reservas (monorepo)

Monolito modular: `apps/api` (Java 25 + Spring Boot 4.1.1 + JDBC + SQLite) + `apps/web` (React + TS + Vite + SCSS).

> Estado Ronda 1: contexto y modelo + esqueleto verificable. Sin datos reales del hotel. Todo dato de ejemplo es sintético y marcado DEMO.

## Estructura

```
apps/api/            backend Spring Boot (Maven Wrapper)
apps/web/            frontend React+TS+Vite+SCSS
docs/plan.md         rondas y criterios
docs/architecture/   arquitectura y modelo
docs/integrations/   estado real OTA
docs/operations/     despliegue, respaldos
docs/evidence/       evidencia por ronda
compose.yaml         desarrollo
compose.production.yaml  producción (disco persistente + HTTPS)
.env.example         variables sin secretos
```

## Requisitos

- Java 25 (verificado Temurin 25.0.4.1), Maven 3.6.3+ (se usa Maven Wrapper, no global)
- Docker 29.x + Compose
- Node 22 LTS para `apps/web` (pendiente instalar global — ver Bloqueos)

## Uso rápido (dev)

```powershell
Copy-Item .env.example .env
docker compose -f compose.yaml up --build
# API: http://localhost:8080/api/health
# Web: http://localhost:5173
```

Producción: ver `docs/operations/despliegue-respaldos.md` y `compose.production.yaml`.
NUNCA `docker compose down -v` en producción (elimina volúmenes).

## Seguridad

- Sin contraseñas por defecto ni usuarios en memoria en prod.
- Primer admin por mecanismo secreto documentado (`ADMIN_INIT_TOKEN`).
- Secretos solo en `.env`/secretos fuera del repo. Nada en frontend/SQLite/logs/Git.

## Datos

No hay nombre, logo, habitaciones, tarifas, impuestos ni políticas inventadas.
Todo se configura en panel admin / formularios. Semillas solo DEMO.
