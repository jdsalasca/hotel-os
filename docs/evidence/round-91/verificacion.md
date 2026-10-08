# Ronda 91 — Bloquear lo inexistente o sin fechas es 400, no 500 (Etapa F)

Fecha: 2026-10-08.

## Dolor

1. `POST /api/admin/bloqueos` con `roomId` inexistente: sin comprobar existencia, la FK
   cruda respondía 500 en producción (en tests ni reventaba: el esquema de pruebas no
   declara la FK, así que colaba en silencio).
2. Sin `desde`/`hasta`: `LocalDate.parse(null)` → NPE → 500. Un bloqueo global sin
   fechas no puede significar nada.

## Cambio

- `registrarBloqueo()`: habitación inexistente → `DatosInvalidosException` (400) antes
  de insertar, más red con la FK por si la carrera la cuela igual.
- `bloquear()`: cuerpo ausente o sin fechas → 400 `desde y hasta son obligatorios`.
- Tests: servicio (inexistente no escribe) + nuevo `BloqueosControllerTest` (400 en
  ambos casos).

## Verificación real

```text
Los 3 vistos fallar antes del fix (FK silenciosa en tests, NPEs en 500).
InventarioServiceTest + BloqueosControllerTest: 50/50.
Suite menos EscaladoSqliteTest: 290 corridos, 0 fallos, 0 errores, 1 omitida
(preexistente). BUILD SUCCESS.
```

## Archivos

- Tocados: `InventarioService.java`, `InventarioAdminController.java`,
  `InventarioServiceTest.java`, `docs/plan.md`.
- Nuevos: `BloqueosControllerTest.java`, esta carpeta (sin capturas: sin UI).

## Riesgos y límites

- Coordinación: 91 verificado libre antes de crear. Trabajo del colega respetado.
