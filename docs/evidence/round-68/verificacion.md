# Ronda 68 — Indicadores: lo rechazado es 400 y no deja datos (saldos Etapa D)

Fecha: 2026-10-07.

## Dolor

1. `POST /api/admin/indicadores/actividades` con fecha inválida insertaba la fila y
   después respondía 400: la fila con fecha basura quedaba y rompía el informe al leerla
   (`LocalDate.parse` en `actividades()`).
2. `POST /api/admin/indicadores/inventario-esperado` inválido respondía 200 con `error`.
3. `GET /api/admin/indicadores?periodo=2026-99` (y su CSV) reventaba en 500: el mes 99
   pasaba la forma `YYYY-MM` y `LocalDate.parse("2026-99-01")` explotaba sin handler.
4. `Csv.celda` solo miraba el primer carácter: `" =1+1"`, `"\t=1+1"` y `"\n=1+1"`
   salían sin la comilla protectora.

## Cambio

- `IndicadoresService.registrarActividad()` valida todo antes de insertar: tipo y
  descripción no vacíos (topes 40/1000), fecha real (`LocalDate.parse` primero),
  participantes nulos o ≥ 0, quien confirma ≤ 200. Todo `IllegalArgumentException` en
  español; nada se escribe si algo falla.
- `IndicadoresController`: `registrarActividad` captura `IAE` → 400 (normaliza
  espacios de fecha/tipo/descripción para la respuesta); `fijarInventarioEsperado`
  es `ResponseEntity` (400 con cuerpo ausente o negativo, 200 con lo guardado);
  `informe` y `csv` son `ResponseEntity` con 400 ante periodo inválido (el cuerpo de
  éxito no cambia, el panel no se toca).
- `normalizarPeriodo` exige mes 01–12: `2026-99` es 400, no 500.
- `Csv.celda` mira el primer carácter no-blanco (espacio, tab, salto y demás
  `Character.isWhitespace`); los números y el texto legítimo salen igual.

## Verificación real

```text
CsvTest (nuevo, 4) + IndicadoresEscriturasTest (nuevo, 5) + IndicadoresServiceTest
(+4) + IndicadoresSoloLecturaTest (3): todo verde en la corrida.
Suite menos EscaladoSqliteTest: 260 corridos, 0 fallos, 0 errores, 1 omitida
(preexistente). BUILD SUCCESS.

Prueba viva contra api:8080 + web:5174 reconstruidos (admin demo):
- GET /api/admin/indicadores?periodo=2026-99 → 400
  "el periodo debe tener formato YYYY-MM con mes de 01 a 12" (antes 500)
- POST actividades {fecha:"ayer"} → 400 "la fecha debe tener formato YYYY-MM-DD"
  y actividades 0 → 0 (nada escrito)
- POST inventario-esperado {habitaciones:-1} → 400 (antes 200)
```

## Archivos

- Tocados: `IndicadoresService.java`, `IndicadoresController.java`, `Csv.java`,
  `IndicadoresServiceTest.java`, `docs/plan.md`.
- Nuevos: `CsvTest.java`, `IndicadoresEscriturasTest.java`, esta carpeta.

## Riesgos y límites

- `confirmadaPor` se guarda recortado tal cual lo manda el panel: no es identidad
  autoritativa (sin actor de sesión). Pendiente cuando haya gestión de personal.
- Sin cambios de UI: el `<input type="month">` no produce `2026-99`; el 400 es red de
  seguridad para llamadas directas. Sin capturas en esta ronda.
- Trabajo del colega respetado: no se tocaron sus archivos (ver `git status` limpio
  tras el commit).
