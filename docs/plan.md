# Rondas y criterios — Sistema hotelero Villa de Leyva / Sáchica
Fecha: 2026-10-06. Fuente versiones: docs.spring.io (Boot 4.1.1 estable, Java 17..26), Flyway Redgate docs (SQLite 3.7+, driver org.xerial:sqlite-jdbc), Maven Central (sqlite-jdbc 3.53.4.0 2026-08-26).

## Objetivo
Monolito modular desplegable en instancia única con disco persistente, web pública + admin en español, inventario centralizado transaccional, OTA solo con APIs oficiales y estado real, indicadores 3 fases, UI SCSS responsive accesible, SQLite en volumen nombrado.

## Restricciones globales
- Backend: Java 25, Spring Boot 4.1.1, Maven Wrapper, Spring MVC, Spring Security, Spring JDBC explícito, SQLite JDBC 3.53.4.0, Flyway + `flyway-database-nc-sqlite` (verificar). Sin JPA/Hibernate.
- Frontend: React + TypeScript + Vite, SCSS con tokens (nunca CSS suelto ni inline), rutas pública/admin por roles.
- Persistencia: `jdbc:sqlite:/data/hotel.sqlite3`, volumen nombrado, single-writer, transacciones, sin escalar escritura horizontal.
- Secretos: solo env/archivos fuera del repo. `.env.example` vacío/ejemplo.
- Sin datos reales inventados. DEMO marcado.
- TDD, commits atómicos en `develop`, `git status` limpio, evidencia en `docs/evidence/round-N/`.

## Rondas
- [x] **Ronda 1 — Contexto y modelo (esta):** repo inspeccionado (Default Project sin git, sin docs/apps; Java 25 OK, Docker OK, Node ausente), arquitectura + modelo + requisitos OTA documentados, esqueleto verificable (pom, V1 SQL, health test, compose, web tokens). Criterio: `mvn -q test` verde en `apps/api`, `docker compose config` válido, docs presentes.
- [ ] **Ronda 2 — Backend y reservas:** API seguridad + migraciones + inventario transaccional anti-doble-reserva + reserva web pendiente/confirmada + origen. Entregable: reserva de prueba en panel y en SQLite del volumen, sobrevive a restart. Tests: superposición rechazada, idempotencia.
- [ ] **Ronda 3 — OTA:** conectores separados, mapeos, estados (no-configurado/pendiente/sandbox/conectado/error/desconectado), reintentos, conciliación, bitácora sin secretos. Entregable: sandbox donde haya acceso; si no, bloqueo exacto documentado, nada simulado como conectado. Resiliencia local ante caídas.
- [ ] **Ronda 4 — Indicadores:** definiciones/fórmulas/fuentes/periodos + CSV + vista imprimible, sin inventar bases/metas. Entregable: informe con DEMO marcado.
- [ ] **Ronda 5 — Frontend y visual:** SCSS sistema, flujos públicos/admin, a11y, responsive, capturas `docs/screenshots/` móvil+desktop verificadas con Playwright.
- [ ] **Ronda 6 — Despliegue y operación:** Compose dev/prod, HTTPS proxy, healthchecks, backups consistentes + restauración probada + retención + copia externa, guía VM disco persistente, update con respaldo previo. Entregable: `docker compose up --build` local OK y reserva sobrevive a reinicio.

## Estado real 2026-10-06
- ✅ Java 25.0.4.1, Maven 3.9.11 global (solo para bootstrap, build usa wrapper), Docker 29.8.2, Git 2.55.0.
- ⛔ Node/npm/bun ausentes → bloquea build `apps/web` hasta instalar Node LTS global (documentar, no instalar sin autorización).
- ⛔ Sin credenciales OTA (esperado): Booking necesita Connectivity Partner + certificación pricing; Airbnb necesita Partner Program + NDA + security review; Despegar necesita HotelCode + API key + mTLS + certificación PDF + PCI si tarjeta. Detalle en `docs/integrations/ota-estado.md`.
- Siguiente: Ronda 2 backend+reservas.
