# Ronda 43 — El simulacro de respaldo funciona de verdad, en aislado

## Lo que se probó

Los scripts `respaldar.sh`, `restaurar.sh` y `probar-respaldo.sh` existían pero nadie los había
ejecutado nunca: un procedimiento de recuperación sin probar es una promesa. Se ejecutó el ciclo
completo contra un proyecto compose separado (`compose.drill.yaml`, red y volúmenes propios),
sin tocar la base de desarrollo:

1. API nueva con migraciones limpias + siembra por API (`sembrar-simulacro.mjs`): reserva
   `H-D413DE04`.
2. `probar-respaldo.sh`: respaldo consistente (20 tablas, integridad ok) → destrucción a
   propósito (`reservations` eliminada) → restauración → verificación (1 reserva, la misma).
3. La app rearrancada sobre lo restaurado: `/api/health` ok y la reserva legible por
   código + correo.
4. Limpieza: `down -v` solo del proyecto del simulacro; los volúmenes de desarrollo intactos
   y la API de desarrollo sana.

```text
respaldo creado: /backups/hotel-20261007T172159Z.sqlite3 (212.0K, 20 tablas, integridad ok)
restauracion completada — integridad: ok, tablas: 20, reservas: 1
CICLO DE RESPALDO Y RESTAURACION VERIFICADO
{"estado":"ok","base":"accesible","tablas":"4"}
{"codigo":"H-D413DE04",...,"estado":"PENDIENTE",...}
```

## Hallazgos

- **El ciclo funciona.** Nada que corregir en los scripts.
- **No hay respaldos programados**: el servicio no existe en ningún compose; las copias solo
  ocurren si alguien las ejecuta. Es el siguiente ítem operativo (decidir calendario y destino
  externo con el hotel, no código arbitrario).
- De paso: el login rota sesión y token CSRF (fijación de sesión bien cerrada); el primer
  intento de siembra falló con 403 por reutilizar el token viejo. Comportamiento correcto,
  documentado en `sembrar-simulacro.mjs` por si muerde a otro script.

## Verificación

Suite backend sin cambios de código en esta ronda (solo `compose.drill.yaml`,
`sembrar-simulacro.mjs` y esta evidencia): la última suite completa sigue en
`Tests run: 247, Failures: 0, Errors: 0, Skipped: 1`.

## Nota de git

Sin push por regla vigente: `develop` va por delante de `origin/develop` (R34–R43).