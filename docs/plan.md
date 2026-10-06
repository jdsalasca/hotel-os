# Rondas y criterios - Sistema hotelero Villa de Leyva / Sáchica

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

- [x] **Ronda 1 - Contexto y modelo.** Arquitectura, modelo de datos y requisitos OTA documentados.
- [x] **Ronda 2 - Backend y reservas (versión antigua).** Sustituida por la ronda 3: contenía
  defectos que hacían imposible iniciar sesión y no usaba Flyway.
- [x] **Ronda 3 - Reconstrucción del backend + base de OTA.** Evidencia:
  `docs/evidence/round-3/verificacion.md`.
- [x] **Ronda 4 - Inventario, tarifas, canales y bloqueos.** Tipos, habitaciones, planes, tarifas
  por fecha y bloqueos, con API y pantallas de administración. Evidencia:
  `docs/evidence/round-4/verificacion.md`.
- [x] **Ronda 5 - Conectores OTA.** Un conector por canal, cliente HTTP con timeouts y reintentos,
  bitácora y pantalla con reintento.
  **Conexión validada: ninguna**, por falta de credenciales de partner. Los tres canales aparecen
  `NO_CONFIGURADO` con el bloqueo exacto. Evidencia: `docs/evidence/round-5/verificacion.md`.
- [x] **Ronda 6 - Indicadores.** Fases, fórmulas, fuentes, periodos, exportación CSV y vista
  imprimible, con `SIN_DATOS` cuando no hay respaldo verificable. Evidencia:
  `docs/evidence/round-6/verificacion.md`.
- [x] **Ronda 7 - Frontend y calidad visual.** Sistema SCSS, flujos públicos y admin, accesibilidad,
  responsive y capturas verificadas. Evidencia: `docs/evidence/round-7/verificacion.md`.
- [x] **Ronda 8 - Despliegue y operación.** Compose dev/prod, HTTPS, respaldos consistentes,
  restauración probada y guía de despliegue. Evidencia:
  `docs/evidence/round-8/verificacion.md`.

## Criterios de aceptación, verificados con ejecución real

| # | Criterio | Verificación | Resultado |
|---|---|---|---|
| 1 | `docker compose up --build` levanta la app | `docker compose config` + arranque real | ✅ |
| 2 | Una reserva creada en la web aparece en el panel | `H-C7F625DA` → panel: `WEB PENDIENTE` | ✅ |
| 3 | La reserva sobrevive al reinicio | `docker compose down && up` → sigue en la base | ✅ |
| 4 | Doble reserva rechazada | Solape → `{"error":"no hay disponibilidad para esas fechas"}` | ✅ |
| 5 | Las OTAs muestran su estado real | `BOOKING/DESPEGAR/AIRBNB = NO_CONFIGURADO` | ✅ |
| 6 | Los indicadores no inventan datos | `tieneResultado=False` con motivo concreto | ✅ |
| 7 | `compose up --build` funciona en dev y prod | Ambos `config` validan con exit 0 | ✅ |
| 8 | Pruebas verdes | `Tests run: 138, Failures: 0, Errors: 0` | ✅ |
| 9 | Sin secretos en el repositorio | Escaneo del diff antes de cada commit | ✅ |

### Escalado medido de SQLite
- 20 hilos compitiendo por la misma habitación → 1 aceptada, 19 con 409, **0 sobreventas**.
- 48 reservas/segundo de escritura.
- 50 000 reservas → 7,5 MB de archivo.

El límite no es el motor: es que el archivo necesita un disco que sobreviva al redespliegue. Por eso
el backend no puede ir en Vercel (filesystem efímero) y sí en una VM o servicio con volumen.

## Estado real 2026-10-06
- Java 25.0.4.1, Docker 29.8.2, Maven Wrapper 3.9.11 funcionando. `distributionUrl` corregido.
- **138/138 pruebas verdes**. Jar empaquetado y ejecutado; el frontend compila con Node 22 dentro
  de Docker, igual que en Vercel.
- **Node.js sigue ausente en el host**: el frontend se construye y verifica en `node:22-alpine`, sin
  instalar nada en la máquina.
- **Sin credenciales OTA** (esperado): ver `docs/integrations/ota-estado.md` con el bloqueo exacto
  de cada canal. Ninguna integración aparece como conectada.
- Gmail implementado con OAuth 2.0; falta el `GOOGLE_REFRESH_TOKEN` del hotel para probarlo contra
  una cuenta real. Procedimiento en `docs/operations/correo.md`.
- Demostración de extremo a extremo verificada por HTTP contra los contenedores reales, no solo por
  pruebas unitarias.

## Pendiente antes de declarar el sistema en operación
1. Dominio y correo del hotel, y `ADMIN_INIT_TOKEN` generado en el servidor.
2. Credenciales y aprobación de partner de Booking.com, Despegar y Airbnb.
3. Inventario y tarifas reales cargados por el panel.
4. Copia de respaldos externa configurada y probada una vez.
5. Cargar los estilos inline restantes a SCSS y completar el calendario y la gestión de tarifas en
   el panel.