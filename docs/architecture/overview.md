# Arquitectura — monolito modular, instancia única
Fecha: 2026-10-06.

## Decisión
Monolito modular en un repo (sin microservicios). `apps/api` + `apps/web` + `docs/` + Compose. Despliegue: 1 instancia backend escritora + SQLite en volumen nombrado + proxy HTTPS.

## Módulos backend (`co.hotel.*`)
`config-hotel` (propiedades del hotel, todo configurable, nada hardcodeado) · `users-seguridad` (Spring Security, roles ADMIN/STAFF, encoder BCrypt, sesiones, rate-limit login, CSRF) · `rooms-inventario` (tipos, habitaciones, unidades) · `disponibilidad` (cómputo transaccional) · `reservas` (estados PENDIENTE/CONFIRMADA/CANCELADA, origen, idempotencia, historial) · `canales` (canales + mapeos) · `conectores-ota` (booking/despegar/airbnb, timeouts/reintentos/idempotencia, bitácora, conciliación) · `indicadores` (definiciones, periodos, cálculo, CSV) · `auditoria` (actividad admin) · `recibos` (no fiscales, distinguir de factura electrónica).

## Flujos
Público: buscar (llegada/salida/huéspedes) → solo disponible/configurado → datos mínimos huésped → revisar/enviar (clave idempotencia) → PENDIENTE si no hay pago/confirmación automática → consulta segura por código+email. Admin: login → resumen → calendario → habitaciones/tarifas/bloqueos → reservas → OTA (estado/sync/reintento) → indicadores → auditoría.

## Persistencia
SQLite single-writer: `jdbc:sqlite:/data/hotel.sqlite3`. WAL + `busy_timeout`, pool 1 escritura, transacciones SERIALIZABLE en reserva (SELECT solape + INSERT atómico). Sin `SELECT ... FOR UPDATE` (no soportado). Migraciones Flyway versionadas, backup antes de destructivas. Límite documentado + ruta futura a Postgres si volumen lo exige.

## Versiones fijadas (verificadas 2026-10-06)
Spring Boot 4.1.1 (docs.spring.io/system-requirements: requiere Java 17+, compatible hasta Java 26, Maven 3.6.3+, Tomcat 11). SQLite JDBC org.xerial:sqlite-jdbc:3.53.4.0 (Maven Central 2026-08-26). Flyway + org.flywaydb:flyway-database-nc-sqlite (Redgate docs: SQLite 3.7+, limitaciones sin SELECT FOR UPDATE, sin multi-schema, sin CREATE TRANSACTION en migración).

## Seguridad
Sin passwords por defecto. Init primer admin con `ADMIN_INIT_TOKEN` secreto (un solo uso). BCrypt, sesiones HttpOnly+Secure+SameSite, CSRF activado (token para React), cabeceras seguras en proxy, rate-limit login, sin secretos en frontend/SQLite/logs/imágenes/Git.
