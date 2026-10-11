# Prueba del ciclo de respaldo y restauración, con datos de demostración.
# No es un ejemplo: borra la base a propósito y la recupera de verdad.
FROM alpine:3.20

RUN apk add --no-cache sqlite bash
RUN addgroup -S -g 101 hotel \
  && adduser -S -u 100 -G hotel -H hotel

COPY respaldar.sh restaurar.sh probar-respaldo.sh probar-restaurar-segura.sh programar.sh programar-simulacro.sh verificar-respaldo.sh /usr/local/bin/
# Git for Windows can check out shell scripts with CRLF; normalize them for Alpine /bin/sh.
RUN for script in /usr/local/bin/*.sh; do sed -i 's/\r$//' "$script"; done
RUN chmod +x /usr/local/bin/respaldar.sh /usr/local/bin/restaurar.sh /usr/local/bin/probar-respaldo.sh \
  /usr/local/bin/probar-restaurar-segura.sh \
  /usr/local/bin/programar.sh /usr/local/bin/programar-simulacro.sh /usr/local/bin/verificar-respaldo.sh

CMD ["/usr/local/bin/probar-respaldo.sh"]
