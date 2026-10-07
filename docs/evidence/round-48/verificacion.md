# Ronda 48 — Las creadas se cuentan cuando se crean, no cuando llegan

## Lo que estaba mal

«Reservas creadas», «tasa de cancelación» y «reservas por canal» filtraban por fecha de
**llegada** aunque sus definiciones dicen «creadas/registradas en el periodo». Una reserva creada
en octubre para diciembre contaba en diciembre —un periodo donde nadie la creó— y una cancelada
se perdía del numerador si su estancia caía fuera. Dos cohortes mezcladas sin decirlo.

## Lo que cambia

Las tres consultas filtran por `creado_en` (solo la fecha): la cohorte es la de creación y la
cancelación cuenta las de esa cohorte que hoy están canceladas. Nada más cambia; las definiciones
semilla ya hablaban de creación, así que el código se alinea con lo documentado.

## Verificación

```text
.\mvnw.cmd test
Tests run: 259, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS

node tools/operacion/verificar-despliegue.mjs
despliegue: rutas de proxy, cabeceras y CSP coherentes   exit=0
```

En vivo: reserva creada hoy (7 oct) para el 20 de noviembre cuenta en el denominador de octubre
(13 creadas) y no en el de noviembre (SIN_DATOS). Ronda sin cambios de interfaz.

## Nota de git

Sin push por regla vigente. Solo archivos de esta ronda en el commit (hay otro agente activo en
el árbol).