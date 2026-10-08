# Ronda 86 — El calendario trae las tarifas del mes una vez: 150 a 34 consultas (Etapa L)

Fecha: 2026-10-08.

## Dolor

Tras la Ronda 83 el calendario seguía pidiendo tipos, planes y noches por cada día:
30 días × 5 = 150 consultas por abrir el mes.

## Cambio

- `detalleParaPlan()` con sobrecarga que recibe las noches ya leídas; la versión de
  4 argumentos pide el rango y delega (misma regla, un solo sitio).
- Núcleo `ofertasDe()` compartido: la búsqueda lo usa sin caché y el calendario con
  caché mensual por (tipo, plan) recortada por día.
- `calendarioMensual()` lee tipos y planes una vez y las tarifas del mes una vez por
  (tipo, plan); por día solo queda `disponibles()`.
- Cota del calendario: ≤ 200 → ≤ 50 (medido 34: 30 libres + 1 tipos + 1 planes + 2
  meses-tarifa).

## Verificación real

```text
Cota ≤ 50 vista fallar (medido 150) antes del cambio.
Suite menos EscaladoSqliteTest: 286 corridos, 0 fallos, 0 errores, 1 omitida
(preexistente). BUILD SUCCESS.
```

## Archivos

- Tocados: `InventarioService.java`, `docs/plan.md`.
- Nuevos: esta carpeta (sin capturas: sin cambios de UI ni de contrato).

## Riesgos y límites

- Por día sigue viajando `disponibles()`: agruparlo en memoria es el siguiente paso.
- Coordinación: 84 y 85 los publicó el colega (verificado antes de crear); 86 libre.
  Trabajo del colega respetado en todo.
