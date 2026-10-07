# Ronda 36 — Un cuerpo sin fechas ya es un 400, no un 500

## Lo que hacía

`POST /api/reservas` con las fechas ausentes (`{}` o sin `llegada`) reventaba con
`NullPointerException` dentro de `LocalDate.parse(null)`: un 500 ante una petición mal formada.
El servicio ya decía «fechas requeridas», pero el controlador no llegaba hasta él.

## La corrección

El controlador comprueba las fechas antes de parsear y responde `400 fechas requeridas`. Tres
líneas, en el borde donde nace el nulo y no donde explota.

## Verificación

```text
.\mvnw.cmd test
Tests run: 236, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS

node tools/operacion/verificar-despliegue.mjs
despliegue: rutas de proxy, cabeceras y CSP coherentes   exit=0
```

Contra el servidor en marcha:

| Cuerpo | Antes | Ahora |
|---|---|---|
| `{}` | 500 (NPE) | 400 «fechas requeridas» |
| Sin `llegada` | 500 (NPE) | 400 «fechas requeridas» |
| Reserva normal | 201 | 201 `H-5FE98644` |

Los dos errores de consola son esos dos 400 de la propia sonda, esperados.

## Nota de git

Sin push por regla vigente: `develop` va 3 por delante de `origin/develop` (R34–R36).