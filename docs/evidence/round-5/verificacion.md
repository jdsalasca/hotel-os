# Ronda 5 — Conectores OTA

Fecha: 2026-10-06. Rama `develop`.

## Qué se implementó y qué NO

**Implementado y verificado localmente:** cliente HTTP con timeouts y reintentos, tres conectores
siguiendo la documentación oficial de cada proveedor, registro de estado real, bitácora en SQLite y
pantalla de administración con reintento controlado.

**Conexión validada: ninguna.** No hay credenciales de ningún canal. Los tres están en
`NO_CONFIGURADO` y la pantalla lo dice, con el bloqueo exacto. Nada se presenta como sincronización
en vivo.

## Módulo `co.hotel.ota`

| Clase | Responsabilidad |
|---|---|
| `ConectorOta` | Contrato mínimo común: canal, versión de API consultada, sincronizar reservas |
| `HttpClienteOta` | Timeouts, reintentos prudentes, detalle saneado |
| `BookingConector` | Token-based auth oficial, intercambio de token y lectura de `OTA_HotelResNotif` |
| `DespegarConector` | Cabecera `x-apikey`, lectura de reservas por `HotelCode` |
| `AirbnbConector` | OAuth 2.0 con renovación de token y scopes de calendario y reservas |
| `IntegracionesService` | Construye conectores desde el entorno, sincroniza, actualiza estado y bitácora |
| `OtaSyncRepository` | Bitácora `ota_syncs` y mapeos `channel_mappings` |
| `CanalConfig` / `CanalEstadoRegistry` / `SyncResult` / `Bitacora` | Configuración inmutable, estado y redacción |

## Decisiones que vienen de la documentación, no de suposiciones

- **Booking**: el esquema credential-based (Basic) se apagó el 31/12/2025. El conector usa solo
  token-based: `POST .../token-based-authentication/exchange` y luego `Authorization: Bearer`.
- **Despegar**: la API key viaja en `x-apikey`, no como bearer. El canal hotelero exige además
  acuerdo de channel manager, mTLS, HotelCode y certificaciones; la API key sola no habilita eso.
- **Airbnb**: no hay API pública para anfitriones individuales; solo socios de empresa aprobados con
  NDA, API Terms y revisión de seguridad. OAuth 2.0 con tokens que expiran.

## Política de reintentos

Se reintenta solo lo transitorio: 5xx, 429 y fallos de red. Un 4xx es una decisión del proveedor
(credenciales, validación) y repetirlo gasta cuota sin arreglar nada. Un timeout **jamás** se reporta
como éxito.

## Pruebas

```
.\mvnw.cmd test
Tests run: 100, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

25 pruebas nuevas. Las de los tres conectores corren contra **servidores HTTP reales en localhost**
(`com.sun.net.httpserver`), no mocks: se comprueba qué cabecera viaja, qué ruta se llama, que un 401
no reintenta y que un secreto nunca llega al detalle del fallo.

## Verificación contra la aplicación real

Sin credenciales de proveedor, contra la app arrancada:

```
GET  /api/admin/integraciones
  BOOKING : estado NO_CONFIGURADO, requisitos: credenciales faltantes
            (BOOKING_CLIENT_ID, BOOKING_CLIENT_SECRET, BOOKING_HOTEL_ID),
            acceso de partner/onboarding vigente y certificaciones
  DESPEGAR: NO_CONFIGURADO, faltan DESPEGAR_API_KEY y DESPEGAR_HOTEL_CODE
  AIRBNB  : NO_CONFIGURADO, faltan AIRBNB_CLIENT_ID, AIRBNB_CLIENT_SECRET, AIRBNB_LISTING_IDS

POST /api/admin/integraciones/BOOKING/sincronizar -> 502 {"exitosa": false}
POST /api/admin/integraciones/EXpedia/sincronizar -> 400 (canal desconocido)
```

El 502 es deliberado: "el proveedor lo rechazó" no es lo mismo que "error del servidor", y el panel
no debe mostrar un error verde.

## Pendiente para declarar una integración operativa

1. Ser aceptado como partner / channel manager / software partner según el proveedor.
2. Credenciales de máquina y onboarding en su sandbox.
3. Mapeos de habitaciones, tipos y planes (`channel_mappings`).
4. Pruebas de sandbox del proveedor y sus certificaciones.
5. Solo entonces una llamada autorizada real habilita el estado `CONECTADO` en producción.