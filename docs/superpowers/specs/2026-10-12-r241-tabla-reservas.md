# R241 — Especificación de la tabla de reservas

## Objetivo

Extraer la tabla y las acciones de cada fila de `PaginaAdminReservas.tsx` a `TablaReservas.tsx`, reduciendo el tamaño de la página sin cambiar el comportamiento que ya usa recepción.

## Contrato visible

- Cada fila conserva código, huésped (nombre o correo como respaldo), fechas, noches, número de huéspedes, origen y estado.
- Las transiciones visibles se derivan exclusivamente de `reserva.siguientes`, enviado por el servidor. Si la lista está vacía, se lee «Sin acciones».
- «Ver detalle» abre la reserva correspondiente. Si hay mensajes nuevos, muestra su conteo con un nombre accesible; con cero, no muestra insignia.
- Al abrir el detalle, la envoltura de la tabla conserva `no-imprimir`.
- La tabla mantiene su caption, encabezados con `scope="col"`, etiquetas responsivas `data-label` y nombres accesibles de las acciones.

## Límites

No cambiar endpoints, formatos de respuesta, filtros, permisos, reglas de transición, actualización periódica, estilos, impresión ni persistencia. No añadir dependencias ni alterar la pantalla de detalle.

## Aceptación

Las pruebas existentes de `PaginaAdminReservas` siguen pasando; una prueba aislada de `TablaReservas` fija las reglas descritas; pruebas, typechecks y build frontend pasan; la página integrada se revisa en escritorio y móvil con datos sintéticos y sin desbordamiento horizontal ni errores de consola.
