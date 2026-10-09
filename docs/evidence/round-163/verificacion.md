# Ronda 163 - Hotel en datos estructurados (JSON-LD)

## Cambio
- `index.html`: bloque `application/ld+json` con `@type Hotel` (nombre, url,
  imagen de portada y dirección Sáchica/Boyacá/CO, todo de la propia landing;
  sin teléfono ni precios inventados).
- `verificar-despliegue.mjs` exige JSON-LD + tipo Hotel.

## TDD rojo-verde
- Verificador: 2 FALLA sin el bloque; con él, exit 0. JSON validado por parseo.

## Verificación (salida real, 2026-10-09)
- En prod tras el deploy: bloque presente y válido en el HTML servido.

## Despliegue
- Merge a `develop`, push y `compose up -d --build` en TopNUC con health OK.
