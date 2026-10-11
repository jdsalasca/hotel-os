#!/bin/sh
# Prueba aislada de la restauración. Solo crea bases sintéticas bajo un directorio temporal.
set -eu

RESTAURAR="${RESTAURAR_BIN:-/usr/local/bin/restaurar.sh}"
TEMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/probar-restaurar-segura-XXXXXX")"
HOLDER_PID=""

fallo() {
  echo "FALLO: $1" >&2
  exit 1
}

limpiar() {
  if [ -n "$HOLDER_PID" ]; then
    printf '.quit\n' >&3 2>/dev/null || true
    exec 3>&- 2>/dev/null || true
    wait "$HOLDER_PID" 2>/dev/null || true
  fi
  rm -rf "$TEMP_DIR"
}
trap limpiar EXIT INT TERM

huella() {
  cksum "$1" | awk '{print $1 ":" $2}'
}

echo "=== 1. Una restauración válida conserva su esquema y sus reservas ==="
BUENO="$TEMP_DIR/hotel-valido.sqlite3"
DESTINO_VALIDO="$TEMP_DIR/destino-valido.sqlite3"
sqlite3 "$BUENO" <<'SQL'
CREATE TABLE flyway_schema_history (version TEXT);
INSERT INTO flyway_schema_history VALUES ('1');
CREATE TABLE users (id INTEGER PRIMARY KEY);
CREATE TABLE reservations (codigo TEXT PRIMARY KEY);
CREATE TABLE rooms (id INTEGER PRIMARY KEY);
CREATE TABLE room_types (id INTEGER PRIMARY KEY);
CREATE TABLE rates (id INTEGER PRIMARY KEY);
INSERT INTO reservations VALUES ('H-SINTETICA');
SQL
sqlite3 "$DESTINO_VALIDO" "CREATE TABLE anterior (valor TEXT); INSERT INTO anterior VALUES ('reemplazable');"
"$RESTAURAR" "$DESTINO_VALIDO" "$BUENO"
[ "$(sqlite3 "$DESTINO_VALIDO" 'SELECT COUNT(*) FROM reservations;')" = "1" ] \
  || fallo "la restauración válida no conservó la reserva sintética"
[ "$(sqlite3 "$DESTINO_VALIDO" "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='flyway_schema_history';")" = "1" ] \
  || fallo "la restauración válida no conservó el historial Flyway"

echo
echo "=== 2. Una base íntegra sin esquema se rechaza antes de tocar destino o WAL ==="
INVALIDO="$TEMP_DIR/sqlite-vacia.sqlite3"
DESTINO="$TEMP_DIR/destino-protegido.sqlite3"
FIFO="$TEMP_DIR/sqlite-holder.fifo"
sqlite3 "$INVALIDO" 'PRAGMA user_version=1;'
[ "$(sqlite3 "$INVALIDO" 'PRAGMA integrity_check;')" = "ok" ] \
  || fallo "la base SQLite sintética no presenta el caso íntegro esperado"
# Mantiene abierta una conexión sin transacción para dejar WAL/SHM reales y estables mientras
# se intenta la restauración inválida. El lector solo se cierra después de comparar los archivos.
mkfifo "$FIFO"
sqlite3 "$DESTINO" < "$FIFO" > "$TEMP_DIR/sqlite-holder.log" 2>&1 &
HOLDER_PID=$!
exec 3>"$FIFO"
printf "PRAGMA journal_mode=WAL;\nPRAGMA wal_autocheckpoint=0;\nCREATE TABLE sentinel (valor TEXT PRIMARY KEY);\nINSERT INTO sentinel VALUES ('destino-original');\n" >&3
sleep 1
[ -f "$DESTINO-wal" ] || fallo "no se pudo preparar un WAL real para la regresión"
[ -f "$DESTINO-shm" ] || fallo "no se pudo preparar un SHM real para la regresión"

DESTINO_ANTES="$(huella "$DESTINO")"
WAL_ANTES="$(huella "$DESTINO-wal")"
SHM_ANTES="$(huella "$DESTINO-shm")"
set +e
SALIDA="$("$RESTAURAR" "$DESTINO" "$INVALIDO" 2>&1)"
CODIGO=$?
set -e
if [ "$CODIGO" -eq 0 ]; then
  fallo "el restaurador aceptó una base íntegra sin historial Flyway ni tablas del hotel: $SALIDA"
fi
case "$SALIDA" in
  *"restauracion completada"*) fallo "el restaurador anunció éxito tras rechazar el respaldo" ;;
esac
[ "$(huella "$DESTINO")" = "$DESTINO_ANTES" ] || fallo "el archivo principal de destino cambió ante un respaldo inválido"
[ -f "$DESTINO-wal" ] && [ "$(huella "$DESTINO-wal")" = "$WAL_ANTES" ] \
  || fallo "el WAL del destino cambió ante un respaldo inválido"
