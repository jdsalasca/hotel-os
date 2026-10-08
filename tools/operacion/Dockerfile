# Prueba del ciclo de respaldo y restauración, con datos de demostración.
# No es un ejemplo: borra la base a propósito y la recupera de verdad.
FROM alpine:3.20

RUN apk add --no-cache sqlite bash

COPY respaldar.sh restaurar.sh probar-respaldo.sh programar.sh verificar-respaldo.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/respaldar.sh /usr/local/bin/restaurar.sh /usr/local/bin/probar-respaldo.sh \
  /usr/local/bin/programar.sh /usr/local/bin/verificar-respaldo.sh

CMD ["/usr/local/bin/probar-respaldo.sh"]