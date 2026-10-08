# Ronda 104 — Tope técnico de precio para que las sumas no desborden (Etapa I)

Fecha: 2026-10-08.

## Dolor

Sin tope, una tarifa absurda (`Long.MAX_VALUE` céntimos) entraba y las sumas en `long`
del desglose y los descuentos desbordaban en silencio, con totales negativos. La Etapa I
pide acotar precios explícitamente.

## Diseño (bounded)

Un techo por noche en el punto por donde pasan todas las escrituras
(`resolverConPrevia` + `fijarPrecio`): `10_000_000_000` céntimos. No es regla
comercial (ninguna tarifa real se acerca); con él, ni 366 noches al tope desbordan.
Alternativa descartada: aritmética con `addExact` por todas partes (ruido sin valor
con el techo puesto).

## Cambio

- `TarifaService.PRECIO_MAXIMO_CENTS` + rechazo en noche individual, lote, rango y
  vía directa, sin escribir nada.
- Tests: tope rechazado en las tres vías, lote mixto sin escritura parcial y suma
  exacta de un mes al tope.

## Verificación real

```text
3 tests vistos fallar (símbolo inexistente).
InventarioServiceTest: 51/51.
Suite menos EscaladoSqliteTest: 300 corridos, 0 fallos, 0 errores, 1 omitida.
BUILD SUCCESS.
```

## Archivos

- Tocados: `TarifaService.java`, `InventarioServiceTest.java`, `docs/plan.md`.
- Nuevos: esta carpeta (sin capturas: sin UI; el 400 viaja por los mensajes existentes).

## Riesgos y límites

- Coordinación: 104 verificado libre antes de crear. Trabajo del colega respetado.
