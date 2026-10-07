# Ronda 38 — Si la tarifa se movió, el huésped lo sabe antes de pagar

## Lo que hacía

La confirmación mostraba el total de la búsqueda, pero el POST solo mandaba la habitación: si el
hotel cambiaba la tarifa entre medias, la reserva se guardaba con el precio nuevo sin decir nada.
El huésped aceptaba un importe y pagaba —figuradamente— otro.

## Lo que hace ahora

La petición lleva el importe visto (`totalEsperadoCents` + `monedaEsperada`) y el servicio lo
compara con el vigente **en la misma transacción del alta**: entre comprobar y escribir la tarifa
podría volver a moverse, así que la comparación no puede ir fuera.

- **Coincide** → 201 como siempre. Sin expectativa (clientes viejos), también 201.
- **Difiere** → `409` con el motivo y el importe vigente (`nuevoTotalCents`, `nuevaMoneda`), sin
  escribir nada.

El navegador, ante ese 409, muestra «Viste X y ahora el total es Y. Si estás de acuerdo, pulsa
Confirmar de nuevo»: la segunda pulsación reintenta con el importe ya visto. Nada se reserva sin
consentimiento explícito del importe final. Para distinguirlo, `ErrorApi` ahora lleva el cuerpo
del error (`datos`), que hasta hoy se tiraba.

Queda fuera a propósito: el plan tarifario se sigue eligiendo como el primero activo con tarifa
completa, igual que la búsqueda. Que el huésped vea y elija el plan explícito es la siguiente
ronda de este flujo.

## Verificación

```text
.\mvnw.cmd test
Tests run: 240, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS

docker compose build api web  (tsc --noEmit + Vite en verde)
node tools/operacion/verificar-despliegue.mjs
despliegue: rutas de proxy, cabeceras y CSP coherentes   exit=0
```

Recorrido completo en el navegador contra el servidor en marcha (búsqueda real a 18500, tarifa
movida a 500000 antes de confirmar):

1. Confirmar con el importe viejo → aviso «Viste EUR 185 y ahora el total es EUR 5.000…».
2. Confirmar de nuevo → 201 `H-81624A37` con el precio vigente.

Capturas: `precio-cambiado.png` (el aviso) y `confirmacion-escritorio.png` (pantalla final en
1280 px). De paso se corrigió un «NoPedimos» sin espacio en el texto de esa pantalla.

## Nota de git

Sin push por regla vigente: `develop` va por delante de `origin/develop` (R34–R38).