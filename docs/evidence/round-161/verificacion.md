# Ronda 161 - Origen y movimientos con su nombre + E2E del dinero

## Hallazgo en E2E real
Stack local throwaway con inventario sembrado (demolido después): el comprobante
rotulaba "Estado" a una fila que dice origen e historial. El estado real ya sale
en la etiqueta de arriba ("Pendiente de confirmación").

## Cambio
- `PaginaConsulta`: la fila se parte en `Origen` ("Reserva creada desde WEB") y
  `Movimientos` ("N movimientos registrado(s)"). Sin tocar datos ni estilos.

## TDD rojo-verde
- Test nuevo a nivel página (flujo consulta con mock): sin el cambio falla por
  rótulos ausentes; con él, 6/6 en el archivo.

## Verificación (salida real, 2026-10-09)
- `vitest` 24 archivos / 95 tests en verde; `tsc --noEmit` exit 0.
- E2E en navegador contra stack local (búsqueda → oferta → reserva H-F469608A →
  consulta → comprobante): todo el camino del dinero funciona con datos reales;
  captura con los rótulos nuevos — ver `comprobante-origen.png`. Stack demolido
  (`down -v`).
- Backend sin cambios en la ronda.

## Despliegue
- Merge a `develop`, push y `compose up -d --build` en TopNUC con health OK.
