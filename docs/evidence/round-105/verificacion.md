# Ronda 105 — Tope de 1 MB a cuerpos JSON con 413 (Etapa F)

Fecha: 2026-10-08.

## Dolor

`server.tomcat.max-http-form-post-size` solo limita formularios: un JSON gigante se
leía entero en memoria. Probado: un tipo con código de 2 MB se creó con 201.

## Diseño (bounded)

Filtro `LimiteCuerpoJson` (nuevo archivo, cero solape): `Content-Length` declarado
corta en 413 sin leer; sin longitud, el flujo se cuenta y corta al pasar el tope
(también cubre troceado). 1 MB sobra (el lote de 366 noches no llega a 100 KB).
Va tras la auditoría (que registra el 413) y antes que seguridad (frenar no exige
sesión). Respuesta 413 con `{"error"}` en español, que el cliente ya muestra.

## Cambio

- `LimiteCuerpoJson.java` + `LimiteCuerpoJsonTest.java` (413 en panel y pública,
  cuerpo normal intacto).

## Verificación real

```text
2 fallos vistos antes (201 con megabytes guardados, 409 procesado).
LimiteCuerpoJsonTest: 3/3.
Suite menos EscaladoSqliteTest: 303 corridos, 0 fallos, 0 errores, 1 omitida.
BUILD SUCCESS.
En el camino, `isFinished()` no declara IOException (compilación).
```

## Archivos

- Tocados: `docs/plan.md`.
- Nuevos: `LimiteCuerpoJson.java`, `LimiteCuerpoJsonTest.java`, esta carpeta
  (sin capturas: sin UI; el 413 viaja por los mensajes existentes).

## Riesgos y límites

- Coordinación: 105 verificado libre antes de crear. Trabajo del colega respetado.
