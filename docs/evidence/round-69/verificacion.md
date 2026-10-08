# Ronda 69 — Liveness y readiness con códigos correctos (Etapa M)

Fecha: 2026-10-07.

## Dolor

`GET /api/health` respondía HTTP 200 incluso con `estado: degradado`: el orquestador
seguía mandando tráfico a una base incompleta porque nadie mira el cuerpo. Y al revés:
el endpoint tocaba SQLite, así que una base colgada convertía la señal de "proceso vivo"
en un fallo que invita a reiniciar lo que no se arregla reiniciando.

## Cambio

- `GET /api/health/vivo` (nuevo, público): el proceso responde. No toca la base.
  Siempre 200 con `{"estado":"ok"}`.
- `GET /api/health` (readiness, cuerpo idéntico): 200 con `ok`/`accesible` cuando la
  base abre y el esquema está completo; 503 `degradado`/`esquema incompleto` si faltan
  tablas; 503 `no-lista`/`inaccesible` si SQLite ni abre. Una línea en `SecurityConfig`
  lo hace público.
- Runbook (`despliegue-respaldos.md`): el 503 apaga el tráfico y `/vivo` distingue
  proceso de base.

## Verificación real

```text
HealthControllerTest: Tests run: 4, Failures: 0, Errors: 0 (1 preexistente + 3 nuevos:
vivo público sin base, esquema incompleto → 503 degradado, base inaccesible → 503).
Suite menos EscaladoSqliteTest: 266 corridos, 0 fallos, 0 errores, 1 omitida
(preexistente). BUILD SUCCESS.

Prueba viva (imagen api reconstruida con este cambio):
- GET :8080/api/health/vivo → 200 (directo)
- GET :8080/api/health → 200 {"estado":"ok","tablas":"4","base":"accesible"}
- GET :5174/api/health/vivo → 200 (a través del proxy nginx)
- node tools/operacion/verificar-despliegue.mjs → exit 0
  "despliegue: rutas de proxy, cabeceras y CSP coherentes entre nginx y Vercel"
- Los healthchecks de compose.yaml y compose.production.yaml siguen válidos:
  `curl -fsS …/api/health | grep -q '"estado":"ok"'` (el 503 falla en -f y el
  degradado ya no trae ok).
```

## Archivos

- Tocados: `HealthController.java`, `SecurityConfig.java` (una línea),
  `HealthControllerTest.java`, `despliegue-respaldos.md`, `docs/plan.md`.
- Nuevos: esta carpeta.

## Riesgos y límites

- Sin cambios de UI: sin capturas en esta ronda.
- Trabajo del colega respetado: cerró Ronda 65 en paralelo sin solapes
  (`HuespedController`, `ReservaServiceHuesped`, `ReservaController`,
  `AdminReservaPropiaTest`, `round-65/`); no se tocó nada suyo.
