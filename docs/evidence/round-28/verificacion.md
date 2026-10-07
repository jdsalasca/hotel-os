# Ronda 28 — Mirar el informe escribía en la base

## Lo encontrado

`GET /api/admin/indicadores` y su CSV **escribían en SQLite en cada lectura**. No es una caché:
`indicator_results` no la consulta nadie en todo el código, así que la tabla solo crecía.

Medido en la base de desarrollo, que ya había recibido visitas desde el guion de capturas:

```text
definitions: 12
results:     24      (12 indicadores × 2 periodos)
generado_en: 2026-10-07 05:57:52   <- de visitas al panel
```

Tres cosas malas en una:

1. **Un GET tiene que ser seguro.** Este no lo era: leer escribía.
2. **El navegador prefetchea los enlaces.** El CSV es un `<a href>` en la pantalla de indicadores,
   así que la escritura ocurría sola, sin que nadie pulsara "Descargar CSV".
3. **El trabajo se perdía.** Doce filas por visita, para una tabla que nadie lee.

## El arreglo

`calcular` deja de persistir, y con él se borra `IndicadoresRepository.guardarResultado`, que queda
sin ninguna llamada. La tabla `indicator_results` se queda en el esquema, ya aplicada en
producción: no se toca una migración que ya corrió.

Si algún día hace falta un histórico de indicadores, que lo escriba una tarea programada a
propósito, no de paso por una pantalla.

## Verificación, y un test que mentía

Este es lo importante de la ronda: **mi primer test pasaba con el bug presente**.

La primera versión comparaba el número de filas antes y después. No lo detectaba porque el guardado
es un UPSERT: la primera lectura inserta y las siguientes actualizan, así que el total no cambia.
La segunda versión miraba cuántos resultados eran recientes, y tampoco: las filas viejas siguen
dentro de la ventana, así que el número tampoco se movía.

La que sí funciona es **vaciar la tabla antes de cada prueba**. Con la tabla vacía, cualquier
escritura se ve:

```text
antes del arreglo:  expected: <0> but was: <12>
después:            Tests run: 3, Failures: 0
```

Un test que pasa con el bug puesto no prueba nada; a veces cuesta dos intentos verlo.

## Contra la base real

```text
resultados antes de mirar:                 0
GET /api/admin/indicadores   -> 200
GET /api/admin/indicadores.csv -> 200
resultados despues de mirar:              0

Y tras las 34 capturas del guion, que entra a la pantalla de indicadores:
indicator_results:                        0
```

Las 34 capturas son la prueba más fuerte: recorren la pantalla y la tabla sigue vacía.

```text
.\mvnw.cmd test
Tests run: 205, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS
```

## Lo que no cambia

- El informe devuelve exactamente lo mismo: periodo, indicadores, reservas por canal y
  actividades. El test `elInformeSigueFuncionando` lo fija.
- El CSV sigue exportando las mismas filas, con los indicadores sin datos marcados como `SIN_DATOS`
  y nunca como 0.
- `indicator_results` sigue existiendo en el esquema, con sus datos de la ronda anterior. Borrarla
  sería una migración sobre datos ya escritos sin necesidad.