# Integraciones OTA — estado real y requisitos (verificado 2026-10-06)

> Nada simulado aparece como conectado. Sin credenciales, todo estado es `no configurado`. Para afirmar `conectado` se exige llamada autorizada + sandbox.

## Booking.com Connectivity APIs
- Docs: `https://developers.booking.com/connectivity/docs` (About, Rates & Availability `.../connectivity/docs/ari`, Reservations `.../connectivity/docs/reservations-api/reservations-overview`, Rooms API units). Consultada 2026-10-06.
- Funciones: inventario/disponibilidad, tarifas/restricciones (Standard, RLO/Derived, OBP, LOS — OBP/LOS con requisitos técnicos + certificación), reservas (OTA_HotelResNotif), contenidos/fotos, promociones, mensajería, pagos.
- Base URLs: `https://supply-xml.booking.com` (no PCI) y `https://secure-supply-xml.booking.com` (reservas PCI). HTTPS obligatorio. Formatos OTA/BXML/JSON (Rates & Reservations hoy en XML).
- Acceso: ser Connectivity Partner (acuerdo + Account Manager), IDs de hotel/alojamiento, permisos/scopes por connection type, onboarding, sandbox, certificación (p.ej. pricing types, carga ≥1 año rates+availability), límites/allowlist.
- Estado actual: NO CONFIGURADO. Bloqueo exacto: falta solicitud Partner + credenciales + HotelCode + entorno + certificación. No usar scraping/navegador/APIs privadas.
- Campos a configurar (solo nombres, valores en env): `BOOKING_CLIENT_ID/SECRET`, `BOOKING_HOTEL_ID`, `BOOKING_ENV=sandbox|prod`, mapeos unit/rate-plan.

## Despegar (Channel / Hotel APIs)
- Docs: `https://channel.despegar.com/portal/documentation/` (Get Hotel Info, List RoomRates, Get/Update Availability-Restrictions, Get Inventory, Update Rates `/v1/hotels/rate-plans/update` OTA_HotelRatePlanNotifRQ con HotelCode/RatePlanCode/ChargeTypeCode 19/21, TaxPolicy AmountAfter/BeforeTax) y `https://api-docs.despegar.com/docs/*` (ecosistema B2B `/v3`, `x-apikey` + mTLS). Consultada 2026-10-06.
- Certificación: evidencia PDF con capturas numeradas (no vale link), reservas en Sandbox, PCI válido si tarjeta/token, mapeos contenido (hotel, meal plans, fee types, amenities), logs con `include=hints,exchange_policies` en prebook.
- Estado: NO CONFIGURADO. Bloqueo: falta acuerdo channel-manager + `DESPEGAR_API_KEY` test/prod + `DESPEGAR_HOTEL_CODE` + mTLS + HotelDO/contenido + certificación.
- Campos: `DESPEGAR_API_KEY`, `DESPEGAR_HOTEL_CODE`, `DESPEGAR_ENV`, mapeos Room/RatePlan.

## Airbnb (Homes API vía programa oficial)
- Docs: `https://developer.withairbnb.com/` + `https://developer.airbnb.com/` (Homes API: listings, pricing/availability, reservations, mensajes/reviews) y `https://airbnb.com/partner`. Consultada 2026-10-06.
- Acceso restringido: sin API pública para hosts individuales; solo partners empresa aprobados (Preferred/Software Partner Program): NDA mutuo, API Terms + Partner Specific Terms, data security review, scopes por programa, Partner Manager, sandbox/onboarding/inventario. Límites y certificaciones según programa.
- Estado: NO CONFIGURADO. Bloqueo: falta aplicación partner + NDA + security review + `AIRBNB_CLIENT_ID/SECRET`, `AIRBNB_LISTING_IDS`, `AIRBNB_ENV`.
- Nota: existen agregadores no oficiales (ej. StayingAPI/Scouting) — PROHIBIDOS aquí (regla: solo APIs oficiales, sin scraping/browser).

## Pantalla admin (contrato)
Por canal: estado {no configurado|acceso pendiente|sandbox|conectado|error|desconectado}, última sync + resultado, identificadores/mapeos, permisos/requisitos pendientes, errores recientes (sin secretos/PII). Reintento controlado + conciliación. Ante caída OTA: operar local, registrar, reintentar, nunca sobreventa silenciosa.
