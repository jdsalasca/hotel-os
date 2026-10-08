# Ronda 90 — El calendario filtra libres en memoria: de 34 a 7 consultas (Etapa L)

Fecha: 2026-10-08.

## Dolor

Tras la Ronda 86 el mes seguía pidiendo `disponibles()` por día (30 de las 34
consultas): la misma pregunta con distinto día, pudiendo filtrar en memoria.

## Cambio

- `calendarioMensual()` lee una vez habitaciones, reservas y bloqueos del mes y por
  día filtra en memoria con los mismos `cubreReserva`/`cubreBloqueo` (incluido el
  bloqueo global y la habitación retirada).
- Núcleo `ofertasDe()` compartido con la búsqueda (caché mensual de tarifas).
- Cota del calendario: ≤ 50 → ≤ 12 (medido 34 antes, 7 después: 1 habitaciones +
  1 reservas + 1 bloqueos + 1 tipos + 1 planes + 2 meses-tarifa).

## Verificación real

```text
Cota ≤ 12 vista fallar (34) + test de paridad día a día contra la consulta SQL
(ocupada, bloqueada, retirada y global): verdes tras el cambio.
En el camino: código de tipo duplicado (la validación de la Ronda 66 muerde en
tests) y un conteo propio que olvidaba las habitaciones del setup.
Suite menos EscaladoSqliteTest: 287 corridos, 0 fallos, 0 errores, 1 omitida
(preexistente). BUILD SUCCESS.
```

## Archivos

- Tocados: `InventarioService.java`, `RendimientoInventarioTest.java`, `docs/plan.md`.
- Nuevos: esta carpeta (sin capturas: sin cambios de UI ni de contrato).

## Riesgos y límites

- Coordinación: 87–89 los publicó el colega (verificado antes de crear); 90 libre.
  Trabajo del colega respetado en todo.
