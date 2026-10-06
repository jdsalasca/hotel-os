# Evidencia Ronda 2 — Backend y reservas (2026-10-06)
Metodología: skills `test-driven-development` (test rojo → verde), `systematic-debugging` (causa raíz BEGIN IMMEDIATE anidado), `verification-before-completion` (evidencia antes de afirmar).

## Tests (TDD)
Comando: `mvn "-Dtest=ReservaServiceTest,SolapeTest" test` en `apps/api`
Real:
- SolapeTest: Tests run: 2, Failures: 0, Errors: 0
- ReservaServiceTest: Tests run: 3, Failures: 0, Errors: 0 (rechaza superpuesta, idempotencia mismo código sin duplicar, permite salida==llegada)
- Total: Tests run: 5, Failures: 0, Errors: 0 — BUILD SUCCESS
- Rojo previo verificado: `cannot find symbol ReservaService` + `SQLITE_ERROR cannot start a transaction within a transaction` (corregido a autoCommit=true + BEGIN IMMEDIATE explícito).

## API corriendo en local (jar 38MB, Spring Boot 4.1.1 + Java 25)
- `GET /api/health` → `{"status":"ok"}`
- `POST /api/reservas` DEMO `ana@example.com 2026-11-01/05 WEB demo-key-001` → `201 {"codigo":"H-3F947A7F","estado":"PENDIENTE","mensaje":"pendiente de confirmación (sin pago/confirmación automática configurada)"}`
- Reintento misma `demo-key-001` → mismo `H-3F947A7F` (sin duplicar)
- `POST solapada 2026-11-04/06` → `409 sin disponibilidad para esas fechas`
- `GET /api/reservas/H-3F947A7F?email=ana@example.com` → `PENDIENTE|WEB`
- Reinicio del jar → `PERSISTE_TRAS_REINICIO:H-3F947A7F|PENDIENTE`, SQLite `./data/hotel.sqlite3` 57KB
- `POST /api/admin/init` con `ADMIN_INIT_TOKEN` → `administrador creado` (BCrypt 12, pass ≥12)
- `GET /api/admin/reservas` con Basic → `ADMIN_LISTA:1` (reserva visible en panel admin API); sin auth → `401`

## Compose
- `docker compose -f compose.yaml config --quiet` → 0
- `docker compose -f compose.production.yaml config --quiet` → 0

## Seguridad/privilegios
- Sin passwords por defecto ni users en memoria; init un solo uso por token secreto env.
- Secretos solo nombres vacíos en `.env.example`/compose; grep no halló valores reales.
- `data/` gitignored; todo DEMO marcado, sin tarifas/políticas inventadas.

## Límites conocidos (→ R3/R5/R6)
- Flyway aún no cableado (V1 versionado + DbInit explícito; artefacto `flyway-database-nc-sqlite` por validar).
- Rate-limit login y CSRF con sesión (stateless Basic ahora, CSRF N/A) → R3/R5.
- Panel visual y Docker build E2E → R5/R6.
