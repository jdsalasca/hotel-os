# Ronda 12 — Fechas inválidas en cuerpos POST

Fecha: 2026-10-06. Rama `develop`.

## Entregable

Una fecha mal escrita en un cuerpo POST responde 400 con mensaje, no 500:

```text
reserva malformada sin roomId: {"error":"las fechas deben tener formato YYYY-MM-DD"} [400]
bloqueo malformado: {"error":"las fechas deben tener formato YYYY-MM-DD"} [400]
tarifa malformada: {"error":"las fechas deben tener formato YYYY-MM-DD"} [400]
```

## Separación de capas

Los controladores siguen sin contener reglas de negocio. Solo convierten transporte a dominio y
traducen excepciones a HTTP:

- `InventarioAdminController.responder()` centraliza las lecturas y escrituras de su módulo.
- `ReservaController` mueve el parseo dentro del bloque que ya traducía errores de dominio.
- El servicio sigue validando periodos, disponibilidad, precios y bloqueos.

## Defectos corregidos

1. `POST /api/admin/bloqueos` hacía `LocalDate.parse` antes de entrar al traductor de errores.
2. `POST /api/admin/tarifas` hacía `LocalDate.parse` dentro del lambda, pero sin captura de
   `DateTimeParseException`.
3. `POST /api/reservas` hacía dos parseos antes del `try` cuando no se indicaba habitación.

Las tres pruebas fallaban primero con `ServletException` causada por `DateTimeParseException`.
Después del cambio, las tres responden 400 con el mismo mensaje.

## Verificación real

```text
.\mvnw.cmd test
Tests run: 153, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

```text
docker compose -f compose.yaml -f compose.capturas.yaml up --build --force-recreate \
  --abort-on-container-exit capturas
capturas completas sin errores de consola ni de API
```

La suite de capturas reconstruyó backend y frontend y volvió a pasar completa después del cambio.