FROM node:22-alpine AS construccion
WORKDIR /app

# El contexto de build es apps/web, que es la raíz del proyecto Node: aquí sí está el lockfile
# y `npm ci` funciona. En Vercel, que construye desde la raíz del repositorio, la orden lleva el
# `cd apps/web` que está en vercel.json.
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY tsconfig.json vite.config.ts index.html ./
COPY public ./public
COPY src ./src
RUN npm run build

FROM nginx:1.27-alpine AS ejecucion
RUN apk add --no-cache wget
COPY --from=construccion /app/dist /usr/share/nginx/html
COPY <<'EOF' /etc/nginx/conf.d/default.conf
server {
  listen 80;
  server_name _;
  root /usr/share/nginx/html;
  index index.html;
  client_max_body_size 64k;

  # nginx resuelve un nombrehost de proxy_pass UNA vez, al arrancar. Si después se recrea solo el
  # contenedor de la API, esta toma una IP nueva y el web sigue apuntando a la vieja: 504 hasta que
  # se reinicie el web. Con `resolver` y una variable, nginx vuelve a resolver cada 10s.
  resolver 127.0.0.11 valid=10s ipv6=off;
  set $api http://api:8080;

  # El nginx.conf de la imagen tiene gzip comentado. El bundle de Vite son cientos de KB de JS/CSS
  # sin comprimir; sin esto cada carga de página los baja íntegros. No comprime lo que ya va
  # comprimido (imágenes, fuentes), por eso gzip_types no incluye image/*.
  gzip on;
  gzip_vary on;
  gzip_min_length 1024;
  gzip_proxied any;
  gzip_types text/plain text/css application/javascript application/json image/svg+xml;

  location /api/ {
    # $request_uri conserva la ruta y la query tal cual vinieron; sin él, el prefijo /api se pierde.
    proxy_pass $api$request_uri;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
  }

  # El baile OAuth2 vive en el backend, no en /api: la ida a Google y la vuelta con el código.
  # Sin estas dos rutas, el botón "Entrar con Google" caería en el index.html del SPA (200 vacío).
  # Ojo con el proto: detrás del túnel, el borde siempre es HTTPS, pero nginx solo ve el HTTP
  # interno. Si se reenvía $scheme, Spring construye la redirect_uri en http y Google la rechaza
  # (redirect_uri_mismatch). Por eso aquí va https fijo; en /api/ no importa porque no redirige.
  location /oauth2/ {
    proxy_pass $api$request_uri;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto https;
  }

  location /login/oauth2/ {
    proxy_pass $api$request_uri;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto https;
  }

  # React Router necesita este fallback: sin él, /admin/reservas da 404 al recargar.
  location / {
    try_files $uri $uri/ /index.html;
  }

  # Cabeceras de seguridad. La CSP no lleva 'unsafe-inline' ni 'unsafe-eval' porque el build de
  # Vite no emite scripts ni estilos en línea (comprobado: el index.html solo referencia /assets), así
  # que se puede cerrar de verdad. Si algún día se añade un script en línea, esto lo rompe de forma
  # visible en vez de dejarlo pasar en silencio.
  add_header Content-Security-Policy "default-src 'self'; base-uri 'self'; object-src 'none'; frame-ancestors 'none'; img-src 'self' data:; font-src 'self'; style-src 'self'; script-src 'self'; connect-src 'self'; form-action 'self'" always;
  add_header X-Content-Type-Options "nosniff" always;
  add_header X-Frame-Options "DENY" always;
  add_header Referrer-Policy "strict-origin-when-cross-origin" always;
  # Esta sí estaba en el Caddy y en Vercel, pero faltaba aquí: el hotel no necesita cámara ni
  # micrófono, y decirlo de más no cuesta nada.
  add_header Permissions-Policy "camera=(), microphone=(), geolocation=()" always;
}
EOF
EXPOSE 80
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget -qO- http://localhost/ >/dev/null 2>&1 || exit 1
CMD ["nginx", "-g", "daemon off;"]