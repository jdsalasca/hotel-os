# Ronda 228 — Lo que bloquea la web, arriba del formulario

## El problema

`PaginaAdminPanel` avisa de que falta el GPS y manda a `/admin/hotel`. Pero allí los campos
de latitud/longitud son **los últimos** del formulario, nueve campos después de algo relevante.

Medido sobre `adcb014` antes del cambio (1366×900):

| Métrica | Valor |
| --- | --- |
| Alto de la página | 1382 px |
| Botón `Ubicar en el mapa` | y = 934 |
| Viewport | 900 px |
| Visible en el pliegue | sí, por 34 px — y solo en escritorio ancho |

En móvil la página mide 2179 px: el botón quedaba a y=371 sobre un pliegue de 844 px, es
decir, había que recorrer casi diez campos para llegar a lo que bloquea la venta.

Es decir: el aviso del panel funcionaba, pero la página a la que mandaba escondía justo lo
que había que arreglar.

## El cambio

`apps/web/src/paginas/PaginaAdminHotel.tsx`:

1. Estado `sinPunto: boolean | null`, calculado en la carga inicial. Se mira el **texto**, no
   `Number()`: `Number('')` es `0`, que es un número válido y dejaría pasar el hueco.
   `null` mientras no se sabe (fallo de lectura) — no se afirma nada sin evidencia.
2. `Aviso` arriba del formulario, solo cuando `sinPunto`, con botón que abre el selector.
3. El botón del final del formulario se oculta cuando el aviso está: dos botones con el mismo
   nombre compitiendo por el mismo destino es peor que uno bien puesto.

El `Aviso` ya existía y está en producción desde R225. Sin SCSS nuevo, sin componente nuevo.

## Verificación

### TDD

El test RED falló primero:

```
FAIL src/paginas/PaginaAdminHotel.test.tsx > lo que bloquea la web, arriba del formulario
  > con el GPS vacío, avisa arriba y abre el selector de un clic
TestingLibraryElementError: Unable to find an element with the text: /el mapa no aparece/i
Tests  1 failed | 7 passed (8)
```

La primera implementación pasó los tests nuevos pero **rompió el de R180**:

```
Found multiple elements with the role "button" and name `/ubicar en el mapa/i`
Tests  2 failed | 6 passed (8)
```

Fallo real de diseño, no del test: de ahí salió ocultar el botón de abajo.

### Navegador real (Chromium, stack `hotelr228`, endpoints interceptados con el estado de producción)

| Comprobación | Resultado |
| --- | --- |
| GPS vacío → aviso presente | sí |
| Aviso dentro del pliegue 1366×900 | sí, y = 255 |
| Botón del aviso dentro del pliegue | sí, y = 371 |
| Un clic abre el mapa | sí, iframe OSM cargado, botón `Usar este punto` presente |
| GPS puesto → aviso | desaparece |
| GPS puesto → botones `Ubicar en el mapa` | 1 (sin duplicados) |
| Móvil 390×844 → aviso en el pliegue | sí |
| Consola | solo 502 de `/api/yo` y `/api/hotel`: falta el backend en este stack, no del cambio |

Antes → después, botón `Ubicar en el mapa`:

| | Antes | Después |
| --- | --- | --- |
| Escritorio 1366×900 | y = 934 (al borde del pliegue) | y = 371 (visible) |
| Móvil 390×844 | y = 371 de 2179 px | dentro del pliegue, sin scroll |

### Suite

```
Test Files  32 passed (32)
     Tests  225 passed (225)
Type Errors  no errors
```

### Build

```
dist/assets/index-BPd3CeP7.js   400.49 kB │ gzip: 115.03 kB
✓ built in 10m
```

## Capturas

- `datos-hotel-escritorio-antes.png` — escritorio, aviso visible sin scroll.
- `datos-hotel-movil.png` — móvil, aviso completo en el pliegue.
- `datos-hotel-mapa-abierto.png` — un clic desde el aviso abre el mapa de OpenStreetMap.
- `datos-hotel-escritorio.png` — captura inicial de auditoría, botón al borde del pliegue.

## Nota de alcance

El panel avisa también del contacto ausente. En esta pantalla **no** hace falta un aviso para
eso: correo y teléfono ya están en la primera fila del formulario, a la vista. El único
bloqueo escondido era el GPS, y es el único que este aviso cubre.

## Lo que sigue bloqueado

El aviso no crea el punto del hotel: eso lo hace el hotelero. Sigue haciendo falta que guarde
en producción el GPS, los tipos de habitación y el contacto para que la web pública venda y
muestre el mapa.
