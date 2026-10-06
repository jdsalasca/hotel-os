# Capturas de verificación. Sin datos reales: todo lo que aparece es demostración.
# Uso: docker compose -f compose.yaml -f compose.capturas.yaml up --build --abort-on-container-exit
FROM mcr.microsoft.com/playwright:v1.56.0-noble

WORKDIR /capturas
RUN npm init -y >/dev/null 2>&1 && npm install playwright@1.56.0 >/dev/null 2>&1

COPY capturar.mjs .
COPY diagnostico.mjs .
CMD ["node", "capturar.mjs"]