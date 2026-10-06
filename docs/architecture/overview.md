# Arquitectura — monolito modular, instancia única

Fecha: 2026-10-06. Versiones verificadas contra Maven Central y documentación oficial en esa fecha.

## Decisión
Monolito modular en un repo. `apps/api` (Spring Boot) + `apps/web` (React/Vite) + `docs/` + Compose.
Despliegue: **una** instancia backend + SQLite en volumen nombrado + proxy HTTPS.

## Módulos backend (`co.hotel.*`)

| Paquete | Responsabilidad |
|---|---|
| `config` | `HotelProperties` (configuración tipada), `DataSource`, `SecurityConfig`, `HealthController` |
| `admin` | `AdminInitController` (arranque con token secreto), `AdminAuthController` (login/logout con sesión) |
| `seguridad` | `LoginThrottle` (límite de intentos). Instancia única → contador en memoria; al escalar debe ir a la base |
| `reservas` | `Reserva`/`CrearReserva`/`EstadoReserva`/`Origen`, `ReservaService` (reglas), `ReservaRepository` (SQL), `SqliteDataSources`, `SqliteTransactionExecutor`, `RoomSelector` |
| `auditoria` | `AuditoriaService`/`AuditoriaRepository`: rastro de cambios de estado (se escribe en la misma transacción) |
| `ota` | `Canal`, `CanalConfig` (inmutable), `CanalEstadoRegistry` (estado), `SyncResult`, `Bitacora` (redactor) |

Pendientes de construir (constan en `docs/plan.md`): `hotel`, `rooms`, `disponibilidad`, `canales`,
`indicadores`, `recibos`, conectores OTA.

## Reglas transversales
- Un `@Service` con las reglas de negocio, un `@Repository` con SQL explícito, un controller que solo
  traduce HTTP. Sin lógica de negocio en controllers; sin `new Servicio()` a mano.
- Configuración por `@ConfigurationProperties`, no `@Value` suelto.
- Errores de negocio con excepción propia (`DatosInvalidosException`, `SinDisponibilidadException`),
  traducidos a HTTP en el borde.

## Persistencia y concurrencia
`hotel.jdbc-path` (por defecto `./data/hotel.sqlite3`; en Docker `/data/hotel.sqlite3`).
`SqliteDataSources.paraRuta(...)` configura WAL, `busy_timeout`, claves foráneas y
`TransactionMode.IMMEDIATE`. **El modo IMMEDIATE es lo que hace segura la comprobación de solapes**:
SQLite abre transacciones diferidas por defecto, así que dos escrituras concurrentes podrían pasar
ambas la lectura de disponibilidad y fallar al insertar. Tomando el candado de escritura desde el
inicio, `ReservaService.crear` decide dentro de la transacción y una de las dos recibe
`SinDisponibilidadException` — nunca una sobreventa silenciosa.

**Límite asumido:** una sola instancia de escritura sobre el archivo. No escalar horizontalmente.
Ruta futura si el volumen lo exige: migrar a PostgreSQL (el SQL es explícito y casi portable).

## Migraciones
Flyway con `spring-boot-starter-flyway` + `flyway-database-nc-sqlite`. Esquema en
`src/main/resources/db/migration/V1__esquema_base.sql`. Sin datos de demostración: el hotel registra
su inventario antes de recibir reservas.

## Seguridad
- Sin contraseña por defecto ni usuario en memoria. Primer administrador solo con `ADMIN_INIT_TOKEN`
  del entorno, un solo uso.
- `DelegatingPasswordEncoder`: el hash se guarda como `{bcrypt}$2a$12$…`. Un `BCryptPasswordEncoder`
  pelado devuelve `matches()=false` para toda contraseña y el login nunca valida.
- CSRF **activo** con `CookieCsrfTokenRepository` (React lee `XSRF-TOKEN`, reenvía `X-XSRF-TOKEN`)
  y `CsrfTokenRequestAttributeHandler` con atributo nulo, sin lo cual el token diferido nunca se
  materializa y toda escritura del SPA responde 403.
- Sesión con `changeSessionId` (fijación de sesión) y cookie `HttpOnly`.
- Secretos solo en variables de entorno. Nunca en código, base, imágenes, logs ni Git.

## Versiones fijadas (verificadas 2026-10-06)

| Artefacto | Versión | Nota |
|---|---|---|
| `spring-boot-starter-parent` | 4.1.1 | Última GA. 4.2.0-M2 es milestone |
| `org.xerial:sqlite-jdbc` | 3.53.4.0 | Última en Central |
| `org.flywaydb:flyway-core` + `flyway-nc-core` | 13.9.0 | Boot gestiona 12.4.0, incompatible con `nc-core` 13.x: hay que fijar la serie completa |
| `org.flywaydb:flyway-database-nc-sqlite` | 13.9.0 | **No existe** un artefacto `flyway-database-sqlite` en Central (404); SQLite solo está en la edición NC |

Notas de Boot 4.1.1 que_costaron_bugs: `AutoConfigureMockMvc` vive en `spring-boot-webmvc-test`
(fuera de `starter-test`), Jackson es v3 (`tools.jackson.databind`) y `DaoAuthenticationProvider`
recibe el `UserDetailsService` por constructor.