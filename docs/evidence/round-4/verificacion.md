# Ronda 4 — Inventario, tarifas y bloqueos

Fecha: 2026-10-06. Rama `develop`.

## Qué resuelve
Antes el hotel tenía que escribir SQL a mano para dar de alta una habitación: no había forma de
configurar inventario, precios ni bloqueos desde la aplicación. Ahora el hotel registra lo suyo por
API y la web pública solo ofrece lo que está dado de alta **y** tiene precio configurado.

## Migración

`V2__tarifas_y_canales.sql`, versionada e inmutable:

- `rate_plans` (código, nombre, **moneda ISO 4217 decided por el hotel**, activo)
- `rates` (precio por noche en centavos, `min_estancia`, `max_estancia`, `cerrado`,
  `UNIQUE(rate_plan_id, room_type_id, fecha)`)
- `channels` con los cinco canales (solo `WEB` queda activo por defecto)
- `channel_mappings` (mapeo habitación/tipo/plan ↔ unidad del canal)
- `ota_syncs` (bitácora de sincronización, con el detalle ya saneado)

**Sin precios, nombres ni monedas inventados.** Sin filas en `rates`, la web no muestra
disponibilidad: la ausencia de precio es un dato, no un cero.

## Módulo `co.hotel.inventario`

| Clase | Responsabilidad |
|---|---|
| `InventarioService` | Reglas: alta de tipos y habitaciones, estados, bloqueos, y qué se ofrece |
| `InventarioRepository` | SQL de inventario, bloqueos y búsqueda de disponibilidad |
| `TarifaService` | Planes y precios; valida moneda ISO 4217 |
| `TarifaRepository` | SQL de planes y precios por noche |
| `InventarioAdminController` | API de administración: tipos, habitaciones, planes, tarifas, bloqueos |
| `DisponibilidadController` | Búsqueda pública `/api/disponibilidad` |

Reglas que se decidió y su porqué:

- **Intervalo semiabierto** `[llegada, salida)`: una reserva que sale el 5 no bloquea otra que
  llega el 5.
- **`FUERA_DE_SERVICIO` no borra la habitación**: la retira de la oferta y la conserva en el
  inventario, para no perder su historial.
- **Bloqueo con `room_id` nulo** = cierre de todo el hotel.
- **Estancia mínima/máxima y noche cerrada** excluyen la habitación de la oferta del periodo.
- **Más huéspedes que la capacidad del tipo** no se ofrece.

## Pruebas

```
.\mvnw.cmd test
Tests run: 70, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

18 pruebas nuevas: 13 de `InventarioServiceTest` (SQLite real) y 5 de `DisponibilidadControllerTest`
(app arrancada).

## Verificación contra la aplicación real

```powershell
java -jar target/hotel-api-0.1.0.jar --hotel.jdbc-path=... --server.port=8098
```

```
POST /api/admin/tipos      -> {"id":1,"codigo":"DOBLE","nombre":"Habitación doble","capacidadMax":3}
POST /api/admin/habitaciones -> {"id":1,"codigo":"101","roomTypeId":1,...,"estado":"ACTIVA"}
POST /api/admin/planes     -> {"id":1,"codigo":"PES","nombre":"Plan pesos","moneda":"COP","activo":true}

GET /api/disponibilidad (sin tarifas)     -> {"ofertas":[]}          <- no inventa precio
GET /api/disponibilidad (con 2 noches)     -> totalCents 300000, moneda COP
GET /api/disponibilidad (5 huéspedes)      -> {"ofertas":[]}          <- capacidad del tipo = 3
POST /api/admin/bloqueos (101, 01→10 nov)  -> {"bloqueoId":1}
GET /api/disponibilidad (tras bloqueo)     -> {"ofertas":[]}
```

## Bug encontrado y corregido en el camino

`/api/disponibilidad` respondía 401: la ruta pública faltaba en las reglas de autorización. Ahora
está en `permitAll` junto con `/api/health` y `/api/reservas`.

## Lo que sigue

Ronda 5: conectores OTA (uno por canal, con reintentos, idempotencia y bitácora). Ninguna integración
se declarará conectada sin llamada autorizada.