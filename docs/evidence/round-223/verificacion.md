# Ronda 223 — Restauración segura

Fecha: 2026-10-11. La reproducción y las verificaciones se hicieron únicamente con SQLite sintética dentro de contenedores efímeros; no se montaron `/data`, respaldos reales ni volúmenes de producción.

## Hallazgo reproducido antes del cambio

Base de partida: `develop` en `1b31d926087c2ae5d67f3fadf58147bb961a03c6`.

La regresión creó una base SQLite sin esquema. `PRAGMA integrity_check` devolvió `ok`, pero no existían `flyway_schema_history` ni `reservations`. El restaurador previo terminó con código 0, reemplazó el destino sintético y mostró:

```text
restauracion completada
  integridad: ok
  tablas:     0
  reservas:   ?
```

La prueba falló por ese comportamiento antes de modificar `restaurar.sh`.

## Cambio verificado

- Se exige integridad, historial Flyway y las tablas `users`, `reservations`, `rooms`, `room_types` y `rates`.
- Se prepara una copia candidata junto al destino y se valida antes de guardar o tocar el destino.
- El estado previo se conserva con `.backup`; se limpian WAL/SHM antiguos y se instala la candidata con `mv` en el mismo sistema de archivos.
- Se vuelve a validar la base instalada. Los fallos de consulta o verificación terminan con error, no imprimen éxito y activan recuperación desde la copia previa cuando existe.
- El contenedor normaliza finales CRLF de los scripts operativos para que funcionen si Git para Windows entrega esos finales.

## Verificación local

Construcción de la imagen:

```text
docker build -t hotel-ops-r223 tools/operacion
exit code: 0
```

Prueba aislada y ciclo existente, con bases de demostración temporales. La regresión incluye una
falla de consulta posterior inyectada una sola vez para comprobar que no se informa éxito y que se
recupera el destino anterior:

```text
docker run --rm hotel-ops-r223 /usr/local/bin/probar-restaurar-segura.sh
exit code: 0
```

La salida confirmó restauración válida (`esquema v1`, `6 tablas`, `1 reservas`), rechazo de la base vacía con destino/WAL/SHM sin cambios, recuperación de la reserva previa ante el fallo inyectado y ciclo completo restaurado con `8 tablas` y `1` reserva.

Comprobaciones adicionales: `sh -n /usr/local/bin/restaurar.sh` en el contenedor, código 0; `git diff --check`, sin errores. ShellCheck no está disponible globalmente y no se instaló.

## Alcance de la evidencia

Esto verifica el comportamiento del script contra datos sintéticos y el contenedor de operaciones. La instalación real sigue requiriendo detener la aplicación antes de restaurar y comprobar `/api/health` después de arrancarla.
