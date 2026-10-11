#!/bin/sh
# Prueba aislada de la restauración. Solo crea bases sintéticas bajo un directorio temporal.
set -eu

RESTAURAR="${RESTAURAR_BIN:-/usr/local/bin/restaurar.sh}"
TEMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/probar-restaurar-segura-XXXXXX")"
HOLDER_PID=""
chown 100:101 "$TEMP_DIR"

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

crear_base_hotel() {
  archivo="$1"
  codigo="$2"
  version="${3:-21}"
  if [ "$version" -ge 21 ]; then
    columna_categoria=", categoria TEXT"
    migracion="V21__categoria_lugares.sql"
    descripcion="categoria lugares"
  else
    columna_categoria=""
    migracion="V20__condiciones_acordadas.sql"
    descripcion="condiciones acordadas"
  fi
  sqlite3 "$archivo" <<SQL
CREATE TABLE flyway_schema_history (
  installed_rank INTEGER NOT NULL,
  version TEXT,
  description TEXT NOT NULL,
  type TEXT NOT NULL,
  script TEXT NOT NULL,
  checksum INTEGER,
  installed_by TEXT NOT NULL,
  installed_on TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  execution_time INTEGER NOT NULL,
  success INTEGER NOT NULL
);
INSERT INTO flyway_schema_history VALUES ($version, '$version', '$descripcion', 'SQL', '$migracion', 0, 'hotel', CURRENT_TIMESTAMP, 1, 1);
CREATE TABLE users (
  id INTEGER, email TEXT, hash TEXT, rol TEXT, activo INTEGER, creado_en TEXT,
  debe_cambiar_clave INTEGER, nombre TEXT
);
CREATE TABLE reservations (
  id INTEGER, codigo TEXT, email TEXT, nombre TEXT, llegada TEXT, salida TEXT,
  huespedes INTEGER, estado TEXT, origen TEXT, idempotencia TEXT, creado_en TEXT,
  usuario_id INTEGER, total_cents INTEGER, moneda TEXT, rate_plan_id INTEGER,
  hora_entrada TEXT, hora_salida TEXT, politica_cancelacion TEXT
);
CREATE TABLE hotel_config (clave TEXT, valor TEXT, actualizado_en TEXT);
CREATE TABLE reservation_items (id INTEGER, reservation_id INTEGER, room_id INTEGER, desde TEXT, hasta TEXT);
CREATE TABLE reservation_history (
  id INTEGER, reservation_id INTEGER, estado_ant TEXT, estado_nuevo TEXT,
  detalle TEXT, actor TEXT, en TEXT
);
CREATE TABLE blocks (room_id INTEGER, desde TEXT, hasta TEXT, motivo TEXT);
CREATE TABLE rooms (id INTEGER, codigo TEXT, room_type_id INTEGER, estado TEXT, nombre TEXT);
CREATE TABLE room_types (id INTEGER, codigo TEXT, nombre TEXT, capacidad_max INTEGER);
CREATE TABLE rate_plans (id INTEGER, codigo TEXT, nombre TEXT, moneda TEXT, activo INTEGER, descuento_pct INTEGER);
CREATE TABLE rates (
  id INTEGER, rate_plan_id INTEGER, room_type_id INTEGER, fecha TEXT, precio_cents INTEGER,
  min_estancia INTEGER, max_estancia INTEGER, cerrado INTEGER
);
CREATE TABLE channels (codigo TEXT, nombre TEXT, activo INTEGER);
CREATE TABLE channel_mappings (channel_codigo TEXT, external_id TEXT);
CREATE TABLE ota_syncs (channel_codigo TEXT, operacion TEXT, exitosa INTEGER, en TEXT);
CREATE TABLE indicator_definitions (
  clave TEXT, fase INTEGER, nombre TEXT, definicion TEXT, formula TEXT, fuente TEXT,
  unidad TEXT, periodo_por_defecto TEXT, activa INTEGER
);
CREATE TABLE indicator_results (clave TEXT, periodo TEXT, generado_en TEXT);
CREATE TABLE adoption_activities (tipo TEXT, descripcion TEXT, fecha TEXT, con_datos_de_origen INTEGER);
CREATE TABLE admin_actions (actor TEXT, metodo TEXT, ruta TEXT, estado INTEGER, en TEXT);
CREATE TABLE usuarios (google_sub TEXT, email TEXT, nombre TEXT, creado_en TEXT);
CREATE TABLE pagos (
  id INTEGER, reservation_id INTEGER, monto_cents INTEGER, moneda TEXT, concepto TEXT,
  actor TEXT, creado_en TEXT, anulado_en TEXT, anulado_por TEXT
);
CREATE TABLE amenidades (id INTEGER, codigo TEXT, nombre TEXT);
CREATE TABLE room_type_amenidades (room_type_id INTEGER, amenidad_id INTEGER);
CREATE TABLE lugares_interes (
  id INTEGER, nombre TEXT, descripcion TEXT, latitud REAL, longitud REAL,
  activo INTEGER, creado_en TEXT$columna_categoria
);
CREATE TABLE mensajes (id INTEGER, reservation_id INTEGER, autor TEXT, texto TEXT, creado_en TEXT, visto INTEGER);
INSERT INTO reservations (codigo) VALUES ('$codigo');
SQL
}

