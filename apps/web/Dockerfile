FROM node:22-alpine AS construccion
WORKDIR /app

# El contexto de build es apps/web, que es la raíz del proyecto Node: aquí sí está el lockfile
# y `npm ci` funciona. En Vercel, que construye desde la raíz del repositorio, la orden lleva el
# `cd apps/web` que está en vercel.json.
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY tsconfig.json vite.config.ts index.html ./
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

  location /api/ {
    proxy_pass http://api:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
  }

  # React Router necesita este fallback: sin él, /admin/reservas da 404 al recargar.
  location / {
    try_files $uri $uri/ /index.html;
  }

  add_header X-Content-Type-Options "nosniff" always;
  add_header X-Frame-Options "DENY" always;
  add_header Referrer-Policy "strict-origin-when-cross-origin" always;
}
EOF
EXPOSE 80
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget -qO- http://localhost/ >/dev/null 2>&1 || exit 1
CMD ["nginx", "-g", "daemon off;"]