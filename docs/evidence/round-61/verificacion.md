# Ronda 61 — Tarifas atómicas: lo rechazado no toca nada (Etapa C, núcleo)

## Los defectos

`POST /api/admin/tarifas` aplicaba cada campo por separado y sin validar antes: precio válido +
mínimo inválido dejaba el precio cambiado con error; precio negativo respondía éxito sin hacer
nada; editar el importe reabría la noche (`fijarPrecio` forzaba `cerrado=0`); mínimo mayor que
máximo colaba; y una restricción sobre una noche inexistente era éxito silencioso.

## Lo que cambia

Nueva operación de dominio `TarifaService.fijarNoche()`: valida toda la entrada (fecha, plan y
tipo existentes, precio ≥ 0, mínimos/máximos ≥ 1 y coherentes, noche inexistente exige precio),
aplica en una transacción y devuelve lo guardado. Semántica explícita: lo omitido se conserva
(incluido el cierre) y el endpoint mantiene su contrato. Se eliminó un helper muerto (`plan()`).

Queda fuera a propósito: la edición masiva con preview (la pantalla sigue mandando noche por
noche, ahora atómicas una por una) y la edición por rangos/días de semana.

## Verificación

```text
.\mvnw.cmd test
Tests run: 299, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS

docker compose build api  (web sin cambios)
node tools/operacion/verificar-despliegue.mjs
despliegue: rutas de proxy, cabeceras y CSP coherentes   exit=0
```

Matriz viva contra el servidor: base 201, precio+mínimo inválido 400, negativo 400, editar
precio en noche cerrada 201 y sigue `cerrado:true` con el importe nuevo. Sin cambios de
interfaz en esta ronda.