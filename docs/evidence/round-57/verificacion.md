# Ronda 57 — La cuenta a la vista: abonos en el detalle y saldo en el comprobante

## Lo que faltaba

La API de abonos de la R56 no se veía en ningún lado: ni el personal podía registrar sin curl ni
el huésped sabía lo que debía.

## Lo que se entrega

- En el detalle del panel, sección «Cuenta»: abonado y pendiente, movimientos con actor y marca
  de anulación con su botón, y formulario de abono (importe + concepto). Refresca el detalle al
  guardar.
- En el comprobante (panel y consulta pública): líneas de Abonado y Pendiente cuando hay precio
  acordado; sin precio no hay saldo que calcular y no salen.

## Verificación

```text
.\mvnw.cmd test
Tests run: 286, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS

docker compose build api web  (tsc --noEmit + Vite en verde)
node tools/operacion/verificar-despliegue.mjs
despliegue: rutas de proxy, cabeceras y CSP coherentes   exit=0
```

Recorrido en el navegador: reserva de EUR 156 → abono 50 (pendiente 106) → anulación (abonado 0,
pendiente 156, movimiento marcado). Capturas: `cuenta-abonos.png` y `cuenta-abonos-movil.png`.

## Nota de git

Sin push por regla vigente. Solo archivos de esta ronda en el commit.