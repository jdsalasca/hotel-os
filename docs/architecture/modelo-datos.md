# Modelo de datos (SQLite, intervalos inequívocos)

Esquema real: `apps/api/src/main/resources/db/migration/V1__esquema_base.sql`.
Este documento **describe lo que existe**; lo previsto va marcado como pendiente.

Fechas como TEXT ISO `YYYY-MM-DD`. Intervalos **semiabiertos** `[desde, hasta)`: la fecha de salida
no ocupa noche, y una reserva que termina el día 5 no bloquea otra que empieza el día 5.

## Implementado (V1)

```sql
hotel_config(clave PK, valor, actualizado_en)              -- nombre, moneda, impuestos, políticas: nada inventado
room_types(id PK, codigo UNIQUE, nombre, capacidad_max)     -- CHECK capacidad_max > 0
rooms(id PK, codigo UNIQUE, room_type_id FK, estado, nombre) -- estado: ACTIVA | MANTENIMIENTO | FUERA_DE_SERVICIO
users(id PK, email UNIQUE, hash, rol, activo, creado_en)    -- rol: ADMIN | STAFF
reservations(id PK, codigo UNIQUE, email, nombre, llegada, salida, huespedes, estado, origen,
             idempotencia UNIQUE, creado_en)                -- CHECK llegada < salida
reservation_items(id PK, reservation_id FK, room_id FK, desde, hasta)
reservation_history(id PK, reservation_id FK, estado_ant, estado_nuevo, actor, en)
blocks(id PK, room_id FK NULL, desde, hasta, motivo)        -- mantenimiento / indisponibilidad
```

Índices para las consultas que decides: `reservation_items(room_id, desde, hasta)`,
`reservations(llegada, estado)`, `blocks(room_id, desde, hasta)`.

Enums validados con `CHECK` en el esquema: `reservations.estado ∈ {PENDIENTE, CONFIRMADA, CANCELADA,
RECHAZADA}` y `reservations.origen ∈ {WEB, BOOKING, DESPEGAR, AIRBNB, OTRO}`. Un origen desconocido se
rechaza en vez de convertirse en silencio en "OTRO".

`reservation_history` se escribe **dentro de la misma transacción** del cambio de estado: si la
auditoría falla, la operación falla.

## Regla anti-doble-reserva

Para un `room_id` y el intervalo pedido, no debe existir una línea de reserva vigente ni un bloqueo
que solape:

```sql
ri.desde < :hasta AND :llegada < ri.hasta     -- reserva vigente (PENDIENTE o CONFIRMADA)
b.desde  < :hasta AND :llegada < b.hasta     -- bloqueo (por habitación o global, room_id NULL)
```

Se evalúa dentro de una transacción `IMMEDIATE`. Cancelar o rechazar deja de contar como vigente y
libera el inventario para esas fechas.

## Idempotencia

`reservations.idempotencia` es `UNIQUE`. La clave viene de la cabecera `Idempotency-Key` o del
cuerpo; si no llega, el servicio genera una. Repetir la misma clave devuelve el código existente sin
crear una segunda reserva. La clave **no** evita el choque de inventario: dos huéspedes distintos con
la misma habitación y fechas reciben 409.

## Pendiente (no existe todavía)

`rate_plans`, `rates` (tarifas y restricciones por fecha), `channels`, `channel_mappings` (mapeos
habitación/tipo/plan ↔ unidad de cada OTA), `ota_syncs` (bitácora de sincronización),
`indicators`, `indicator_results`, `audit`, `receipts`. Nada de esto está en el esquema actual: la
pantalla de tarifas e indicadores no puede mostrar datos que no existan.