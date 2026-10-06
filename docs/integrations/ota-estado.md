# Integraciones OTA — estado real y requisitos

Verificado contra la documentación oficial el **2026-10-06**. Actualizado en la ronda 3.

> Principio: **nada simulado aparece como conectado**. Para afirmar `CONECTADO` hacen falta dos cosas
> a la vez: credenciales del entorno **y** una llamada autorizada exitosa contra el proveedor.
> Llenar campos no cambia el estado.

## Estado en esta ronda

| Canal | Estado | Bloqueo exacto |
|---|---|---|
| Booking.com | `NO_CONFIGURADO` | Falta ser Connectivity Partner + credenciales de máquina |
| Despegar | `NO_CONFIGURADO` | Falta acuerdo channel manager + API key + mTLS |
| Airbnb | `NO_CONFIGURADO` | Falta programa de socios aprobado + NDA + revisión de seguridad |

**Código implementado:** configuración por canal con sus propias variables, estados
(`NO_CONFIGURADO`, `ACCESO_PENDIENTE`, `SANDBOX`, `CONECTADO`, `ERROR`, `DESCONECTADO`), redactor de
bitácora y registro de estado (`co.hotel.ota`). Cobertura: 30 tests.
**Conexión validada: ninguna.** No hay llamadas a proveedores, así que no hay sandbox ni producción.

## Booking.com Connectivity APIs

Docs: `developers.booking.com/connectivity/docs` (Authentication, Token-based authentication,
Reservations API, Acknowledging new reservations). Consultadas 2026-10-06.

- **Autenticación vigente: token-based.** `POST https://connectivity-authentication.booking.com/
  token-based-authentication/exchange` con `client_id` y `client_secret` devuelve un token JWT de
  ~1 hora; después `Authorization: Bearer {JWT}`. El esquema **credential-based (Basic) se apagó el
  31 de diciembre de 2025**: implementarlo hoy es implementar un camino muerto.
- Endpoints: `https://supply-xml.booking.com` (sin PCI) y `https://secure-supply-xml.booking.com`
  (reservas). Reservas nuevas: `GET/POST .../hotels/ota/OTA_HotelResNotif`; sin acuse de recibo,
  Booking reenvía la reserva por correo tras un tiempo.
- Acceso: acuerdo Connectivity Partner, machine accounts por propiedad, IDs de alojamiento,
  onboarding, sandbox, certificaciones (p. ej. tipos de precio) y límites.
- Variables: `BOOKING_CLIENT_ID`, `BOOKING_CLIENT_SECRET`, `BOOKING_HOTEL_ID`, `BOOKING_ENV`
  (`sandbox`/`prod`) + mapeos de unidad y rate plan.

## Despegar

Docs: `channel.despegar.com/portal/documentation` (Rates, Availability-Restrictions, Inventory) y
`api-docs.despegar.com` (API B2B `/v3`, `x-apikey` + mTLS). Consultadas 2026-10-06.

- Actualizaciones de precio vía `OTA_HotelRatePlanNotifRQ` con `HotelCode`, `RatePlanCode` y
  `ChargeTypeCode` (19/21); `TaxPolicy` distingue `AmountAfter`/`BeforeTax`.
- Certificación: evidencia en PDF con capturas numeradas (no un enlace), reservas en sandbox, PCI
  válido si se manejan tarjetas o tokens, mapeos de contenido (hotel, planes de comida, tipos de
  cargos, amenidades).
- Variables: `DESPEGAR_API_KEY`, `DESPEGAR_HOTEL_CODE`, `DESPEGAR_ENV` + mapeos Room/RatePlan.

## Airbnb

Docs: `developer.airbnb.com` / `developer.withairbnb.com` y API Terms (art. 1.3). Consultadas 2026-10-06.

- **No hay API pública para anfitriones individuales.** Solo socios de empresa aprobados
  (Preferred/Software Partner Program).
- Requisitos: NDA mutuo, API Terms + Partner Specific Terms, **revisión de seguridad de datos**,
  implementación de funciones obligatorias en 6 meses de su lanzamiento.
- Scopes observados en la especificación de Homes: `listings:read/write`, `reservations:read/write`,
  `calendar:read/write`, `messages:read/write`, `reviews:read`.
- Variables: `AIRBNB_CLIENT_ID`, `AIRBNB_CLIENT_SECRET`, `AIRBNB_LISTING_IDS`, `AIRBNB_ENV`.

## Lo que no se hizo, y por qué

- Sin scraping, endpoints privados ni automatización de navegador: solo APIs documentadas.
- Sin agregadores no oficiales.
- Ninguna integración marca `CONECTADO` por tener los campos llenos: `CanalEstadoRegistry` exige un
  registro de sincronización **exitosa** y entorno `prod`. Con `sandbox` el techo es `SANDBOX`.