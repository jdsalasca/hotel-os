# Ronda 82 — El calendario cuenta habitaciones y agrupa el mínimo por moneda (Etapa I)

Fecha: 2026-10-08.

## Dolor

1. `calendarioMensual()` contaba `ofertas.size()` (parejas habitación×plan) como
   "habitaciones libres": con dos planes cubriendo una habitación decía 2.
2. El mínimo se calculaba sobre todas las ofertas y se etiquetaba con la moneda de la
   primera: con planes en COP y USD, `min(100000, 5000)` con etiqueta COP.

## Cambio

- `DiaCalendario` ahora trae `disponibles` (habitaciones distintas) y `precios`
  (mínimo por moneda, ordenado), en vez de `desdeCents`/`moneda` únicos.
- `GET /api/disponibilidad/calendario` devuelve `precios: [{moneda, desdeCents}]`
  (siempre presente, vacío si no hay oferta). Sin conversión entre monedas.
- La pública muestra `4 · EUR 72` o `COP 100.000 · USD 50` por día, igual en celda y
  `aria-label`.

## Verificación real

```text
2 tests de servicio (cuenta habitaciones, agrupa por moneda) + 1 de contrato
multimoneda + actualización del existente: vistos fallar antes (símbolos nuevos).
En el camino: códigos de habitación/test duplicados en la BD compartida de la clase
(701, fechas de otros tests) → códigos y fecha 2028-01 propios.
InventarioServiceTest + DisponibilidadControllerTest + CalendarioControllerTest
verdes; suite en worktree limpio sobre HEAD: 279 corridos, 0 fallos, 0 errores,
1 omitida. BUILD SUCCESS.
docker compose build web → tsc + vite ok. npm test → 20/20. test:typecheck → 0.
Prueba viva (imágenes reconstruidas): calendario con `precios` por día y la pública
pinta «4 habitaciones libres desde EUR 72». Captura: ../round-82/calendario-desktop.png.
```

## Archivos

- Tocados: `InventarioService.java`, `DisponibilidadController.java`,
  `InventarioServiceTest.java`, `DisponibilidadControllerTest.java`,
  `PaginaInicio.tsx`, `docs/plan.md`.
- Recuperado aquí porque `round-82/verificacion.md` lo ocupa la Ronda 82 del colega
  (Bienvenido/panel); el original salió en `a703dd3` y sigue ahí en el historial.

## Riesgos y límites

- Cambio de contrato del calendario (de `desdeCents`/`moneda` a `precios`): el único
  consumidor es la web del repo, actualizada en la misma ronda. Verificado que nada
  más lee `desdeCents`.
- La suite del checkout quedó en rojo por WIP ajeno a mitad de refactor
  (`ManejadorAdminOauth2` con nueva firma vs `Oauth2AdminTest` viejo): no tocado,
  verificado en worktree limpio y eliminado después.
- Coordinación: 81 era del colega (su archivo intacto); 82 verificado libre.
  Trabajo del colega respetado en todo.
