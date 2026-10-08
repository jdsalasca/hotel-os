# Auditoría de accesibilidad con axe. No forma parte del despliegue ni toca datos:
# solo lee páginas públicas y escribe el informe. Imagen con navegadores ya incluidos.
FROM mcr.microsoft.com/playwright:v1.56.0-noble

WORKDIR /accesibilidad
COPY package.json package-lock.json* ./
RUN npm install --no-audit --no-fund 2>&1 | tail -2

COPY auditar.mjs ./
CMD ["node", "auditar.mjs"]
