#!/bin/sh
# Prueba real del ciclo: respaldo -> destrucción de la base -> restauración -> verificación.
# Si el procedimiento no funciona, es mejor que se rompe aquí y no a las 3 de la mañana.
set -eu

DB="${1:-/data/hotel.sqlite3}"
BACKUPS="${2:-/backups}"
export BACKUP_RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-30}"

fallo() { echo "FALLO: $1" >&2; exit 1; }

echo "=== 1. Datos de partida ==="
sqlite3 "$DB" "SELECT COUNT(*) || ' reservas en la base'" || fallo "no se puede leer la base"
RESERVAS_ANTES="$(sqlite3 "$DB" 'SELECT COUNT(*) FROM reservations;')"

echo
echo "=== 2. Respaldo consistente ==="
salida="$(/usr/local/bin/respaldar.sh "$DB" "$BACKUPS")"
echo "$salida"
ARCHIVO="$(echo "$salida" | grep -o "$BACKUPS/hotel-[0-9TZ]*\.sqlite3" | head -1)"
[ -f "$ARCHIVO" ] || fallo "no se creó el archivo de respaldo"
INTEGRIDAD="$(sqlite3 "$ARCHIVO" 'PRAGMA integrity_check;')"
[ "$INTEGRIDAD" = "ok" ] || fallo "el respaldo no pasa integrity_check: $INTEGRIDAD"
RESERVAS_RESPALDO="$(sqlite3 "$ARCHIVO" 'SELECT COUNT(*) FROM reservations;')"
[ "$RESERVAS_RESPALDO" = "$RESERVAS_ANTES" ] \
  || fallo "el respaldo tiene $RESERVAS_RESPALDO reservas y la base tenía $RESERVAS_ANTES"
echo "respaldo con $RESERVAS_RESPALDO reservas, íntegro"

echo
echo "=== 3. Destrucción de la base (a propósito) ==="
sqlite3 "$DB" 'DROP TABLE IF EXISTS reservation_items; DROP TABLE IF EXISTS reservations;'
sqlite3 "$DB" 'VACUUM;'
DESPUE="$(sqlite3 "$DB" "SELECT COUNT(*) FROM sqlite_master WHERE name='reservations';")"
[ "$DESPUE" = "0" ] || fallo "no se pudo destruir la tabla de reservas"
echo "tabla reservations eliminada. Ahora el sistema no tiene datos:"

echo
echo "=== 4. Restauración ==="
/usr/local/bin/restaurar.sh "$DB" "$ARCHIVO"

echo
echo "=== 5. Verificación posterior ==="
INTEGRIDAD_FINAL="$(sqlite3 "$DB" 'PRAGMA integrity_check;')"
[ "$INTEGRIDAD_FINAL" = "ok" ] || fallo "la base restaurada no pasa integrity_check: $INTEGRIDAD_FINAL"
RESERVAS_DESPUES="$(sqlite3 "$DB" 'SELECT COUNT(*) FROM reservations;')"
[ "$RESERVAS_DESPUES" = "$RESERVAS_ANTES" ] \
  || fallo "se esperaban $RESERVAS_ANTES reservas y hay $RESERVAS_DESPUES"
TABLAS="$(sqlite3 "$DB" "SELECT COUNT(*) FROM sqlite_master WHERE type='table';")"
[ "$TABLAS" -ge 8 ] || fallo "faltan tablas tras restaurar (hay $TABLAS)"

echo "integridad:  $INTEGRIDAD_FINAL"
echo "tablas:      $TABLAS"
echo "reservas:    $RESERVAS_DESPUES (antes de destruir: $RESERVAS_ANTES)"
echo
echo "CICLO DE RESPALDO Y RESTAURACION VERIFICADO"