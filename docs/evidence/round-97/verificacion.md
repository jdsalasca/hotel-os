# Ronda 97 - El verificador también cuida respaldos

Fecha: 2026-10-08.

## Dolor
El modo túnel perdió los respaldos en silencio (R78 lo encontró tarde) porque nada los
exigía: el checker miraba proxy y CSP, pero no quién respalda.

## Cambio
- Sección 4 en `verificar-despliegue.mjs`: `compose.yaml`, `compose.production.yaml` y
  `compose.tunnel.yaml` definen el servicio `respaldo` con ambos volúmenes. El drill y el
  de capturas no sirven tráfico y quedan fuera a propósito.
- En repo: **24/24 verde**. En dir vacío: las 6 nuevas fallan + exit 1 (la regla muerde).

## Verificación real
- `node tools/operacion/verificar-despliegue.mjs` → 24 ok, exit 0.
- Negativo en dir temporal con composes rotos → 6 FALLA + exit 1 (dir limpiado después).
- Archivos del otro agente intactos.

## Archivos
- Tocados: `tools/operacion/verificar-despliegue.mjs`.
- Nuevos: este archivo.
