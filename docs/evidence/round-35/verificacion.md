# Ronda 35 — Reutilizar una clave con otro contenido ya es un conflicto definido

## Lo que hacía

La idempotencia solo miraba `(clave, correo)`: si el mismo huésped repetía la clave con otras
fechas u otra habitación, el servicio devolvía la reserva vieja en silencio. El huésped creía
haber reservado lo nuevo y el hotel no se enteraba. Ni 400, ni 409, ni aviso: un 201 con los datos
de la otra petición.

## La regla nueva

Al encontrar la reserva que la clave ya creó, el servicio compara habitación, fechas y huéspedes
dentro de la misma transacción:

- **Mismo contenido** → devuelve el código sin duplicar (el reintento legítimo sigue intacto).
- **Contenido distinto** → `409` con motivo y sin escribir nada:

```text
esa clave de idempotencia ya creó otra reserva:
repite la petición original o usa una clave nueva
```

Es 409 y no 400: la petición está bien formada, pero choca con lo que esa clave ya hizo. El
comparador vive en el servicio, así que protege a cualquier llamador futuro, no solo al
controlador público. El contenido se lee con una consulta nueva (`contenidoPorCodigo`) en vez de
reutilizar el mapeo completo de la reserva, que trae campos que aquí no pintan.

## Verificación

```text
.\mvnw.cmd test
Tests run: 235, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS

node tools/operacion/verificar-despliegue.mjs
despliegue: rutas de proxy, cabeceras y CSP coherentes   exit=0
```

Contra el servidor en marcha, con la misma `Idempotency-Key`:

| Intento | Resultado |
|---|---|
| Primero | 201 `H-256F345E` |
| Reintento idéntico | 201, mismo código, sin duplicar |
| Otras fechas, misma clave | 409 con el motivo de arriba |

Los errores de consola del navegador son esos 409 de la propia sonda, esperados.

## Nota de git

Sin push por regla vigente: esta ronda queda commiteada en `develop`, que va por delante de
`origin/develop` (R34 + R35).