echo "=== 1. Una restauración válida conserva su esquema y sus reservas ==="
BUENO="$TEMP_DIR/hotel-valido.sqlite3"
DESTINO_VALIDO="$TEMP_DIR/destino-valido.sqlite3"
crear_base_hotel "$BUENO" "H-SINTETICA"
sqlite3 "$DESTINO_VALIDO" "CREATE TABLE anterior (valor TEXT); INSERT INTO anterior VALUES ('reemplazable');"
"$RESTAURAR" "$DESTINO_VALIDO" "$BUENO"
[ "$(sqlite3 "$DESTINO_VALIDO" 'SELECT COUNT(*) FROM reservations;')" = "1" ] \
  || fallo "la restauración válida no conservó la reserva sintética"
[ "$(sqlite3 "$DESTINO_VALIDO" "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='flyway_schema_history';")" = "1" ] \
  || fallo "la restauración válida no conservó el historial Flyway"
[ "$(stat -c '%u:%g' "$DESTINO_VALIDO")" = "100:101" ] \
  || fallo "la base restaurada no pertenece al usuario de la API (hotel:100:101)"
[ "$(stat -c '%a' "$DESTINO_VALIDO")" = "660" ] \
  || fallo "la base restaurada no tiene permisos de lectura y escritura para hotel"
HOTEL_DB_PRUEBA="$DESTINO_VALIDO" su -p -s /bin/sh -c \
  'sqlite3 "$HOTEL_DB_PRUEBA" "PRAGMA journal_mode=WAL; CREATE TABLE __hotel_restore_probe (id INTEGER); DROP TABLE __hotel_restore_probe;" >/dev/null' hotel \
  || fallo "el usuario de la API no puede abrir la base en WAL y escribir en su directorio"
[ "$(sqlite3 "$DESTINO_VALIDO" "SELECT COUNT(*) FROM sqlite_master WHERE name='__hotel_restore_probe';")" = "0" ] \
  || fallo "la comprobación de escritura dejó objetos temporales en la base restaurada"
echo "archivo restaurado como 100:101, modo 660; la API abrió WAL y pudo escribir"

echo
echo "=== 2. Un directorio no escribible por la API se rechaza antes de sustituir la base ==="
DIR_SOLO_ROOT="$TEMP_DIR/solo-root"
mkdir -p "$DIR_SOLO_ROOT"
chmod 700 "$DIR_SOLO_ROOT"
DESTINO_SOLO_ROOT="$DIR_SOLO_ROOT/hotel.sqlite3"
crear_base_hotel "$DESTINO_SOLO_ROOT" "H-DIRECTORIO-ORIGINAL"
set +e
SALIDA="$("$RESTAURAR" "$DESTINO_SOLO_ROOT" "$BUENO" 2>&1)"
CODIGO=$?
set -e
[ "$CODIGO" -ne 0 ] || fallo "el restaurador aceptó un directorio inaccesible para hotel"
case "$SALIDA" in
  *"restauracion completada"*) fallo "el restaurador anunció éxito con un directorio inaccesible" ;;
esac
[ "$(stat -c '%u:%g' "$DESTINO_SOLO_ROOT")" = "0:0" ] \
  || fallo "se sustituyó o cambió el propietario de la base aunque la API no podía usar el directorio"
[ "$(sqlite3 "$DESTINO_SOLO_ROOT" 'SELECT codigo FROM reservations;')" = "H-DIRECTORIO-ORIGINAL" ] \
  || fallo "se alteraron datos en un destino cuyo directorio la API no puede usar"
echo "directorio sin escritura para hotel rechazado; base previa intacta"

