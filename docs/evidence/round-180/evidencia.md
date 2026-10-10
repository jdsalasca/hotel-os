# Ronda 180 — Ubicar el hotel en el mapa sin teclear coordenadas

## Problema real medido en producción

El hotel no tiene coordenadas. El mapa público y el panel de distancias de las rondas
177/178/179 ya calculaban y mostraban `metros`, pero nunca se veían:

```
GET https://hotel.eridu.top/api/lugares
{"hotel":{"ubicado":false},"lugares":[]}
```

Y en el panel el hotelero tecleaba `5.635` / `-73.525` a ciegas, sin ninguna vista previa.
Este era el punto que el usuario reportaba como *"no veo un mapa interactivo"* y
*"que el hotelero pueda poner en el GPS dónde queda el hotel"*.

## Cambio

`apps/web/src/paginas/PaginaAdminHotel.tsx`: botón **"Ubicar en el mapa"** que abre un
visor de OpenStreetMap. Los botones Sur/Norte/Oeste/Este/Acercar mueven el punto y
**"Usar este punto"** lo deja en los campos Latitud/Longitud listos para guardar.

Sin backend, sin migración y sin SCSS nuevo: reusa `.mapa__marco`, `.acciones-lugar`,
`.campo__ayuda`, `.boton--*`, `.pila.gap-e1`.

### Corrección de raíz hallada en la verificación visual

La primera versión movía el *encuadre* del mapa y guardaba su centro. Al mirarla en el
navegador appeared un bug real: el visor de OSM trae sus propios controles de zoom y
arrastre, así que el hotelero podía arrastrar el mapa, ver un sitio y **guardar unas
coordenadas distintas de las que estaba viendo** — justo lo contrario de lo que promete
el texto de ayuda.

Se invirtió el modelo: **el punto verde es la fuente de verdad**. Los botones lo mueven y
el visor se recentra en él en cada pulsación, así que lo guardado nunca se desincroniza de
lo mostrado. El test cubre precisamente eso.

## Pruebas

TDD RED → GREEN:

```
Unable to find an accessible element with the role "button" and name /ubicar en el mapa/i
```

Suite completa con typecheck:

```
Test Files  27 passed (27)
Tests       148 passed (148)
Type Errors no errors
```

Build:

```
dist/assets/index-T5R-f4i9.js   381.53 kB │ gzip: 109.61 kB
✓ built in 2m 37s
```

## Verificación en navegador real

`mapa-ubicacion.png` — punto centrado, mapa a ancho completo (marco 1002 px).

Comportamiento comprobado sobre la app viva, no sobre el test:

| Acción | `marker` del visor | Campo guardado |
|---|---|---|
| Abrir el mapa | `5.65,-73.52` | `5.65` / `-73.52` |
| Pulsar **Norte** | `5.655,-73.52` | `5.655` / `-73.52` |
| Pulsar **Acercar** | zoom correcto | sin cambios |

## Pendiente

Falta que el hotelero guarde su punto real; mientras `latitud`/`longitud` estén vacíos en
producción el mapa público seguirá oculto. Es un paso de datos, no de código.