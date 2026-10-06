# Herramienta de DEMOSTRACION: borra las reservas creadas por las capturas para poder repetirlas.
#
# ⚠️  Solo para desarrollo. En producción esto destruiria el historico de reservas:
#     jamas ejecutes este servicio contra un despliegue con datos reales.
FROM alpine:3.20

RUN apk add --no-cache sqlite

# Borra reservas de los correos de demostración y deja el historial coherente con esa reserva.
# El orden importa por las claves foraneas: primero los hijos, despues la cabecera.
CMD ["sh", "-c", "\
  sqlite3 /data/hotel.sqlite3 'PRAGMA busy_timeout=5000;' && \
  sqlite3 /data/hotel.sqlite3 \"\
    DELETE FROM reservation_history WHERE reservation_id IN \
      (SELECT id FROM reservations WHERE email LIKE '%@example.com');\" && \
  sqlite3 /data/hotel.sqlite3 \"\
    DELETE FROM reservation_items WHERE reservation_id IN \
      (SELECT id FROM reservations WHERE email LIKE '%@example.com');\" && \
  sqlite3 /data/hotel.sqlite3 \"\
    DELETE FROM reservations WHERE email LIKE '%@example.com';\" && \
  sqlite3 /data/hotel.sqlite3 'PRAGMA wal_checkpoint(TRUNCATE);' && \
  echo 'reservas de demostracion borradas' && \
  sqlite3 /data/hotel.sqlite3 \"SELECT COUNT(*) FROM reservations;\""]