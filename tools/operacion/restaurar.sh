# Restauración desde un respaldo. Se ejecuta con la aplicación DETENIDA.
#
# Por qué detenida: restaurar sobre un archivo que tiene una escritura en curso deja el WAL
# inconsistente. El orden correcto es: parar -> sustituir -> arrancar -> verificar.
#
# Uso:  ./restaurar.sh /data/hotel.sqlite3 /backups/hotel-20261006T120000Z.sqlite3
set -eu

DESTINO="${1:-/data/hotel.sqlite3}"
RESPALDO="${2:?falta la ruta del respaldo a restaurar}"

if [ ! -f "$RESPALDO" ]; then
  echo "ERROR: no existe el respaldo $RESPALDO" >&2
  exit 1
fi

# 1. El respaldo debe estar sano antes de tocar la base viva.
INTEGRIDAD="$(sqlite3 "$RESPALDO" 'PRAGMA integrity_check;' 2>&1)"
if [ "$INTEGRIDAD" != "ok" ]; then
  echo "ERROR: el respaldo no pasa integrity_check ($INTEGRIDAD). No se restaura nada." >&2
  exit 1
fi
echo "respaldo verificado: integridad ok"

# 2. Se guarda el estado actual por si la restauración hay que revertir.
if [ -f "$DESTINO" ]; then
  COPIA="$(dirname "$DESTINO")/hotel-antes-de-restaurar-$(date -u +%Y%m%dT%H%M%SZ).sqlite3"
  sqlite3 "$DESTINO" ".backup '$COPIA'"
  echo "estado actual guardado en $COPIA"
fi

# 3. Se sustituye el archivo y se limpian los archivos auxiliares del WAL.
#    Sin borrar el -wal viejo, SQLite puede aplicar transacciones que ya no corresponden.
rm -f "$DESTINO" "$DESTINO-wal" "$DESTINO-shm"
cp "$RESPALDO" "$DESTINO"

# 4. Comprobación posterior: no basta con copiar, hay que verificar.
INTEGRIDAD_FINAL="$(sqlite3 "$DESTINO" 'PRAGMA integrity_check;' 2>&1)"
TABLAS="$(sqlite3 "$DESTINO" "SELECT COUNT(*) FROM sqlite_master WHERE type='table';")"
RESERVAS="$(sqlite3 "$DESTINO" 'SELECT COUNT(*) FROM reservations;' 2>/dev/null || echo '?')"

echo "restauracion completada"
echo "  archivo:   $DESTINO"
echo "  integridad: $INTEGRIDAD_FINAL"
echo "  tablas:     $TABLAS"
echo "  reservas:   $RESERVAS"
echo
echo "Arranca la aplicacion y comprueba /api/health antes de darla por buena."