# Ronda 6 — Indicadores de las tres fases

Fecha: 2026-10-06. Rama `develop`.

## La regla que gobierna el módulo

**Un indicador sin datos no vale cero.** Si falta el dato, `resultado` es `null`, el estado es
`SIN_DATOS` y hay un motivo escrito. Un cero real es un cero, con su numerador y denominador.

Esto no es cosmético: reportar 0% de ocupación cuando simplemente no hay habitaciones cargadas es
justo el tipo de número que hace creer a un hotel que va mal cuando lo que pasa es que nadie ha
cargado el inventario.

## Migración

`V3__indicadores.sql`, versionada:

- `indicator_definitions` (clave, fase 1|2|3, definición, fórmula, fuente, unidad, periodo por
  defecto, línea base, meta, responsable). **Línea base, meta y responsable nacen en NULL**: el
  hotel los fija cuando tiene datos.
- `indicator_results` (clave, periodo, resultado NULL, `datos_faltantes` con el motivo).
- `adoption_activities` (tipo, fecha, participantes, quién la confirma, `con_datos_de_origen`).

12 indicadores definidos, sin metas ni líneas base inventadas.

## Fórmulas implementadas

| Indicador | Fórmula |
|---|---|
| Ocupación | noches ocupadas / noches disponibles para venta × 100 |
| Tasa de cancelación | reservas canceladas / reservas creadas × 100 (con numerador y denominador visibles) |
| Sincronizaciones exitosas | exitosas / intentadas × 100 |
| Inventario cargado | habitaciones cargadas / **inventario esperado declarado por el hotel** × 100 |
| Reservas por canal | conteo por `reservations.origen` |
| Sobreventa | conteo de incidentes detectados por el control transaccional |
| Capacitaciones / uso / difusión | conteo de actividades que el hotel confirma |

`inventario_esperado` es una entrada del hotel, no un valor constante: sin ella, el indicador de
fase 1 no tiene denominador y sale `SIN_DATOS`.

## Adopción sin atribución inventada

Cada actividad registra si tiene **datos de origen medibles** (`con_datos_de_origen`). Si no los
tiene, la respuesta lo dice y el informe no atribuye ninguna venta a esa campaña. No se reporta
"mejora por campaña" sin origen demostrable.

## Pruebas

```
.\mvnw.cmd test
Tests run: 115, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

15 pruebas nuevas, agrupadas por fase. Entre las que importan:

- `sinInventarioCargadoLaOcupacionEsDatoFaltante`: sin habitaciones, `valor == null` y el motivo
  menciona las habitaciones. Si devolviera 0, el test caería.
- `sinReservasLaOcupacionEsCeroRealNoFaltante`: con inventario cargado y sin reservas, sí hay 0.
- `laTasaDeCancelacionMuestraNumeradorYDenominador`: 1 de 2 → 50% con ambos visibles.
- `noSeInventanLineaBaseNiMeta`: ambas en null.

## Bug encontrado por los tests

`String.format("%.2f", ...)` usa el locale del sistema y devolvía `0,00` con coma decimal. Con coma
como separador de campos, el CSV quedaba corrupto para una hoja de cálculo. Corregido con
`Locale.ROOT`.

## Verificación contra la aplicación real

Las tres migraciones aplican (`Successfully applied 3 migrations ... now at version v3`).

Con 2 habitaciones, 1 plan en COP, 2 noches tarifas y 2 reservas en noviembre de 2026:

```
GET /api/admin/indicadores?periodo=2026-11

reservas por canal: {"WEB":2}

F1 f1_canales_conectados:  SIN_DATOS <- no hay ninguna sincronización autorizada exitosa
F1 f1_inventario_cargado:  SIN_DATOS <- falta el inventario esperado: el hotel aún no declara...
F1 f1_mapeos_por_canal:    SIN_DATOS <- pendiente de datos de mapeos
F1 f1_pruebas_sync:        SIN_DATOS <- no hay sincronizaciones registradas en el periodo
F2 f2_capacitaciones:      0
F2 f2_difusion:            0
F2 f2_uso_personal:        0
F3 f3_ocupacion:           8.33 (5/60)
F3 f3_reservas_por_canal:  2
F3 f3_sobreventa:          0
F3 f3_sync_exitosas:       SIN_DATOS <- no hay sincronizaciones registradas en el periodo
F3 f3_tasa_cancelacion:    0 (0/2)
```

Exportación CSV (`GET /api/admin/indicadores.csv?periodo=2026-11`), 13 líneas:

```
clave,fase,nombre,...,resultado,numerador,denominador,estado,nota
"f1_canales_conectados",1,...,SIN_DATOS,"no hay ninguna sincronización autorizada exitosa"
"f3_ocupacion",3,...,8.33,5,60,CALCULADO,
```

## Pendiente

- Vista imprimible: el CSV y el JSON sirven hoy; el maquetado de impresión es trabajo de frontend.
- `f1_mapeos_por_canal` está definido pero sin cálculo: requiere la pantalla de mapeos por canal.