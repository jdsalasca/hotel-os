# Evidencia Ronda 1 — 2026-10-06
## Tests backend (apps/api)
Comando: `mvn -Dtest=SolapeTest test` en `apps/api`
Resultado real:
- Tests run: 2, Failures: 0, Errors: 0, Skipped: 0 -- in co.hotel.disponibilidad.SolapeTest
- BUILD SUCCESS (1.5s)
- Java: openjdk 25.0.4.1 Temurin, Maven 3.9.11, Spring Boot fijado 4.1.1 (docs.spring.io, compatible Java 17..26), sqlite-jdbc 3.53.4.0, Flyway SQLite 3.7+ (Redgate docs).

## Compose
- `docker compose -f compose.yaml config --quiet` → DEV_EXIT:0
- `docker compose -f compose.production.yaml config --quiet` → PROD_EXIT:0 (tras fix defaults para validación local; en prod real DOMAIN y ADMIN_INIT_TOKEN vienen de .env)
- Docker 29.8.2.

## Git
- Repo nuevo `hotel-villa-leyva`, rama `develop`, commit `feat: ronda 1 contexto modelo y esqueleto verificable`, `git status` limpio.
- Sin secretos: grep de ADMIN_INIT_TOKEN/SECRET solo en .env.example y compose como nombres vacíos.

## UI
- Sin UI ejecutable aún (Node ausente bloquea `apps/web` build). Tokens SCSS creados. Screenshots desde Ronda 5 con Playwright.

## Bloqueos
- Node/npm/bun ausentes → instalar Node 22 LTS global antes de Ronda 5.
- OTA sin credenciales (esperado): estados no configurado, requisitos en docs/integrations/ota-estado.md.