[ -f "$DESTINO-shm" ] && [ "$(huella "$DESTINO-shm")" = "$SHM_ANTES" ] \
  || fallo "el SHM del destino cambió ante un respaldo inválido"

printf '.quit\n' >&3
exec 3>&-
wait "$HOLDER_PID"
HOLDER_PID=""
[ "$(sqlite3 "$DESTINO" 'SELECT valor FROM sentinel;')" = "destino-original" ] \
  || fallo "el destino no conserva sus datos originales después de cerrar SQLite"
echo "respaldo inválido rechazado; destino, WAL y SHM permanecieron intactos"
echo
echo "=== 3. Un fallo posterior no informa éxito y recupera el destino previo ==="
BUENO_NUEVO="$TEMP_DIR/hotel-nuevo.sqlite3"
sqlite3 "$BUENO_NUEVO" <<'SQL'
CREATE TABLE flyway_schema_history (version TEXT);
INSERT INTO flyway_schema_history VALUES ('2');
CREATE TABLE users (id INTEGER PRIMARY KEY);
CREATE TABLE reservations (codigo TEXT PRIMARY KEY);
CREATE TABLE rooms (id INTEGER PRIMARY KEY);
CREATE TABLE room_types (id INTEGER PRIMARY KEY);
CREATE TABLE rates (id INTEGER PRIMARY KEY);
INSERT INTO reservations VALUES ('H-NUEVA');
SQL
SQLITE_REAL="$(command -v sqlite3)"
FALLO_BIN="$TEMP_DIR/fallo-bin"
FALLO_MARCADO="$TEMP_DIR/fallo-inyectado"
mkdir -p "$FALLO_BIN"
cat > "$FALLO_BIN/sqlite3" <<'SH'
#!/bin/sh
if [ "$1" = "$DESTINO_FALLO" ] \
  && [ "$2" = 'SELECT COUNT(*) FROM reservations;' ] \
  && [ ! -e "$MARCADOR_FALLO" ]; then
  : > "$MARCADOR_FALLO"
  echo "fallo de consulta simulado" >&2
  exit 1
fi
exec "$SQLITE_REAL" "$@"
SH
chmod +x "$FALLO_BIN/sqlite3"
PATH_ANTERIOR="$PATH"
export DESTINO_FALLO="$DESTINO_VALIDO" MARCADOR_FALLO="$FALLO_MARCADO" SQLITE_REAL
PATH="$FALLO_BIN:$PATH"
export PATH
set +e
SALIDA="$("$RESTAURAR" "$DESTINO_VALIDO" "$BUENO_NUEVO" 2>&1)"
CODIGO=$?
set -e
PATH="$PATH_ANTERIOR"
export PATH
[ -f "$FALLO_MARCADO" ] || fallo "no se ejecutó el fallo de consulta posterior simulado"
[ "$CODIGO" -ne 0 ] || fallo "el restaurador anunció éxito ante una consulta posterior fallida"
case "$SALIDA" in
  *"restauracion completada"*) fallo "el restaurador anunció éxito pese al fallo posterior" ;;
esac
case "$SALIDA" in
  *"estado anterior recuperado"*) ;;
  *) fallo "no se recuperó el destino anterior tras el fallo posterior: $SALIDA" ;;
esac
[ "$(sqlite3 "$DESTINO_VALIDO" 'SELECT codigo FROM reservations;')" = "H-SINTETICA" ] \
  || fallo "la recuperación no dejó la reserva previa intacta"
echo "fallo posterior inyectado; salida sin éxito y reserva previa recuperada"
echo
echo "=== 4. El ciclo existente de respaldo y restauración sigue funcionando ==="
CICLO_DB="$TEMP_DIR/ciclo.sqlite3"
CICLO_BACKUPS="$TEMP_DIR/backups"
mkdir -p "$CICLO_BACKUPS"
sqlite3 "$CICLO_DB" <<'SQL'
CREATE TABLE flyway_schema_history (version TEXT);
INSERT INTO flyway_schema_history VALUES ('1');
CREATE TABLE users (id INTEGER PRIMARY KEY);
CREATE TABLE reservations (codigo TEXT PRIMARY KEY);
CREATE TABLE rooms (id INTEGER PRIMARY KEY);
CREATE TABLE room_types (id INTEGER PRIMARY KEY);
CREATE TABLE rates (id INTEGER PRIMARY KEY);
CREATE TABLE reservation_items (id INTEGER PRIMARY KEY);
CREATE TABLE audit_log (id INTEGER PRIMARY KEY);
INSERT INTO reservations VALUES ('H-CICLO-SINTETICO');
SQL
/usr/local/bin/probar-respaldo.sh "$CICLO_DB" "$CICLO_BACKUPS"

echo "PRUEBA DE RESTAURACION SEGURA VERIFICADA"
