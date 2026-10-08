# Ronda 102 - El bloqueo dice en cuántos minutos

Fecha: 2026-10-08.

## Diseño (brainstorming, vía acotada)
El bloqueo de login decía "Espera 15 minutos" siempre, aunque faltaran 2: el dueño veía
una ventana pegada sin saber cuándo volver. Opciones: (a) header Retry-After (invisible),
(b) segundos en el JSON + minutos en el mensaje (visible y máquina), (c) countdown vivo
(timer, complejidad sin ganancia). Va (b): `LoginThrottle.segundosRestantes()` y ambas
rutas con secreto (login y password) devuelven `reintentar_en_seg` + mensaje con minutos.
El front ya muestra el mensaje tal cual: sin cambios allá.

## Verificación real (TDD)
- Test nuevo `bloqueoDiceMinutos`: 5 fallos → 401, 6º → 429 con `reintentar_en_seg ≥ 1`
  y "minuto" en el mensaje. Vecinas de throttle verdes: **12/12, BUILD SUCCESS**.
- Despliegue + health `ok`; 429 real con minutos se ve con cuenta (no quemo el throttle
  de prod a propósito).
- Archivos del otro agente intactos.

## Archivos
- Tocados: `LoginThrottle.java`, `AdminAuthController.java`, `AdminPasswordController.java`,
  `CambioClaveAdminTest.java`.
