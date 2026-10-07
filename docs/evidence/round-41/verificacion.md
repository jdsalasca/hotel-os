# Ronda 41 — El plan viaja explícito de la búsqueda al alta

## Lo que hacía

La búsqueda elegía el primer plan activo con tarifa completa sin decirlo: la oferta no traía el
plan y el alta no lo verificaba. El huésped reservaba sin saber con qué plan, y si entre medias
cambiaba el plan vigente, el precio nuevo colaba igual.

## Lo que hace ahora

La oferta trae el plan (`id`, `código`, `nombre`), la tarjeta lo muestra («Plan Flexible») y la
confirmación lo envía (`ratePlanIdEsperado`). El servicio lo compara en la misma transacción del
alta: si no es el vigente, el mismo 409 de cambio de precio con el importe y el plan que sí
valen. Sin plan en la petición (clientes viejos), todo sigue igual.

```json
{ "error": "el precio cambió desde tu búsqueda: confirma de nuevo con el importe actualizado",
  "nuevoTotalCents": 23500, "nuevaMoneda": "EUR", "nuevoRatePlanId": 1 }
```

## Verificación

```text
.\mvnw.cmd test
Tests run: 247, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS

docker compose build api web  (tsc --noEmit + Vite en verde)
node tools/operacion/verificar-despliegue.mjs
despliegue: rutas de proxy, cabeceras y CSP coherentes   exit=0
```

En vivo: la oferta trae `plan: {id: 1, codigo: "FLEX", nombre: "Flexible"}`; reservar con otro
plan da 409 con `nuevoRatePlanId: 1`; con su plan, 201 `H-C590907F`. Captura:
`oferta-con-plan.png` (tarjetas con «Plan Flexible», escritorio y móvil).

## Nota de git

Sin push por regla vigente: `develop` va por delante de `origin/develop` (R34–R41).