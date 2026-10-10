# Ronda 194 - El encuadre del mapa se ajusta a los sitios

## El hueco
La ronda 193 añadió los marcadores de los sitios, pero dejó el encuadre del mapa con el
margen fijo que traía el hotel (±0.03°, unos **6 km de lado**). Medido en la captura de
193: el mapa salía a escala de comarca y los tres pines —hotel y dos sitios a 300 m—
**se solapaban en el mismo punto de pantalla**. Solo se veía un pin.

Además había un fallo escondido: con ese margen fijo, un sitio a más de ~3 km del hotel
**quedaba fuera del encuadre**, sin verse y sin aviso.

## El cambio
- `PaginaInicio.tsx`: helper `encuadreDelMapa(hotel, lugares)` que calcula el recuadro
  real sobre todos los puntos con aire alrededor, y con **dos topes**:
  - mínimo `0.0025°`: sin él, un sitio pegado al hotel dejaría el mapa en un punto;
  - máximo `0.06°`: sin él, un sitio a 30 km mandaría el hotel a una esquina diminuta.
- El aire escala con la dispersión (35% del medio), no es fijo.
- Sin estilos nuevos; la lista de la derecha no cambia.

## TDD rojo-verde
- `el encuadre se ajusta a los sitios para que sus pines no se solapen`:
  rojo `expected 0.060000000000002274 to be less than 0.01` (6 km de lado);
  verde con ancho y alto por debajo de 0.01° (~1 km) y los dos extremos dentro.
- `el encuadre se ensancha si hay un sitio lejos`: rojo
  `expected -74.0777 to be less than or equal to -74.1` — **el sitio lejano quedaba
  fuera del mapa**; verde con el recuadro amplifierso > 0.05° y cubriéndolo.
- `sin sitios, el encuadre usa el margen mínimo`: fija el valor exacto (0.0025°) para que
  el mínimo no se convierta en un zoom inservible.
- Archivo 30/30. Frontend completo: **150/150**, `tsc --noEmit` limpio.

## Verificación real (contenedores)
- Hotel en Villa de Leyva (5.6672, -74.0477) y dos sitios creados por la API
  (`Casona del Río` a 284 m, `Plaza de Bolívar` a 321 m).
- Playwright: 3 marcadores en la URL, `erroresJS=0`, sin desborde en 1440 ni 390 px.
- **Antes/después en las capturas**: en `01-mapa-escritorio.png` de 193 el mapa salía a
  escala de comarca (carretera 60, sin calles del casco urbano); en el de 194 se ven las
  calles del pueblo, el río y el parque: el encuadre bajó de ~6 km a ~1 km de lado.

## Nota de coordinación
- `npm run test:typecheck` sigue rojo por los dos errores preexistentes de
  `PaginaAdminLugares.test.tsx` (R189, propiedad `metros` fuera del tipo). Comprobado
  con `git stash` que fallan igual en `origin/develop` limpio: no son de esta ronda y no
  se tocaron.