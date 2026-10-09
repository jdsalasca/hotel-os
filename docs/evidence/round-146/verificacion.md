# Ronda 146 - El vacío contacta de verdad (mailto/tel)

## Cambio
- El contacto del vacío ahora es clicable: `mailto:` al correo y `tel:` al
  teléfono (sin espacios para el marcador). En móvil es un toque para escribir
  o llamar; antes era texto muerto. `Vacio` no se tocó: el párrafo vive aparte
  con `campo__ayuda` existente. Cero clases y cero estilos nuevos.
- Nota: `useContactoHotel` hace casi lo mismo para otras páginas; se deja el
  duplicado a propósito (copys y formas distintas), no se unifica a ciegas.

## TDD rojo-verde
- `getByRole('link', {name})` falla sin los enlaces; con ellos, 9/9 en el archivo.

## Verificación (salida real, 2026-10-09)
- `vitest` full en verde; `tsc --noEmit` exit 0. Backend sin cambios.
- Visual: en prod no hay contacto configurado (párrafo ausente, correcto — los
  hrefs exactos se afirman en jsdom). Captura post-deploy sin regresión — ver
  `vacio-prod.png`.

## Despliegue
- Merge a `develop`, push y `compose up -d --build` en TopNUC con health OK.
