# Ronda 87 - El perf del colega, en vivo

Fecha: 2026-10-08.

## Dolor
Origin avanzó con su perf de calendario y prod seguía atrás.

## Cambio
Ninguno de código: desplegar y verificar lo suyo.
- Deploy de `e94edbf` (calendario en una lectura por tipo y plan) al TopNUC.
- `/api/disponibilidad/calendario?mes=2026-11` responde el mes con forma correcta
  (inventario vacío en prod: 0 disponibles, que es lo real).
- Búsqueda inválida responde 400 en vivo (su Etapa F funcionando, no solo en tests).
- Health `ok`, ambas sanas.

## Archivos
- Solo este archivo + plan.