echo
echo "=== 3. Un historial y unas tablas parciales no pasan por base del hotel ==="
PARCIAL="$TEMP_DIR/esquema-parcial.sqlite3"
DESTINO_PARCIAL="$TEMP_DIR/destino-parcial.sqlite3"
sqlite3 "$PARCIAL" <<'SQL'
CREATE TABLE flyway_schema_history (version TEXT);
INSERT INTO flyway_schema_history VALUES ('texto');
CREATE TABLE users (id INTEGER);
CREATE TABLE reservations (id INTEGER);
CREATE TABLE rooms (id INTEGER);
CREATE TABLE room_types (id INTEGER);
CREATE TABLE rates (id INTEGER);
SQL
sqlite3 "$DESTINO_PARCIAL" "CREATE TABLE sentinel (valor TEXT); INSERT INTO sentinel VALUES ('intacto');"
PARCIAL_ANTES="$(huella "$DESTINO_PARCIAL")"
set +e
SALIDA="$("$RESTAURAR" "$DESTINO_PARCIAL" "$PARCIAL" 2>&1)"
CODIGO=$?
set -e
[ "$CODIGO" -ne 0 ] || fallo "el restaurador aceptó un historial y unas tablas parciales: $SALIDA"
case "$SALIDA" in
  *"restauracion completada"*) fallo "el restaurador anunció éxito para el esquema parcial" ;;
esac
[ "$(huella "$DESTINO_PARCIAL")" = "$PARCIAL_ANTES" ] \
  || fallo "el destino cambió ante el esquema parcial"
echo "historial Flyway inválido y esquema parcial rechazados; destino intacto"

echo
echo "=== 4. Una migración Flyway fallida se rechaza aunque haya versiones anteriores ==="
FLYWAY_FALLIDO="$TEMP_DIR/hotel-con-migracion-fallida.sqlite3"
DESTINO_FLYWAY="$TEMP_DIR/destino-flyway.sqlite3"
crear_base_hotel "$FLYWAY_FALLIDO" "H-NO-DEBE-INSTALAR"
sqlite3 "$FLYWAY_FALLIDO" "INSERT INTO flyway_schema_history VALUES (22, '22', 'migracion fallida', 'SQL', 'V22__fallida.sql', 0, 'hotel', CURRENT_TIMESTAMP, 1, 0);"
sqlite3 "$DESTINO_FLYWAY" "CREATE TABLE sentinel (valor TEXT); INSERT INTO sentinel VALUES ('intacto');"
FLYWAY_ANTES="$(huella "$DESTINO_FLYWAY")"
set +e
SALIDA="$("$RESTAURAR" "$DESTINO_FLYWAY" "$FLYWAY_FALLIDO" 2>&1)"
CODIGO=$?
set -e
[ "$CODIGO" -ne 0 ] || fallo "el restaurador aceptó una base con migración Flyway fallida: $SALIDA"
[ "$(huella "$DESTINO_FLYWAY")" = "$FLYWAY_ANTES" ] \
  || fallo "el destino cambió ante una migración Flyway fallida"
echo "migración fallida rechazada; el destino permaneció intacto"

echo
echo "=== 5. Una base íntegra sin esquema se rechaza antes de tocar destino o WAL ==="
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
echo "=== 6. Un fallo posterior no informa éxito y recupera el destino previo ==="
BUENO_NUEVO="$TEMP_DIR/hotel-nuevo.sqlite3"
crear_base_hotel "$BUENO_NUEVO" "H-NUEVA"
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
echo "=== 7. Un fallo parcial al limpiar WAL/SHM recupera el estado anterior ==="
RM_REAL="$(command -v rm)"
FALLO_RM_BIN="$TEMP_DIR/fallo-rm-bin"
FALLO_RM_MARCADO="$TEMP_DIR/fallo-rm-inyectado"
mkdir -p "$FALLO_RM_BIN"
cat > "$FALLO_RM_BIN/rm" <<'SH'
#!/bin/sh
if [ "$1" = "-f" ] \
  && [ "$2" = "$DESTINO_RM-wal" ] \
  && [ ! -e "$MARCADOR_RM" ]; then
  : > "$2"
  : > "$3"
  "$RM_REAL" "$1" "$2"
  : > "$MARCADOR_RM"
  echo "fallo parcial de limpieza simulado" >&2
  exit 1
