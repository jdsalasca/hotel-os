# Rondas y criterios — Sistema hotelero Villa de Leyva / Sáchica

Fecha: 2026-10-06. Versiones verificadas contra Maven Central y documentación oficial en esa fecha.

## Objetivo
Monolito modular desplegable en instancia única con disco persistente, web pública + panel en
español, inventario centralizado transaccional, OTA solo con APIs oficiales y estado real,
indicadores en 3 fases, UI SCSS responsive accesible, SQLite en volumen nombrado.

## Restricciones globales
- Backend: Java 25, Spring Boot 4.1.1, Maven Wrapper, Spring MVC, Spring Security, Spring JDBC
  explícito, SQLite JDBC 3.53.4.0, Flyway 13.9.0 + `flyway-database-nc-sqlite`. Sin JPA/Hibernate.
- Frontend: React + TypeScript + Vite, SCSS con tokens (nunca CSS suelto ni inline), rutas
  pública/admin por roles.
- Persistencia: `jdbc:sqlite:/data/hotel.sqlite3`, volumen nombrado, **instancia única de escritura**,
  transacciones IMMEDIATE, sin escalar horizontalmente.
- Secretos: solo variables de entorno. `.env.example` vacío/de ejemplo.
- Sin datos reales inventados; sin datos demo en el esquema.
- TDD, commits atómicos en `develop`, evidencia en `docs/evidence/round-N/`.

## Rondas

- [x] **Ronda 1 — Contexto y modelo.** Arquitectura, modelo de datos y requisitos OTA documentados.
- [x] **Ronda 2 — Backend y reservas (versión antigua).** Sustituida por la ronda 3: contenía
  defectos que hacían imposible iniciar sesión y no usaba Flyway. Ver abajo.
- [x] **Ronda 3 — Reconstrucción del backend + base de OTA.** 52 pruebas verdes; verificado contra el
  jar real: login, CSRF, reserva, 409 por solape, idempotencia, panel con historial y persistencia
  tras reiniciar el proceso. Evidencia: `docs/evidence/round-3/verificacion.md`.
- [ ] **Ronda 4 — Inventario, tarifas, canales y bloqueos.** API y pantallas de administración.
  Entregable: el hotel registra habitaciones y tipos por la aplicación, sin SQL a mano.
- [ ] **Ronda 5 — Conectores OTA.** Un conector por canal con su documentación oficial, reintentos,
  idempotencia y bitácora. Entregable: bloqueos externos identificados con precisión; ninguna
  integración declarada conectada sin llamada autorizada.
  **Hecho 2026-10-06** (100 pruebas): los tres conectores, cliente HTTP con timeouts y reintentos,
  bitácora y pantalla con reintento. **Conexión validada: ninguna** por falta de credenciales de
  partner. Evidencia: `docs/evidence/round-5/verificacion.md`.
- [ ] **Ronda 6 — Indicadores.** Definiciones, fórmulas, fuentes, periodos, exportación CSV y vista
  imprimible. Entregable: informe calculado con datos de prueba sintéticos marcados como tales.
- [ ] **Ronda 7 — Frontend y calidad visual.** Sistema SCSS, flujos públicos y admin, accesibilidad,
  responsive. Entregable: capturas verificadas en móvil y escritorio en `docs/screenshots/`.
- [ ] **Ronda 8 — Despliegue y operación.** Compose dev/prod, HTTPS, respaldos consistentes con
  SQLite, restauración probada, retención, copia externa y manual. Entregable: `docker compose up
  --build` funciona y una reserva sobrevive al reinicio de los contenedores.

## Estado real 2026-10-06
- ✅ Java 25.0.4.1, Docker 29.8.2, Maven Wrapper funcionando (el `distributionUrl` estaba roto y se
  corrigió a 3.9.11).
- ✅ 52/52 pruebas verdes; jar empaquetado y ejecutado; reserva persiste tras reiniciar el proceso.
- ⛔ **Node.js ausente en esta máquina**: `apps/web` no compila todavía. El frontend se hará y se
  verificará dentro de Docker (`node:22-alpine`), no instalando Node en el host sin autorización.
- ⛔ **Sin credenciales OTA** (esperado): ver `docs/integrations/ota-estado.md` con el bloqueo exacto
  de cada canal. Ninguna integración aparece como conectada.
- ⛔ Inventario, tarifas, canales e indicadores aún no tienen API ni pantalla: hoy se gestionan por
  SQL directo. Es el siguiente bloque de trabajo, no un detalle pendiente.

## Siguiente paso
Ronda 4: inventario, tarifas y bloqueos con sus endpoints de administración, porque sin inventario
configurado el flujo público no puede ofrecer habitaciones que el hotel haya dado de alta.