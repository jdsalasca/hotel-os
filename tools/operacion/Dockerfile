FROM alpine:3.20 AS checksum-builder

RUN apk add --no-cache build-base zlib-dev
COPY tools/operacion/flyway-checksum.c /tmp/flyway-checksum.c
RUN cc -Os -s -o /usr/local/bin/flyway-checksum /tmp/flyway-checksum.c -lz

FROM alpine:3.20

RUN apk add --no-cache sqlite bash zlib \
  && addgroup -S -g 101 hotel \
  && adduser -S -u 100 -G hotel -H hotel

COPY tools/operacion/respaldar.sh tools/operacion/restaurar.sh \
  tools/operacion/probar-respaldo.sh tools/operacion/probar-restaurar-segura.sh \
  tools/operacion/programar.sh tools/operacion/programar-simulacro.sh \
  tools/operacion/verificar-respaldo.sh /usr/local/bin/
COPY apps/api/src/main/resources/db/migration/ /usr/local/share/hotel/migrations/
COPY --from=checksum-builder /usr/local/bin/flyway-checksum /usr/local/bin/flyway-checksum

# Conserva los CRC32 que Flyway calcula sobre líneas UTF-8 sin BOM ni saltos de línea.
RUN set -eu; \
  mkdir -p /usr/local/share/hotel; \
  { \
    printf '%s\n' 'CREATE TABLE migration_checksum (version TEXT PRIMARY KEY, description TEXT NOT NULL, script TEXT NOT NULL, checksum INTEGER NOT NULL);'; \
    for migration in /usr/local/share/hotel/migrations/V*__*.sql; do \
      nombre="${migration##*/}"; \
      version="${nombre#V}"; \
      version="${version%%__*}"; \
      case "$version" in ''|*[!0-9]*) echo "versión Flyway no numérica: $nombre" >&2; exit 1;; esac; \
      descripcion="${nombre#*__}"; \
      descripcion="${descripcion%.sql}"; \
      descripcion="$(printf '%s' "$descripcion" | tr '_' ' ')"; \
      checksum="$(/usr/local/bin/flyway-checksum "$migration")"; \
      printf "INSERT INTO migration_checksum VALUES ('%s', '%s', '%s', %s);\n" "$version" "$descripcion" "$nombre" "$checksum"; \
    done; \
  } | sqlite3 /usr/local/share/hotel/flyway-checksums.sqlite3; \
  rm -f /usr/local/bin/flyway-checksum; \
  for script in /usr/local/bin/*.sh; do sed -i 's/\r$//' "$script"; done; \
  chmod +x /usr/local/bin/respaldar.sh /usr/local/bin/restaurar.sh \
    /usr/local/bin/probar-respaldo.sh /usr/local/bin/probar-restaurar-segura.sh \
    /usr/local/bin/programar.sh /usr/local/bin/programar-simulacro.sh \
    /usr/local/bin/verificar-respaldo.sh

CMD ["/usr/local/bin/probar-respaldo.sh"]
