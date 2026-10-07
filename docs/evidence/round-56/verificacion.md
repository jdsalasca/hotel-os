# Ronda 56 — Libro de abonos y saldo (parte 1: API)

## Lo que faltaba

Cobrar en recepción era un papelito: ningún registro de abonos, ningún saldo pendiente, ninguna
trazabilidad. Sin pasarela (el hotel no ha elegido proveedor), el registro manual es lo que
permite cobrar sin perder la cuenta.

## Lo que se entrega (API; la pantalla viene en la R57)

- V12 `pagos`: reserva, monto positivo, moneda, concepto, actor, creado; anular marca
  (quién/cuándo) sin borrar.
- `POST /api/admin/reservas/{codigo}/abonos` (201 + id), `POST /api/admin/abonos/{id}/anular`
  (200; doble, 400), `GET .../saldo` (total, moneda, abonado, pendiente, movimientos).
- Reglas: moneda igual a la de la reserva, importe > 0, 404 si no existe. El saldo se calcula al
  leer, nunca se guarda. De paso se cazó un bug real: `last_insert_rowid()` corría en otra
  conexión del pool y devolvía ids ajenos; ahora `GeneratedKeyHolder`.

## Verificación

```text
.\mvnw.cmd test
Tests run: 285, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS

Flyway V12 aplicada en vivo sobre la base con datos (v11 → v12)
node tools/operacion/verificar-despliegue.mjs
despliegue: rutas de proxy, cabeceras y CSP coherentes   exit=0
```

En vivo: reserva de 16800 → abono 50000 → pendiente −33200 (de más, permitido y visible) →
anulación → pendiente 16800. Sin cambios de interfaz en esta ronda.

## Nota de git

Sin push por regla vigente. Solo archivos de esta ronda en el commit.