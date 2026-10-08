# Ronda 99 — El lote lee el rango una vez y no resuelve dos veces (Etapa L)

Fecha: 2026-10-08.

## Dolor

Previsualizar 10 noches hacía 12 lecturas (una por noche) y aplicar 44 sentencias:
resolvía cada fila dos veces (previa + aplicar) y releía lo que acababa de guardar.

## Cambio

- `resolverNoche()` partida en validación + `resolverConPrevia()` pura; el lote trae
  `previasPorFecha()` (un `nochesDelPeriodo` del rango) y resuelve una vez.
- `aplicarLote()` escribe lo resuelto y devuelve lo escrito sin releer.
- Cotas nuevas en `LoteRendimientoTest`: previa de 10 ≤ 4, aplicar ≤ 16.

## Verificación real

```text
Bounds vistos fallar (previa 12, aplicar 44) antes del cambio.
Suite menos EscaladoSqliteTest: 292 corridos, 0 fallos, 0 errores, 1 omitida
(preexistente). BUILD SUCCESS.
```

## Archivos

- Tocados: `TarifaService.java`, `RendimientoInventarioTest.java` (cota calendario
  ≤10, ya cumplida), `docs/plan.md`; restaurado `round-94b/` (mi Ronda 94, ocupada
  por la suya; la suya intacta).
- Nuevos: `LoteRendimientoTest.java`, esta carpeta (sin capturas: sin UI ni contrato).

## Riesgos y límites

- Coordinación: 95 y 97 los publicó el colega (verificado; su convención: lo suyo se
  queda, lo mío se mueve — igual que él hizo en 38de54f). Falsa alarma de "commits
  perdidos" (el `log -4` no llegaba a los míos; el reflog los tiene todos).
- Trabajo del colega respetado en todo.
