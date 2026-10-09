# Ronda 164 - Imprimir y WhatsApp en el comprobante propio

## Cambio
- `ComprobantePropio` (Mis reservas): botón Imprimir (`window.print`, fuera del
  papel con `no-imprimir`) + enlace "Compartir por WhatsApp" con el código y las
  fechas, vía `enlaceWhatsApp` existente (respeta regla de país). Sin teléfono
  no hay enlace. Cero estilos nuevos.

## TDD rojo-verde
- Sin los botones: 2 fallos por contenido; con ellos, 4/4 (print llamado,
  href wa.me con código, ausencia sin teléfono).

## Verificación (salida real, 2026-10-09)
- `vitest` full en verde; `tsc --noEmit` exit 0. Backend sin cambios (el dato
  ya viajaba en `hotel.publicos`).
- Visual: exige sesión; jsdom afirma botones y hrefs exactos.

## Despliegue
- Merge a `develop`, push y `compose up -d --build` en TopNUC con health OK.
