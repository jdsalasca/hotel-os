# Ronda 60 — Todos los planes válidos se ofrecen, no solo el primero

## El defecto (Etapa B)

La búsqueda devolvía una oferta por habitación con el primer plan con tarifa completa. Si había
dos planes válidos (Flexible y Escapada), el huésped nunca veía el segundo ni podía elegirlo: la
«selección explícita de planes» no existía.

## Lo que cambia

`disponiblesConPrecio` emite una oferta por (habitación, plan con tarifa completa): la tarjeta ya
mostraba el plan y `elegir()` ya enviaba el de la tarjeta, así que sin tocar el frontend cada
habitación sale con sus planes. El núcleo de precio se partió en `detalleParaPlan` (un plan) que
reutilizan la búsqueda (todos), el alta, el calendario y el detalle (el primero válido).

En vivo con FLEX (−20 %) y ESCAPADA nuevos: cada habitación ofrece `FLEX:16400` y
`ESCAPADA:40000`; la pantalla muestra ambas tarjetas con su plan y su total.

## Verificación

```text
.\mvnw.cmd test
Tests run: 290, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS

docker compose build api web  (tsc --noEmit + Vite en verde)
node tools/operacion/verificar-despliegue.mjs
despliegue: rutas de proxy, cabeceras y CSP coherentes   exit=0
```

Capturas: `varios-planes.png` (cuatro habitaciones × dos planes) y `varios-planes-movil.png`.