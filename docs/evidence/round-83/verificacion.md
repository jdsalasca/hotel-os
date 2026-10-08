# Ronda 83 — La búsqueda agrupa lecturas: de 17 a 5 consultas (Etapa L)

Fecha: 2026-10-08.

## Dolor

Una petición HTTP nunca implicó una sola consulta SQL: `disponiblesConPrecio()` pedía
tipo y planes por cada habitación y las noches por cada (habitación, plan), y el
calendario repetía esa búsqueda por día. Medido con contador de sentencias en tests:
búsqueda de 4 habitaciones × 2 planes = **17 consultas**; calendario de 30 días = **510**.

## Cambio

- `disponiblesConPrecio()`: planes y tipos una vez, y el precio por (tipo, plan) una
  vez para todas sus habitaciones (el precio depende del tipo, no de la habitación).
  Mismas reglas, mismo resultado, otro orden de filas.
- `detalleOferta()`: `estaLibre()` (nuevo, una fila) en vez de la lista completa de
  libres para comprobar una habitación.
- Nuevo `pruebas/ContadorConsultas` (test-util, envuelve el DataSource) y
  `RendimientoInventarioTest` con cotas: búsqueda ≤ 8, detalle ≤ 7, calendario ≤ 200.
  Un N+1 reintroducido truena en vez de degradar en silencio.

## Verificación real

```text
Cotas vistas fallar antes: búsqueda 17, calendario 510. Después: verdes.
Suite menos EscaladoSqliteTest: 286 corridos, 0 fallos, 0 errores, 1 omitida
(preexistente). BUILD SUCCESS.
```

## Archivos

- Tocados: `InventarioService.java`, `InventarioRepository.java` (+`estaLibre`),
  `docs/plan.md`.
- Nuevos: `ContadorConsultas.java`, `RendimientoInventarioTest.java`, esta carpeta
  (sin capturas: sin cambios de UI ni de contrato).

## Riesgos y límites

- El orden de las ofertas cambió (por tipo→plan→habitación en vez de por
  habitación→plan): ningún test ni pantalla depende del orden.
- El calendario sigue haciendo una búsqueda por día (≤200/mes ahora): agrupar el mes
  entero en una lectura es el siguiente paso, con estas cotas como red.
- `V16`/OAuth del colega iban y venían en el checkout durante la ronda; su refactor
  a medias rompía la compilación de tests en un punto: no tocado, verificado aparte.
