# Ronda 62 - Servicios por tipo y moneda cerrada

Fecha: 2026-10-07.

## Dolor
- Sin servicios: la oferta no decía si el tipo tiene wifi o jacuzzi.
- Moneda en campo abierto: "cop", "COP" y "COL" convivían como tres monedas.

## Cambio
- Migración V13: `amenidades` (catálogo sembrado con 10) + `room_type_amenidades`.
- `GET /api/amenidades` y `GET /api/amenidades/por-tipo?ids=` públicos;
  `PUT /api/admin/tipos/{id}/amenidades` reemplaza la marca (ids inventados → 400,
  tipo inexistente → 404, sin sesión → 401).
- Panel: moneda del plan en select ISO cerrado; formulario "Servicios del tipo" con
  checkboxes que se guardan por tipo.
- Ofertas públicas: pastillas con los servicios del tipo (una lectura por búsqueda).
- Estilos `grupo-chequeos`, `chequeo`, `servicios` con tokens; variante `boton--claro`.

## Verificación real
- `AmenidadesTest` nuevo (catálogo de 10, reemplaza-no-acumula, 400/404/401): verde.
- `tsc` + `vite build` en el build Docker del servidor.
- Despliegue: `git reset --hard origin/develop` + `up -d --build`; V13 aplicada;
  `/api/amenidades` con 10; pastillas visibles en ofertas con servicios marcados.
- Archivos del otro agente intactos: solo se commitearon los de esta ronda (verificando
  con `git status` que inventario queda fuera).

## Archivos
- Nuevos: `V13__amenidades.sql`, `AmenidadesRepository.java`, `AmenidadesController.java`,
  `AmenidadesTest.java`, este archivo.
- Tocados: `SecurityConfig.java` (solo la línea de `/api/amenidades**` pública),
  `cliente.ts` (`api.put`), `PaginaAdminInventario.tsx`, `PaginaInicio.tsx`,
  `_componentes.scss`.