fi
exec "$RM_REAL" "$@"
SH
chmod +x "$FALLO_RM_BIN/rm"
PATH_ANTERIOR="$PATH"
export DESTINO_RM="$DESTINO_VALIDO" MARCADOR_RM="$FALLO_RM_MARCADO" RM_REAL
PATH="$FALLO_RM_BIN:$PATH"
export PATH
set +e
SALIDA="$("$RESTAURAR" "$DESTINO_VALIDO" "$BUENO_NUEVO" 2>&1)"
CODIGO=$?
set -e
PATH="$PATH_ANTERIOR"
export PATH
[ -f "$FALLO_RM_MARCADO" ] || fallo "no se ejecutó el fallo parcial de limpieza simulado: $SALIDA"
[ "$CODIGO" -ne 0 ] || fallo "el restaurador anunció éxito ante el fallo de limpieza"
case "$SALIDA" in
  *"estado anterior recuperado"*) ;;
  *) fallo "no recuperó el estado previo tras limpiar solo uno de los sidecars: $SALIDA" ;;
esac
[ "$(sqlite3 "$DESTINO_VALIDO" 'SELECT codigo FROM reservations;')" = "H-SINTETICA" ] \
  || fallo "el estado previo no quedó recuperado tras el fallo de limpieza"
[ ! -e "$DESTINO_VALIDO-wal" ] && [ ! -e "$DESTINO_VALIDO-shm" ] \
  || fallo "quedaron sidecars parciales junto a la base recuperada"
echo "fallo parcial inyectado; la restauración terminó con error y recuperó la reserva previa"

echo
echo "=== 8. El ciclo existente de respaldo y restauración sigue funcionando ==="
CICLO_DB="$TEMP_DIR/ciclo.sqlite3"
CICLO_BACKUPS="$TEMP_DIR/backups"
mkdir -p "$CICLO_BACKUPS"
crear_base_hotel "$CICLO_DB" "H-CICLO-SINTETICO"
sqlite3 "$CICLO_DB" "CREATE TABLE audit_log (id INTEGER PRIMARY KEY);"
/usr/local/bin/probar-respaldo.sh "$CICLO_DB" "$CICLO_BACKUPS"

echo
echo "=== 9. Un historial Flyway sin checksum se rechaza sin tocar el destino ==="
SIN_CHECKSUM="$TEMP_DIR/hotel-sin-checksum.sqlite3"
DESTINO_SIN_CHECKSUM="$TEMP_DIR/destino-sin-checksum.sqlite3"
crear_base_hotel "$SIN_CHECKSUM" "H-SIN-CHECKSUM"
sqlite3 "$SIN_CHECKSUM" 'ALTER TABLE flyway_schema_history DROP COLUMN checksum;'
sqlite3 "$DESTINO_SIN_CHECKSUM" "CREATE TABLE sentinel (valor TEXT); INSERT INTO sentinel VALUES ('intacto');"
SIN_CHECKSUM_ANTES="$(huella "$DESTINO_SIN_CHECKSUM")"
set +e
SALIDA="$("$RESTAURAR" "$DESTINO_SIN_CHECKSUM" "$SIN_CHECKSUM" 2>&1)"
CODIGO=$?
set -e
[ "$CODIGO" -ne 0 ] || fallo "el restaurador aceptó un historial Flyway sin checksum: $SALIDA"
case "$SALIDA" in
  *"restauracion completada"*) fallo "el restaurador anunció éxito con historial Flyway incompleto" ;;
esac
[ "$(huella "$DESTINO_SIN_CHECKSUM")" = "$SIN_CHECKSUM_ANTES" ] \
  || fallo "el destino cambió ante un historial Flyway sin checksum"
echo "historial sin checksum rechazado; destino intacto"

echo
echo "=== 10. Un respaldo válido v20 se conserva para migrarlo al reiniciar ==="
BUENO_V20="$TEMP_DIR/hotel-valido-v20.sqlite3"
DESTINO_V20="$TEMP_DIR/destino-v20.sqlite3"
crear_base_hotel "$BUENO_V20" "H-ANTERIOR" 20
sqlite3 "$DESTINO_V20" "CREATE TABLE anterior (valor TEXT); INSERT INTO anterior VALUES ('reemplazable');"
"$RESTAURAR" "$DESTINO_V20" "$BUENO_V20"
[ "$(sqlite3 "$DESTINO_V20" "SELECT COUNT(*) FROM pragma_table_info('lugares_interes') WHERE name='categoria';")" = "0" ] \
  || fallo "la restauración de v20 fabricó la columna introducida en v21"
[ "$(sqlite3 "$DESTINO_V20" 'SELECT MAX(CAST(version AS INTEGER)) FROM flyway_schema_history;')" = "20" ] \
  || fallo "la restauración v20 no conservó su versión Flyway para migración posterior"
echo "respaldo v20 restaurado sin inventar el esquema de v21"

