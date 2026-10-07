# Ronda 58 — La lista de reservas se descarga en CSV

## Lo que faltaba

Trabajar la lista fuera del panel exigía copiar a mano: no había exportación.

## Lo que se entrega

`GET /api/admin/reservas.csv` con los mismos filtros de la pantalla (`q`, `estado`) y las
mismas celdas neutralizadas del resto de exportaciones (helper `Csv` compartido, extraído del
servicio de indicadores). En la pantalla, enlace «Descargar CSV» que conserva los filtros
vigentes.

## Verificación

```text
.\mvnw.cmd test
Tests run: 288, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS

docker compose build api web  (tsc --noEmit + Vite en verde)
node tools/operacion/verificar-despliegue.mjs
despliegue: rutas de proxy, cabeceras y CSP coherentes   exit=0
```

En vivo: `reservas.csv?estado=PENDIENTE` descarga 17 líneas con cabecera; la barra muestra
buscador, Estado, Filtrar y Descargar CSV en una fila (la primera versión dejaba el enlace
colgando en otra fila: rejilla a 4 columnas). Capturas: `descargar-csv.png` y
`descargar-csv-movil.png`.
