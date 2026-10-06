# Modelo de datos V1 (SQLite, intervalos inequívocos)
Fechas como TEXT ISO `YYYY-MM-DD` (noche: [llegada, salida) ). Horas check-in/out configurables, no inventadas.

```sql
hotel_config(clave TEXT PK, valor TEXT) -- nombre, moneda, impuestos, políticas: todo configurable, sin defaults inventados
users(id INTEGER PK, email TEXT UNIQUE, hash TEXT, rol TEXT, activo INTEGER, creado_en TEXT)
room_types(id INTEGER PK, codigo TEXT UNIQUE, nombre TEXT, capacidad_max INTEGER, descripcion TEXT)
rooms(id INTEGER PK, codigo TEXT UNIQUE, room_type_id FK, piso TEXT, estado TEXT) -- estado: ACTIVA/MANTENIMIENTO
rate_plans(id INTEGER PK, codigo TEXT UNIQUE, nombre TEXT, moneda TEXT)
rates(id INTEGER PK, rate_plan_id FK, room_type_id FK, fecha TEXT, precio_cents INTEGER, min_estancia INTEGER, max_estancia INTEGER, cerrado INTEGER, UNIQUE(rate_plan_id,room_type_id,fecha))
blocks(id INTEGER PK, room_id FK NULL, room_type_id FK NULL, desde TEXT, hasta TEXT, motivo TEXT) -- mantenimiento/indisponibilidad [desde,hasta)
channels(codigo TEXT PK, nombre TEXT, tipo TEXT) -- WEB, BOOKING, DESPEGAR, AIRBNB, OTRO
channel_mappings(id INTEGER PK, channel_codigo FK, room_type_id FK NULL, room_id FK NULL, rate_plan_id FK NULL, external_id TEXT, UNIQUE(channel_codigo,external_id))
reservations(id INTEGER PK, codigo TEXT UNIQUE, email TEXT, nombre TEXT, llegada TEXT, salida TEXT, huespedes INTEGER, estado TEXT, origen TEXT, idempotencia TEXT UNIQUE, total_cents INTEGER NULL, creado_en TEXT)
reservation_items(id INTEGER PK, reservation_id FK, room_id FK, room_type_id FK, desde TEXT, hasta TEXT)
reservation_history(id INTEGER PK, reservation_id FK, estado_ant TEXT, estado_nuevo TEXT, actor TEXT, en TEXT)
ota_syncs(id INTEGER PK, channel_codigo FK, operacion TEXT, resultado TEXT, detalle TEXT, en TEXT) -- sin secretos ni PII innecesaria
indicators(def TEXT PK, formula TEXT, fuente TEXT, periodo TEXT, unidad TEXT, base TEXT NULL, meta TEXT NULL, responsable TEXT NULL)
indicator_results(id INTEGER PK, def FK, periodo TEXT, valor TEXT NULL, faltante INTEGER) -- faltante=1 distingue dato faltante de cero
audit(id INTEGER PK, actor TEXT, accion TEXT, entidad TEXT, en TEXT)
```

Regla anti-doble-reserva (transaccional): para cada `room_id` en [llegada,salida), no debe existir `reservation_items` vigente ni `blocks` que solape (`nuevo.llegada < existente.hasta AND existente.desde < nuevo.salida`). Se valida en transacción de escritura única. Cancelaciones liberan según reglas configuradas. Ver `apps/api/src/main/resources/db/migration/V1__init.sql`.
