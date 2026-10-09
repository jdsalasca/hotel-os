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
- En prod tras el deploy: `/sitemap.xml` 200 text/xml con las 5 loc; OG completo
  en el HTML servido; `/api/health` ok.
- `/robots.txt` aún devuelve el SPA por CACHÉ DEL BORDE (`cf-cache-status: HIT`
  de cuando era fallback): el origen ya lo sirve bien (200 text/plain verificado
  dentro del contenedor). ACCIÓN DEL DUEÑO: Cloudflare → Caching → purgar
  `/robots.txt` (o Purge Everything).

## Despliegue
- Merge a `develop`, push y `compose up -d --build` en TopNUC con health OK.
