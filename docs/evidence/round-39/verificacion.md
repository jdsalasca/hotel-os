# Ronda 39 — Cada oferta muestra su precio noche por noche y su plan

## Lo que pedía el hotel

Detalles de las habitaciones: la tarjeta decía el total y nada más. El huésped no podía ver qué
vale cada noche ni con qué plan está calculado —y el plan, además, se elegía en silencio.

## Lo que se entrega

`GET /api/disponibilidad/detalle?roomId=&llegada=&salida=&huespedes=` devuelve habitación, tipo,
plan y precio noche por noche con el total. Son las mismas reglas que la búsqueda y que el alta:
si no está a la venta, 404 con motivo en vez de un desglose vacío. Fechas mal formadas o
invertidas son 400. La ruta se abrió explícita en el `permitAll`.

```json
{ "habitacion": { "codigo": "601" }, "tipo": { "capacidadMax": 2 },
  "plan": { "codigo": "PES_D", "nombre": "Plan pesos" },
  "noches": [{ "fecha": "2026-12-10", "precioCents": 150000 },
             { "fecha": "2026-12-11", "precioCents": 180000 }],
  "totalCents": 330000, "moneda": "COP" }
```

En la tarjeta, «Ver detalle por noche» despliega el plan y cada noche con su importe, con
`aria-expanded` y sin navegar. De paso, la validación de noches (cerradas, mín./máx. estancia,
tarifa completa) que vivía copiada en dos métodos ahora vive una sola vez en `precioDetallado`,
que usan la búsqueda, el alta, el calendario y el detalle.

## Verificación

```text
.\mvnw.cmd test
Tests run: 242, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS

docker compose build api web  (tsc --noEmit + Vite en verde)
node tools/operacion/verificar-despliegue.mjs
despliegue: rutas de proxy, cabeceras y CSP coherentes   exit=0
```

En el navegador: búsqueda real del 11 al 13 de marzo de 2027, «Ver detalle por noche» muestra
Plan Flexible con 11 mar → EUR 2.500 y 12 mar → EUR 2.500 sumando el total EUR 5.000.
Capturas: `detalle-por-noche.png` (escritorio) y `detalle-por-noche-movil.png` (390 px, la
tarjeta apila sin desbordar).

## Nota de git

Sin push por regla vigente: `develop` va por delante de `origin/develop` (R34–R39).