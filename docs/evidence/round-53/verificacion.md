# Ronda 53 — Los bloqueos se retiran desde el panel y vuelven a la venta

## Lo que faltaba

Un bloqueo aplicado no se podía quitar: ni lista ni retiro. Un mantenimiento que termina antes
dejaba noches sin vender hasta que el rango expirase solo.

## Lo que se entrega

`GET /api/admin/bloqueos` lista los vigentes (aún cubren noches futuras) con habitación, fechas
y motivo; `POST /api/admin/bloqueos/{id}/retirar` lo borra. Es POST y no DELETE para que quede
en la auditoría del panel como el resto de escrituras. Lo inexistente es 404, no un 200
silencioso. En Inventario, tarjeta «Bloqueos vigentes» a ancho completo con botón Retirar por
fila (la primera versión iba en la rejilla de tercios y la tabla invadía la tarjeta vecina: se
movió a sección propia).

## Verificación

```text
.\mvnw.cmd test
Tests run: 274, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS

docker compose build api web  (tsc --noEmit + Vite en verde)
node tools/operacion/verificar-despliegue.mjs
despliegue: rutas de proxy, cabeceras y CSP coherentes   exit=0
```

En vivo: creado el bloqueo de la 104 (Obra baño, 1–6 sep 2027), retirado el de la 101 desde el
botón —la tabla pasó de 2 filas a 1 y la 101 volvió a las ofertas del 10–12 de noviembre.
Capturas: `bloqueos-vigentes.png` (escritorio) y `bloqueos-vigentes-movil.png` (390 px, la fila
se vuelve tarjeta con etiquetas).

## Nota de git

Sin push por regla vigente. Solo archivos de esta ronda en el commit.