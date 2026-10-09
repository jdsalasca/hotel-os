# Ronda 178 - El panel también dice la distancia de cada lugar

## El hueco
R177 puso la distancia en el mapa público (`/api/lugares`). El **panel del hotelero**
(`/api/admin/lugares`) seguía devolviendo el lugar crudo: el hotelero no podía verificar
que un sitio "quedó cerquita" antes de publicarlo.

## Cambio
- `todos()` calcula `metros` igual que la web pública, y ambos comparten ahora un solo
  helper `conDistancia()`: la distancia se calculaba en un sitio y se escribe el lugar de
  una sola forma. Antes el bucle vivía enterrado en la web pública y el panel no lo tenía.
- Sin hotel ubicado no se inventa distancia (el campo no existe).

## TDD rojo-verde
- `panelVeDistancias`: **RED** sin el cambio
  (`NullPointerException` al leer `metros` — el campo no existía en la respuesta admin).
  **GREEN** con él, comprobando el rango real: a menos de 300 m para el lugar de al lado.
- `LugaresTest`: `Tests run: 4, Failures: 0, Errors: 0` (los 3 de R177 siguen verdes).
- Suite backend completa: **`Tests run: 454, Failures: 0, Errors: 0, Skipped: 1` — BUILD SUCCESS**.

## Nota de entorno
Hecha en worktree limpio de `origin/develop`. El checkout compartido tiene un `V20`
sin trackear que rompe Flyway ahí (`duplicate column name: hora_entrada`); ese archivo
es de otro agente y no se tocó.
