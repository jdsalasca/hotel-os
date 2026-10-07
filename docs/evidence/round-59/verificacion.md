# Ronda 59 — La reconfirmación lleva el plan nuevo, no el viejo

## El defecto (Etapa B)

El 409 ya devolvía `nuevoRatePlanId`, pero `cambioDePrecio()` lo tiraba: el segundo POST seguía
enviando `eleccion.plan.id`. Si lo que cambió fue el plan (mismo importe), el huésped quedaba
atrapado en 409 tras 409 aunque aceptara. Reproducido con plan 9999 inexistente: antes, aceptar
volvía a chocar; ahora, el retry lleva el plan vigente.

## Lo que cambia

- `cambioDePrecio()` conserva `nuevoRatePlanId`; el reintento envía importe + moneda + plan
  vigentes.
- El resumen visible también se refresca: tras el 409 se relee el detalle (plan vigente) y el
  aviso y el lateral muestran el plan nuevo («con el plan Flexible»). Reconfirmar lo que se ve,
  no un número oculto.

## Verificación

```text
docker compose build web  (tsc --noEmit + Vite en verde)
```

En vivo: oferta con plan 9999 → 409 → aviso «Viste EUR 156 y ahora el total es EUR 156 con el
plan Flexible» → aceptar → 201 `H-2D6E844E`. Sin cambios de backend en esta ronda (la suite
sigue en 286 en verde de la R57).

Capturas: `reconfirmacion-con-plan.png` y `reconfirmacion-con-plan-movil.png`.