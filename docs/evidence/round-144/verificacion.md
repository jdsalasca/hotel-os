# Ronda 144 - Base SEO: robots, sitemap y previa para compartir

## Hallazgo en prod
`GET /robots.txt` devolvía el `index.html` del SPA (contenido y tipo erróneos
para rastreadores) y no existían `sitemap.xml` ni etiquetas Open Graph: el
enlace compartido por WhatsApp/redes salía pelado. Para un hotel de reserva
directa, eso es visibilidad perdida.

## Cambio
- `apps/web/public/robots.txt`: abre `/`, cierra `/admin/`, anuncia el sitemap.
- `apps/web/public/sitemap.xml`: las 5 rutas públicas.
- `index.html`: `og:title/description/type/locale/image` + `twitter:card` con la
  portada existente (`/img/portada-villa-de-leyva.jpg`).
- `tools/operacion/verificar-despliegue.mjs`: sección 5 que exige los tres
  (el guion ya existía para que el proxy no se rompiera en silencio).

## TDD rojo-verde
- Guion con los checks nuevos: 6 FALLA + exit 1 sin los archivos; con ellos,
  todo ok + exit 0.

## Verificación (salida real, 2026-10-09)
- `node tools/operacion/verificar-despliegue.mjs` → exit 0.
- En prod tras el deploy: `curl /robots.txt` (text/plain con Disallow),
  `curl /sitemap.xml` (5 loc) y OG en el HTML servido.

## Despliegue
- Merge a `develop`, push y `compose up -d --build` en TopNUC con health OK.
