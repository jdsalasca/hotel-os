# Ronda 177 - Cada lugar dice a qué distancia está del hotel

## El hueco (el último punto del feedback original sin resolver)
El usuario pidió *"que salga qué tan lejos o cerca está de las ubicaciones"* y un
mapa que mostrara la distancia del hotel hacia sitios y experiencias. `/api/lugares`
devolvía el punto del hotel y cada sitio **con coordenadas, pero sin distancia**:
el mapa era un mapa, no una respuesta a la pregunta.

## Cambio
- `LugaresController.publicos()` añade `metros` a cada lugar, calculado con haversine
  (radio medio terrestre 6371 km) y redondeado al metro. Sin API nueva ni
  dependencia nueva: es aritmética.
- **Sin hotel ubicado no se inventa distancia**: si falta lat/long del hotel, cada
  lugar sale sin `metros`. Un número inventado es peor que un campo ausente.
- Sin cambios de frontend: el mapa ya pinta las fichas de cada lugar y este campo
  es lo que les falta para poder decir "a X m".

## TDD rojo-verde
- `lugaresConDistancia`: **RED** sin el cambio
  (`Expected a non-empty value at JSON path "$.lugares[?(@.nombre=='Al lado')].metros" but found: []`).
  **GREEN** con él. Los valores se comprueban con números, no con strings: lo que
  está a un par de calles tiene que dar **< 300 m** y lo que está en Santiago de
  Chile **> 1.000.000 m**.
- `sinHotelNoHayDistancias`: **RED** sin el cambio, **GREEN** con él.

## Defecto de aislamiento que salió en el camino
`crudConValidacion` **ya fallaba antes de tocar nada**: `expected:<false> but was:<true>`.
No lo rompió mi cambio: la base es un `static final` compartida por los tres métodos,
así que el hotel que ubica uno se le queda a los siguientes. Con la distancia
dependiendo del hotel, ese defecto se volvía visible. Se arregla con un
`@BeforeEach` que borra `hotel_config.latitud/longitud` y `lugares_interes`: sin eso
probar "sin hotel ubicado" era imposible.

## Verificación (salida real)
- `LugaresTest`: `Tests run: 3, Failures: 0, Errors: 0` (incluye el test
  preexistente que fallaba).

## Nota de entorno
Esta ronda se hizo en un worktree limpio de `develop`, no en el checkout
compartido: allí una migración `V20__condiciones_acordadas.sql` sin commitear (sin su
`V19`) rompe el arranque de Spring y tumba toda la suite backend
(`duplicate column name: hora_entrada`). Ese archivo es de otro agente y no se tocó.