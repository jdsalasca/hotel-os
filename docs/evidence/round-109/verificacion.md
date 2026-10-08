# Ronda 109 — f3_ocupación se nombra por lo que mide + infra axe lista (Etapa D/L)

Fecha: 2026-10-08.

## Dolor

`f3_ocupacion` ("Ocupación") contaba reservas vigentes (pendientes y confirmadas):
sin recepción no hay estancia efectiva que medir y el nombre lo prometía igual.

## Diseño (bounded)

Renombrar por lo que mide (la Etapa D lo pide explícito), sin tocar la clave ni el
cálculo. La infra axe va en directorio y compose propios para no interferir con
capturas ni despliegue.

## Cambio

- Migración `V17__ocupacion_nombre_honesto.sql`: nombre, definición y fórmula
  ("Noches comprometidas"); la clave `f3_ocupacion` no cambia.
- Tests del nuevo nombre + actualización del que fijaba el viejo.
- Infra `tools/accesibilidad/` (axe en imagen Playwright) + `compose.accesibilidad.yaml`
  (volumen por env `EVIDENCIA`) + ignore de su salida local. Pendiente de correr:
  el daemon Docker está caído y no lo toco (posible experimento ajeno).

## Verificación real

```text
2 fallos vistos antes de la migración (nombre viejo).
IndicadoresServiceTest + resto de indicadores verdes.
Suite menos EscaladoSqliteTest: 304 corridos, 0 fallos, 0 errores, 1 omitida.
BUILD SUCCESS.
```

## Archivos

- Tocados: `V17` (nueva), `IndicadoresServiceTest.java`, `.gitignore`, `docs/plan.md`.
- Nuevos: `tools/accesibilidad/`, `compose.accesibilidad.yaml`, esta carpeta
  (sin capturas: sin UI; el informe muestra los textos nuevos solo).

## Riesgos y límites

- `PreparacionTest` del colega falló UNA vez en suite (pasa solo y pasa ahora):
  flake no reproducido en ruta ajena a este cambio; queda anotado, no investigado
  a fondo.
- Coordinación: 109 verificado libre antes de crear. Trabajo del colega respetado.
