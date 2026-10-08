# Ronda 92 - Auditoría de auth + deploy de lo nuevo

Fecha: 2026-10-08.

## Dolor
El dueño reporta login "pegado" sin más datos: había que mirar los logs reales en vez de
adivinar, y origin había avanzado sin desplegar.

## Cambio
Ninguno de código.
- Logs de prod: **0 lockouts de throttle, 0 logins fallidos**; un login correcto hoy 02:08.
  El único 400 es de mis propias pruebas con parámetros mal puestos. El backend autentica;
  lo pendiente es que el dueño diga qué ve (contraseña vs Google + pantallazo).
- Deploy de `7c68555` (bloquear inexistente/sin fechas) al TopNUC: sanos, health `ok`,
  amenidades/lugares 200, web 200.

## Archivos
- Solo este archivo + plan.
