# Ronda 37 — Calendario público: qué noches hay lugar y desde qué precio

## Lo que pedía el hotel

Ver un calendario con las habitaciones disponibles y consultar la ocupación por días. Hasta ahora
el único calendario era el del panel (reservas por habitación, solo personal) y el huésped solo
podía buscar por rango a ciegas: o acertaba las fechas o veía el vacío.

## Lo que se entrega

`GET /api/disponibilidad/calendario?mes=YYYY-MM&huespedes=N` devuelve el mes día por día con
habitaciones vendibles y precio desde (una noche). Usa las mismas reglas que la búsqueda, así que
no hay dos verdades. Mes mal formado o huéspedes inválidos son 400; va topado por el mismo límite
de lecturas públicas y la ruta se abrió explícita en el `permitAll` (el matcher es exacto y sin
eso era 401).

En la página de reserva, sección «Calendario de disponibilidad»: rejilla del mes con días
pasados apagados, días llenos marcados y días libres con cantidad y precio desde. Tocar un día
libre fija llegada/salida (esa noche) y lanza la búsqueda, que muestra las habitaciones con sus
detalles y totales. Las respuestas viejas se ignoran si el huésped cambia de mes antes de que
vuelvan.

```json
{ "mes": "2026-12", "huespedes": 2,
  "dias": [{ "fecha": "2026-12-01", "disponibles": 2, "desdeCents": 150000, "moneda": "COP" },
           { "fecha": "2026-12-03", "disponibles": 0 }] }
```

## Verificación

```text
.\mvnw.cmd test
Tests run: 238, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS

docker compose build api web  (tsc --noEmit + Vite en verde)
node tools/operacion/verificar-despliegue.mjs
despliegue: rutas de proxy, cabeceras y CSP coherentes   exit=0
```

En el navegador, contra el servidor en marcha: el mes carga 31 días con precios, el clic en el
15 busca del 15 al 16 para 2 huéspedes y muestra las habitaciones. Cada día es un botón con su
frase («15 de oct de 2026: 4 habitaciones libres desde EUR 100») para lector de pantalla.
Capturas: `calendario-publico.png` (escritorio) y `calendario-publico-movil.png` (390 px, la
rejilla de 7 columnas cabe sin desbordar).

## Nota de git

Sin push por regla vigente: `develop` va por delante de `origin/develop` (R34–R37).