# Ronda 63 - GPS, distancias y mapa administrable

Fecha: 2026-10-07.

## Dolor
El hotel no podía decir dónde queda ni qué hay cerca: sin punto en el mapa, sin distancias,
sin panel para gestionarlo.

## Cambio
- Migración V14: `lugares_interes` (nombre, descripción, lat/lng con CHECK, activo).
- `GET /api/lugares` público (hotel ubicado + activos), CRUD `/api/admin/lugares` con
  validación de rangos (400), 404 si no existe, escritura solo ADMIN.
- `latitud`/`longitud` entran a `hotel_config` administrable y público (vacío = sin ubicar).
- Panel: lat/lng en Hotel (number con decimales) + página Mapa (`/admin/lugares`) con alta,
  mostrar/ocultar y eliminar; enlace en el pie del panel.
- Landing "Encuéntranos": marco OpenStreetMap embebido (sin keys ni librerías), sitios con
  distancia haversiana y "Cómo llegar"; la sección no existe si no hay nada que mostrar.
- CSP `frame-src` abierta solo a openstreetmap.org en nginx y vercel.json.

## Verificación real
- `LugaresTest` nuevo (sin ubicar, CRUD, 400/401/404): verde.
- `HotelConfigControllerTest` vecina por correr (nuevas claves).
- `tsc` + `vite build` en el build Docker del servidor.
- Despliegue: `git reset --hard origin/develop` + `up -d --build`; V14 aplicada; `/api/lugares`
  con `ubicado:false`; mapa visible tras ubicar + primer sitio (captura).
- Archivos del otro agente intactos: paquete nuevo `co.hotel.lugares`, `HotelConfigService`
  solo suma claves, `SecurityConfig` solo suma matcher.

## Archivos
- Nuevos: `V14__lugares_interes.sql`, `LugaresRepository.java`, `LugaresController.java`,
  `LugaresTest.java`, `PaginaAdminLugares.tsx`, este archivo.
- Tocados: `HotelConfigService.java`, `SecurityConfig.java`, `PaginaAdminHotel.tsx`, `App.tsx`,
  `PaginaInicio.tsx`, `_componentes.scss`, `Dockerfile` (web, CSP), `vercel.json`.
