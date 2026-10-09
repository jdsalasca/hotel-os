# Ronda 178 — Distancias de lugares más legibles

## Cambio

- Los lugares a menos de 1.000 m ahora muestran metros redondeados; los de 1 km o más muestran kilómetros con coma decimal colombiana.
- Una nota aclara que la distancia es en línea recta y que «Cómo llegar» abre la ruta real; solo aparece cuando el hotel está ubicado.
- En móvil, el enlace «Cómo llegar» conserva una sola línea.
- La portada mantiene su cálculo existente; no se añadió una API duplicada ni dependencias.

## TDD

- Los dos primeros casos fallaron antes del cambio: faltaba `a 44 m` y el formato esperado `a 1,1 km`.
- Al probar sobre la implementación anterior, los tres casos de unidad/locale/valor cero fallaron por la razón esperada.
- El caso de claridad falló antes de agregar la nota.
- El caso de hotel sin ubicación falló al principio porque la nota aparecía sin una distancia calculable.
- Resultado enfocado final: `npm test -- src/paginas/PaginaInicio.test.tsx --reporter=dot` — 23/23.

## Verificación

- `npm test` — 24 archivos, 123/123 pruebas. Se mantiene el aviso existente de jsdom: `Window's scrollTo() method` no está implementado.
- `npm run test:typecheck` — exit 0.
- `npm run build` — exit 0; 70 módulos transformados. JS 373,39 kB (107,39 kB gzip), CSS 22,25 kB (4,77 kB gzip).
- Playwright en Chrome local con datos sintéticos para dos lugares; no se usaron datos de huéspedes.
- Consola y errores de página: 0.

## Revisión visual

| Vista | Viewport | Ancho documento / viewport | Límites de la sección del mapa | Resultado |
|---|---:|---:|---|---|
| Escritorio | 1440 × 1000 | 1440 / 1440 px | x=168–1272, y=236–764 | `a 44 m`, `a 1,1 km`; nota visible; enlace 103 × 35 px en una línea |
| Móvil | 390 × 844 | 390 / 390 px | x=16–374, y=74–771 | `a 44 m`, `a 1,1 km`; nota visible; enlace 103 × 35 px en una línea |

El iframe de OpenStreetMap es externo y su contenido varió entre las capturas; esta revisión valida las etiquetas, el enlace y el ajuste responsive, no la disponibilidad de sus teselas.

Capturas guardadas junto a este informe: `desktop.png` y `mobile.png`.
