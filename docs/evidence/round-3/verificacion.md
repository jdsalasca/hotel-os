# Ronda 3 — Reconstrucción del backend y base de integraciones

Fecha: 2026-10-06. Rama `develop`. Todo lo que sigue está respaldado por salida real de comandos.

## Por qué esta ronda existe

La revisión del código heredado encontró defectos que no eran de estilo sino de comportamiento:

| Defecto | Evidencia | Corrección |
|---|---|---|
| El login nunca validaba | `JdbcUserDetailsManager` leía la columna `hash` sin prefijo `{bcrypt}`; `matches()` daba `false` siempre | `DelegatingPasswordEncoder`; login probado contra la app real |
| CSRF desactivado "porque no hay sesión" | `SecurityConfig.java` (versión anterior) | CSRF con cookie + `CsrfTokenRequestAttributeHandler` con atributo nulo; 403 sin token verificado |
| `DbInit` no era Flyway | Partía el SQL con `split(";")` y sembraba una habitación `101` en cada arranque | Flyway real (`spring-boot-starter-flyway` + `flyway-database-nc-sqlite`); migración versionada sin datos demo |
| Servicio instanciado dentro del controller | `new ReservaService(jdbcUrl)` por request | Dependencias por constructor |
| Sin transacciones Spring | `ReservaService` abría su propia `Connection` con `PRAGMA` a mano | `SqliteTransactionExecutor` + DataSource con `TransactionMode.IMMEDIATE` |
| `reservation_history` sin escribir nunca | Tabla creada, ningún `INSERT` | `AuditoriaService` dentro de la misma transacción |
| DDL duplicado entre test y producción | `ReservaServiceTest` y `V1__init.sql` | El test usa la misma fábrica de DataSource y el esquema de la migración |
| Bug propio de la reescritura: clave de idempotencia resuelta y nunca enviada al `INSERT` | Descubierto por `SecurityIntegrationTest` (violación NOT NULL) | `ReservaRepository.insertar(codigo, datos, clave)` |
| Sin `/api/health` | Al ejecutar el jar, el healthcheck devolvía 401 | `HealthController` que además verifica el esquema en SQLite |
| Sin endpoints de panel | Al reescribir el controller se perdieron; una reserva no era visible | `AdminReservasController` con listado, detalle e historial |
| El Maven Wrapper no arrancaba | `distributionUrl` con versión `3` inexistente → 404 | `apache-maven-3.9.11-bin.zip` |
| Documentación que describía un Flyway inexistente | `overview.md` decía "migraciones Flyway" sin Flyway | Documentos alineados con el código |

## Versiones corregidas contra fuentes oficiales

- Spring Boot **4.1.1** (última GA; 4.2.0-M2 es milestone).
- SQLite JDBC **3.53.4.0**.
- Flyway **13.9.0** completo. Boot 4.1.1 gestiona `flyway-core` 12.4.0, incompatible con
  `flyway-nc-core` 13.9.0 (`NativeConnectorsSupportImpl` queda abstracto y el arranque falla): hay que
  fijar la serie completa con la propiedad `flyway.version`.
- `flyway-database-sqlite` **no existe** en Maven Central (404). SQLite solo está en
  `flyway-database-nc-sqlite`.
- Booking.com Connectivity: la autenticación vigente es **token-based**; la credential-based se apagó
  el 31/12/2025. El documento anterior describía la vía muerta.

## Pruebas

```
.\mvnw.cmd test
Tests run: 52, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Cobertura por clase:

| Clase | Tests | Qué fija |
|---|---|---|
| `CanalEstadoTest` | 15 | La configuración sola nunca declara `CONECTADO`; sin credenciales es `NO_CONFIGURADO`; el panel no filtra secretos |
| `BitacoraTest` | 7 | La bitácora redacta secretos, tokens, correos y teléfonos, y conserva lo diagnosticable |
| `ReservaServiceTest` | 15 | Solape, intervalo semiabierto, bloqueo de mantenimiento, idempotencia, validación, consulta segura, historial |
| `SecurityIntegrationTest` | 7 | Arranque con token, login válido e inválido, panel protegido, CSRF, consulta por correo |
| `AdminReservasControllerTest` | 5 | La reserva web aparece en el panel; confirmar/cancelar; estado inválido rechazado |
| `HealthControllerTest` | 1 | Salud pública que consulta el esquema real |
| `CsrfParaBrowserTest` | 2 | El navegador recibe `XSRF-TOKEN` y puede escribir; sin token, 403 |

## Verificación contra la aplicación real (no solo tests)

Con el jar empaquetado, `java -jar target/hotel-api-0.1.0.jar`, puerto 8099 y SQLite en
`%TEMP%\opencode\hotel-verify\data\hotel.sqlite3`:

```
GET  /api/health          -> {"estado":"ok","tablas":"4","base":"accesible"}
POST /api/admin/init      -> {"estado":"administrador creado"}
POST /api/admin/login     -> {"estado":"autenticado","rol":[{"authority":"ROLE_ADMIN"}]}
POST /api/admin/login     -> 401   (contraseña incorrecta)
POST /api/reservas        -> {"codigo":"H-F5F12AD2","estado":"PENDIENTE","origen":"WEB",
                              "mensaje":"... queda pendiente de confirmación ..."}
POST /api/reservas        -> {"error":"no hay disponibilidad para esas fechas"}   (solape, misma hab.)
POST /api/reservas        -> H-D0BF636D                                              (otra habitación)
POST /api/reservas x2     -> H-86EF8607 == H-86EF8607                               (idempotencia)
```

**Persistencia tras reiniciar el proceso** (mismo archivo SQLite, proceso java detenido y arrancado
de nuevo):

```
GET /api/admin/reservas -> las 3 reservas, mismas fechas, origen WEB
POST /api/admin/reservas/H-86EF8607/estado {"estado":"CONFIRMADA"} -> estado CONFIRMADA
GET /api/admin/reservas/H-86EF8607
  -> historial: [{estado_ant: null, estado_nuevo: "PENDIENTE",  actor: "WEB"},
                 {estado_ant: "PENDIENTE", estado_nuevo: "CONFIRMADA", actor: "admin@hotel.test"}]
```

CSRF comprobado con `curl` contra el servidor real:
`GET /api/health` devuelve `Set-Cookie: XSRF-TOKEN=...` y el `POST` posterior con esa cookie y la
cabecera `X-XSRF-TOKEN` se acepta.

## Lo que NO está hecho

- **Conectores OTA**: no hay llamadas a Booking, Despegar ni Airbnb. Solo la base (configuración por
  canal, estados, bitácora sin secretos). Ninguna integración puede mostrarse como conectada.
- Panel de React, tarifas, canales, indicadores, recibos, Compose, HTTPS, respaldos.
- Inventario, tarifas y bloqueos se gestionan hoy por SQL directo: no hay todavía pantalla ni API de
  administración para ellos.
- Node.js no está instalado en esta máquina, así que `apps/web` no compila todavía; el frontend se
  hará por Docker.

## Comandos

```powershell
cd apps/api
.\mvnw.cmd test                 # 52 pruebas
.\mvnw.cmd package              # jar ejecutable
docker compose config           # validar Compose
```