echo
echo "=== 11. Columnas añadidas por migraciones posteriores son obligatorias ==="
for definicion in \
  rate_plans:descuento_pct \
  users:debe_cambiar_clave \
  reservation_history:detalle \
  users:nombre \
  reservation_items:id \
  reservation_history:id \
  reservation_history:estado_ant \
  pagos:id \
  pagos:concepto \
  pagos:anulado_en \
  pagos:anulado_por \
  mensajes:id; do
  tabla="${definicion%%:*}"
  columna="${definicion#*:}"
  BASE_INCOMPLETA="$TEMP_DIR/hotel-sin-$tabla-$columna.sqlite3"
  DESTINO_INCOMPLETO="$TEMP_DIR/destino-sin-$tabla-$columna.sqlite3"
  crear_base_hotel "$BASE_INCOMPLETA" "H-SIN-$tabla-$columna"
  sqlite3 "$BASE_INCOMPLETA" "ALTER TABLE $tabla DROP COLUMN $columna;"
  sqlite3 "$DESTINO_INCOMPLETO" "CREATE TABLE sentinel (valor TEXT); INSERT INTO sentinel VALUES ('intacto');"
  ANTES="$(huella "$DESTINO_INCOMPLETO")"
  set +e
  SALIDA="$("$RESTAURAR" "$DESTINO_INCOMPLETO" "$BASE_INCOMPLETA" 2>&1)"
  CODIGO=$?
  set -e
  [ "$CODIGO" -ne 0 ] || fallo "se aceptó un v21 sin $tabla.$columna: $SALIDA"
  [ "$(huella "$DESTINO_INCOMPLETO")" = "$ANTES" ] \
    || fallo "el destino cambió ante la ausencia de $tabla.$columna"
done
echo "se rechazaron 12 respaldos v21 que omitían columnas de V9, V10, V11, V16 o V19; destinos intactos"

echo
echo "=== 12. Una interrupción después de instalar la candidata recupera la base previa ==="
MV_REAL="$(command -v mv)"
FALLO_MV_BIN="$TEMP_DIR/fallo-mv-bin"
FALLO_MV_MARCADO="$TEMP_DIR/fallo-mv-inyectado"
FALLO_MV_SALIDA="$TEMP_DIR/senal-restauracion.log"
mkdir -p "$FALLO_MV_BIN"
cat > "$FALLO_MV_BIN/mv" <<'SH'
#!/bin/sh
"$MV_REAL" "$@" || exit $?
if [ "$2" = "$DESTINO_SENAL" ] && [ ! -e "$MARCADOR_SENAL" ]; then
  : > "$MARCADOR_SENAL"
  kill -TERM "$PPID"
fi
SH
chmod +x "$FALLO_MV_BIN/mv"
PATH_ANTERIOR="$PATH"
export DESTINO_SENAL="$DESTINO_VALIDO" MARCADOR_SENAL="$FALLO_MV_MARCADO" MV_REAL
PATH="$FALLO_MV_BIN:$PATH"
export PATH
"$RESTAURAR" "$DESTINO_VALIDO" "$BUENO_NUEVO" >"$FALLO_MV_SALIDA" 2>&1 &
PID_RESTAURACION=$!
for intento in $(seq 1 50); do
  [ -e "$FALLO_MV_MARCADO" ] && break
  sleep 0.1
done
PATH="$PATH_ANTERIOR"
export PATH
[ -f "$FALLO_MV_MARCADO" ] || {
  kill -TERM "$PID_RESTAURACION" 2>/dev/null || true
  wait "$PID_RESTAURACION" 2>/dev/null || true
  fallo "no se alcanzó la sustitución atómica para inyectar SIGTERM"
}
set +e
wait "$PID_RESTAURACION"
CODIGO=$?
set -e
[ "$CODIGO" -ne 0 ] || fallo "el restaurador terminó como éxito después de SIGTERM"
case "$(cat "$FALLO_MV_SALIDA")" in
  *"estado anterior recuperado"*) ;;
  *) fallo "SIGTERM no activó la recuperación: $(cat "$FALLO_MV_SALIDA")" ;;
esac
[ "$(sqlite3 "$DESTINO_VALIDO" 'SELECT codigo FROM reservations;')" = "H-SINTETICA" ] \
  || fallo "la interrupción dejó instalada la candidata en lugar de la base previa"
echo "SIGTERM posterior a la instalación recuperó la reserva previa y no anunció éxito"

echo "PRUEBA DE RESTAURACION SEGURA VERIFICADA"
