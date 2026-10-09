# Ronda 168 - Cambiar huéspedes desde el panel

## El hueco
La ronda 166 dejó el endpoint `POST /api/admin/reservas/{codigo}/huespedes` probado
en el servidor, pero **el panel no lo exponía**: recepción no tenía forma de corregir
el grupo de una reserva sin cancelar y volver a crear, perdiendo historial y abono.

## Cambio
- `PaginaAdminReservas`: estado `nuevoGrupo`, función `cangerHuespedes` calcada de
  `reasignar`/`cambiarFechas`, y el formulario junto a ellos. Solo aparece en reserva
  vigente (mismo `admiteDinero` que el abono, sin una regla nueva). El campo muestra
  el grupo actual en la etiqueta y avisa de que la capacidad manda.
- Sin estilos nuevos: reutiliza `campo`, `campo__etiqueta`, `campo__ayuda` y
  `boton--secundario--chico` de SCSS.
- Sin cambios en el servidor: el endpoint ya existia de la ronda 166.

## TDD rojo-verde
- 3 tests nuevos en `PaginaAdminReservas.test.tsx`: el formulario aparece con el
  grupo actual, el envio es `{huespedes: 3}` a la ruta correcta, y una reserva
  cerrada no lo ofrece.
- El primero fallo en rojo de una forma informativa: `getByLabelText(/Huéspedes
  \(ahora 1\)/)` no encuentra nada porque React parte ese texto en tres nodos
  (`Huéspedes (`, `ahora`, `1)`) y la expresion regular no casa. El campo se busca
  por `#grupo-huespedes`, que es como lo referencia el `label htmlFor`.
- Verde: 8/8 en el archivo, 106/106 en el frontend.

## Verificacion (salida real, 2026-10-09, stack de contenedor)
Flujo completo en Chrome (no mocks), con una habitacion de capacidad 3:

| Paso | Resultado |
|---|---|
| Poner 9 y enviar | aviso `esa habitación no admite 9 huéspedes en esas fechas`, sin cambios |
| Poner 1 y enviar | 200, historial con `huéspedes 3 → 1 por admin` |

Capturas:
- `01-panel-grupo-antes.png` y `02-grupo-sobre-capacidad.png` (escritorio 1440):
  formulario integrado, rechazo por capacidad visible.
- `03-grupo-cambiado.png`: el cambio aplicado y anotado en el historial.
- `04-movil-grupo.png` (390 px): el bloque apilado y sin desborde.
- `erroresJS=0` en las cuatro pasadas.

## Lo que sigue abierto
- El huesped sigue sin poder cambiar su grupo desde la web: el endpoint
  `POST /api/mis-reservas/{codigo}/huespedes` existe y esta probado, pero
  `PaginaMisReservas` no lo expone.
- Cambiar el grupo no recalcula el saldo a mostrar: el total se recalcula en el
  servidor (el precio depende del número de ocupantes), pero conviene mirarlo en la
  siguiente ronda con un caso de tariff real por huespedes.