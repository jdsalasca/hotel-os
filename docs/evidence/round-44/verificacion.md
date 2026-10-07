# Ronda 44 — Descuentos por plan, de la tarifa al tag

## Lo que pedía el hotel

Ofertas con tags de descuento. Hasta ahora el precio era el que era: ningún plan podía rebajar
sus noches y el huésped no veía ninguna oferta marcada.

## Lo que se entrega

El plan lleva `descuento_pct` (0-100, validado al crear y al modificar, migración V9 con 0 por
defecto para los planes existentes). Al totalizar, el descuento se aplica al total y se redondea
al céntimo; el total sin descuento viaja al lado para que el huésped vea lo que se ahorra:

- **Búsqueda**: cada oferta trae `descuentoPct` y `totalSinDescuentoCents`; la tarjeta muestra el
  tag «−20 %» y «antes EUR 235».
- **Detalle por noche**: plan con su descuento y antes/ahora.
- **Confirmación**: plan, tag y antes/ahora en el resumen.
- **Calendario**: el precio del día ya es el rebajado.
- **Panel**: crear plan acepta descuento y la lista de planes lo muestra y lo edita por plan.
  Las reservas ya guardadas no se tocan.

```json
{ "totalCents": 18800, "totalSinDescuentoCents": 23500, "descuentoPct": 20,
  "plan": { "id": 1, "codigo": "FLEX", "nombre": "Flexible" } }
```

De paso, la validación de noches que se había duplicado al añadir el detalle quedó en un solo
método (`precioDetallado`) que usan búsqueda, alta, calendario y detalle.

## Verificación

```text
.\mvnw.cmd test
Tests run: 251, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS

docker compose build api web  (tsc --noEmit + Vite en verde)
Flyway V9 aplicada en vivo sobre la base con datos (v8 → v9, 0.018 s)
node tools/operacion/verificar-despliegue.mjs
despliegue: rutas de proxy, cabeceras y CSP coherentes   exit=0
```

En vivo con FLEX al −20 %: oferta 23500 → 18800; reserva `H-98028FBE` guarda 18800 (el que se
cobra); redondeo comprobado (199 −10 % = 179). Capturas: `oferta-con-descuento.png`
(escritorio, tags «−20 %» con antes/ahora) y `confirmacion-con-descuento-movil.png` (390 px).

## Nota de git

Sin push por regla vigente. Commit selectivo de esta ronda: el otro agente trabaja en el árbol
(sus páginas legales ya están en `2824f43`) y no se toca nada suyo.