# Ronda 165 - Rotación de logs en prod

## Cambio
- Los 5 servicios de `compose.tunnel.yaml` llevan `logging: json-file` con
  `max-size 10m + max-file 3` (30 MB tope por contenedor): el driver crece sin
  fin por defecto y el disco del mini PC no. `docker logs` sigue igual.
- `verificar-despliegue.mjs` exige rotación por servicio (para que no se pierda).
- Limpieza puntual: `docker image prune -af` en TopNUC (solo lo sin usar).

## TDD rojo-verde
- Verificador: 5 FALLA sin rotación; con ella, exit 0.

## Verificación (salida real, 2026-10-09)
- Disco: 114G con 22% usado (85G libres); logs de api/web ~1 MB (sin incendio,
  prevención); imágenes huerfanas liberadas con el prune.
- En prod tras el deploy: contenedores recreados sanos + health OK.

## Despliegue
- Merge a `develop`, push y `compose up -d --build` en TopNUC.
