# Ronda 46 — La ocupación cuenta noches de verdad y la sobreventa deja de fingir cero

## Lo que estaba mal

Tres defectos en las consultas de indicadores, todos con dinero o decisiones detrás:

1. **Ocupación contaba estancias completas**: `SUM(hasta − desde)` por línea sumaba la estancia
   entera aunque solo rozara el periodo. Una reserva de 9 noches con 2 en noviembre aportaba 9 a
   noviembre (30 % en vez de 6.67 %).
2. **El denominador ignoraba bloqueos**: `días × habitaciones` aunque el hotel estuviera cerrado
   por mantenimiento. Un cierre total seguía mostrando el 100 % vendible.
3. **Sobreventa era un cero fijo** con nota de «conteo detectado»: fingía una medición que nadie
   hizo.

## Lo que cambia

- `nochesOcupadas` recorta cada estancia al periodo (`MIN/MAX`) en SQL.
- Nuevo `nochesBloqueadas`: intersecciones con el periodo en noches de habitación (bloqueo de
  habitación × 1, de todo el hotel × habitaciones activas); `nochesDisponibles` las resta con
  suelo en cero. Solo cuentan bloqueos de habitaciones activas. Aproximación declarada en el
  código: un bloqueo general y uno de habitación sobre la misma noche restaría dos veces; el
  hotel no cierra dos veces lo mismo y, si ocurriera, el denominador saldría conservador.
- `f3_sobreventa` es `SIN_DATOS` con motivo: sin registro de incidentes no hay cero que valga.

## Verificación

```text
.\mvnw.cmd test
Tests run: 256, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS

node tools/operacion/verificar-despliegue.mjs
despliegue: rutas de proxy, cabeceras y CSP coherentes   exit=0
```

En vivo (noviembre, 4 habitaciones, bloqueo 10–13 de la 101): denominador 117 = 120 − 3;
sobreventa SIN_DATOS con su motivo. Pantalla de Indicadores revisada en escritorio y 390 px:
«Incidentes de sobreventa: Sin datos» con el motivo a la vista.

## Nota de git

Sin push por regla vigente. Solo archivos de esta ronda en el commit.