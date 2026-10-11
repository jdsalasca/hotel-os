#!/bin/sh
# Restauración desde un respaldo. Se ejecuta con la aplicación DETENIDA.
#
# Por qué detenida: restaurar sobre un archivo que tiene una escritura en curso deja el WAL
# inconsistente. El orden correcto es: parar -> sustituir -> arrancar -> verificar.
#
# Uso:  ./restaurar.sh /data/hotel.sqlite3 /backups/hotel-20261006T120000Z.sqlite3
set -eu

DESTINO="${1:-/data/hotel.sqlite3}"
RESPALDO="${2:?falta la ruta del respaldo a restaurar}"
FLYWAY_CHECKSUMS_DB="${FLYWAY_CHECKSUMS_DB:-/usr/local/share/hotel/flyway-checksums.sqlite3}"
TEMPORAL_DIR=""
COPIA=""
HOTEL_UID=""
HOTEL_GID=""
FASE_DESTRUCTIVA="0"
RECUPERACION_EN_CURSO="0"

limpiar_temporales() {
  if [ -n "$TEMPORAL_DIR" ] && [ -d "$TEMPORAL_DIR" ]; then
    rm -rf "$TEMPORAL_DIR"
  fi
}
trap limpiar_temporales 0

consulta_sqlite() {
  consulta_archivo="$1"
  consulta_sql="$2"
  if consulta_salida="$(sqlite3 "$consulta_archivo" "$consulta_sql" 2>&1)"; then
    printf '%s' "$consulta_salida"
  else
    consulta_codigo=$?
    echo "ERROR: no se pudo consultar $consulta_archivo ($consulta_codigo): $consulta_salida" >&2
    return 1
  fi
}

ejecutar_como_hotel() {
  variable_api="$1"
  valor_api="$2"
  comando_api="$3"
  if [ "$(id -u)" = "$HOTEL_UID" ]; then
    env "$variable_api=$valor_api" sh -c "$comando_api"
  else
    env "$variable_api=$valor_api" su -p -s /bin/sh -c "$comando_api" hotel
  fi
}

validar_escritura_api() {
  validar_archivo_api="$1"
  if ! ejecutar_como_hotel HOTEL_DB_API "$validar_archivo_api" \
    'sqlite3 "$HOTEL_DB_API" "PRAGMA journal_mode=WAL; BEGIN IMMEDIATE; CREATE TABLE __hotel_restore_probe (id INTEGER); ROLLBACK;" >/dev/null'; then
    echo "ERROR: el usuario hotel ($HOTEL_UID:$HOTEL_GID) no puede abrir y escribir $validar_archivo_api en WAL" >&2
    return 1
  fi
}

validar_directorio_api() {
  validar_directorio="$1"
  if ! ejecutar_como_hotel HOTEL_DIR_API "$validar_directorio" \
    'prueba="$(mktemp "$HOTEL_DIR_API/.restaurar-permisos-XXXXXX")" && rm -f "$prueba"'; then
    echo "ERROR: el directorio $validar_directorio no permite crear WAL/SHM al usuario hotel ($HOTEL_UID:$HOTEL_GID). No se toca el destino." >&2
    return 1
  fi
}

exigir_indice() {
  tabla_requerida="$1"
  indice_requerido="$2"
  columnas_requeridas="$3"
  BASE_HAY_INDICE="$(consulta_sqlite "$validar_archivo" "SELECT COUNT(*) FROM pragma_index_list('$tabla_requerida') WHERE name = '$indice_requerido' AND partial = 0 AND \"unique\" = 0;")" || return 1
  if [ "$BASE_HAY_INDICE" != "1" ]; then
    echo "ERROR: $validar_archivo no conserva un índice completo para $tabla_requerida ($indice_requerido)" >&2
    return 1
  fi
  BASE_COLUMNAS_INDICE="$(consulta_sqlite "$validar_archivo" "SELECT group_concat(name, ',') FROM (SELECT name FROM pragma_index_info('$indice_requerido') ORDER BY seqno);")" || return 1
  if [ "$BASE_COLUMNAS_INDICE" != "$columnas_requeridas" ]; then
    echo "ERROR: $validar_archivo tiene columnas distintas en el índice $indice_requerido: $BASE_COLUMNAS_INDICE" >&2
    return 1
  fi
}

exigir_indice_expresion() {
  tabla_requerida="$1"
  indice_requerido="$2"
  expresion_requerida="$3"
  BASE_HAY_INDICE="$(consulta_sqlite "$validar_archivo" "SELECT COUNT(*) FROM pragma_index_list('$tabla_requerida') WHERE name = '$indice_requerido' AND partial = 0 AND \"unique\" = 0;")" || return 1
  BASE_HAY_EXPRESION="$(consulta_sqlite "$validar_archivo" "SELECT COUNT(*) FROM pragma_index_xinfo('$indice_requerido') WHERE key = 1 AND cid = -2 AND name IS NULL;")" || return 1
  PLAN_INDICE="$(consulta_sqlite "$validar_archivo" "EXPLAIN QUERY PLAN SELECT id FROM $tabla_requerida WHERE $expresion_requerida = '__hotel_restore_probe__';")" || return 1
  case "$PLAN_INDICE" in *"$indice_requerido"*) ;; *) BASE_HAY_EXPRESION="0";; esac
  if [ "$BASE_HAY_INDICE" != "1" ] || [ "$BASE_HAY_EXPRESION" != "1" ]; then
    echo "ERROR: $validar_archivo no conserva el índice de expresión completo $indice_requerido" >&2
    return 1
  fi
}

exigir_indice_unico() {
  tabla_requerida="$1"
  columnas_requeridas="$2"
  BASE_INDICES_UNICOS="$(consulta_sqlite "$validar_archivo" "SELECT name FROM pragma_index_list('$tabla_requerida') WHERE \"unique\" = 1 AND partial = 0;")" || return 1
  for indice_unico in $BASE_INDICES_UNICOS; do
    BASE_COLUMNAS_UNICAS="$(consulta_sqlite "$validar_archivo" "SELECT group_concat(name, ',') FROM (SELECT name FROM pragma_index_info('$indice_unico') ORDER BY seqno);")" || return 1
    if [ "$BASE_COLUMNAS_UNICAS" = "$columnas_requeridas" ]; then
      return 0
    fi
  done
  echo "ERROR: $validar_archivo no conserva unicidad en $tabla_requerida($columnas_requeridas)" >&2
  return 1
}

exigir_clave_primaria() {
  tabla_requerida="$1"
  columna_requerida="$2"
  BASE_TIPO_PK_REQUERIDO="$(printf '%s' "${3:-INTEGER}" | tr '[:lower:]' '[:upper:]')"
  BASE_HAY_PK="$(consulta_sqlite "$validar_archivo" "SELECT COUNT(*) FROM pragma_table_info('$tabla_requerida') WHERE pk = 1 AND name = '$columna_requerida';")" || return 1
  BASE_COLUMNAS_PK="$(consulta_sqlite "$validar_archivo" "SELECT COUNT(*) FROM pragma_table_info('$tabla_requerida') WHERE pk > 0;")" || return 1
  BASE_TIPO_PK="$(consulta_sqlite "$validar_archivo" "SELECT upper(type) FROM pragma_table_info('$tabla_requerida') WHERE pk = 1 AND name = '$columna_requerida';")" || return 1
  if [ "$BASE_HAY_PK" != "1" ] || [ "$BASE_COLUMNAS_PK" != "1" ] || [ "$BASE_TIPO_PK" != "$BASE_TIPO_PK_REQUERIDO" ]; then
    echo "ERROR: $validar_archivo no conserva $BASE_TIPO_PK_REQUERIDO PRIMARY KEY en $tabla_requerida.$columna_requerida" >&2
    return 1
  fi
}

exigir_clave_primaria_compuesta() {
  tabla_requerida="$1"
  columnas_requeridas="$2"
  BASE_COLUMNAS_PK="$(consulta_sqlite "$validar_archivo" "SELECT group_concat(name, ',') FROM (SELECT name FROM pragma_table_info('$tabla_requerida') WHERE pk > 0 ORDER BY pk);")" || return 1
  if [ "$BASE_COLUMNAS_PK" != "$columnas_requeridas" ]; then
    echo "ERROR: $validar_archivo no conserva la clave primaria compuesta ($columnas_requeridas) en $tabla_requerida" >&2
    return 1
  fi
}

exigir_autoincrement() {
  tabla_requerida="$1"
  insercion_inicial="$2"
  insercion_siguiente="$3"
  if salida_autoincremento="$(sqlite3 -bail "$validar_archivo" "
    PRAGMA foreign_keys=ON;
    BEGIN;
    CREATE TEMP TABLE __hotel_restore_autoincrement_probe (id INTEGER NOT NULL);
    $insercion_inicial
    INSERT INTO __hotel_restore_autoincrement_probe(id)
      SELECT id FROM $tabla_requerida WHERE rowid=last_insert_rowid();
    DELETE FROM $tabla_requerida WHERE rowid=(SELECT id FROM __hotel_restore_autoincrement_probe);
    $insercion_siguiente
    SELECT CASE WHEN (SELECT id FROM $tabla_requerida WHERE rowid=last_insert_rowid()) IS NOT NULL
      AND (SELECT id FROM $tabla_requerida WHERE rowid=last_insert_rowid()) >
        (SELECT id FROM __hotel_restore_autoincrement_probe) THEN 1 ELSE 0 END;
    ROLLBACK;
  " 2>&1)"; then
    if [ "$salida_autoincremento" = "1" ]; then
      return 0
    fi
  else
    echo "ERROR: no se pudo comprobar AUTOINCREMENT en $tabla_requerida: $salida_autoincremento" >&2
    return 1
  fi
  echo "ERROR: $validar_archivo no conserva el comportamiento AUTOINCREMENT en $tabla_requerida" >&2
  return 1
}

probar_regla_rechaza() {
  validar_archivo="$1"
  regla="$2"
  sentencia="$3"
  if salida_probe="$(sqlite3 -bail "$validar_archivo" "PRAGMA foreign_keys=ON; BEGIN; $sentencia; ROLLBACK;" 2>&1)"; then
    echo "ERROR: $validar_archivo permite un dato que viola la regla $regla" >&2
    return 1
  fi
  case "$salida_probe" in
    *"CHECK constraint failed"*) return 0;;
    *) echo "ERROR: no se pudo comprobar la regla $regla en $validar_archivo: $salida_probe" >&2; return 1;;
  esac
}

probar_regla_acepta() {
  validar_archivo="$1"
  regla="$2"
  sentencia="$3"
  if ! sqlite3 -bail "$validar_archivo" "PRAGMA foreign_keys=ON; BEGIN; $sentencia; ROLLBACK;" >/dev/null 2>&1; then
    echo "ERROR: $validar_archivo rechaza un dato permitido por la regla $regla" >&2
    return 1
  fi
}

reserva_prueba_sql() {
  estado_prueba="$1"
  origen_prueba="$2"
  huespedes_prueba="${3:-1}"
  llegada_prueba="${4:-2026-10-11}"
  salida_prueba="${5:-2026-10-12}"
  reserva_prueba="${6:-__hotel_restore_probe_$$}"
  printf "INSERT INTO reservations(codigo,email,nombre,llegada,salida,huespedes,estado,origen,idempotencia,creado_en) VALUES ('%s','restore-probe-%s@example.invalid','', '%s','%s',%s,'%s','%s','%s',CURRENT_TIMESTAMP);" \
    "$reserva_prueba" "$$" "$llegada_prueba" "$salida_prueba" "$huespedes_prueba" "$estado_prueba" "$origen_prueba" "$reserva_prueba"
}

habitacion_prueba_sql() {
  codigo_habitacion="__hotel_restore_room_$$_$1"
  if [ "$BASE_VERSION" -ge 2 ]; then
    codigo_tipo="__hotel_restore_type_$$_$1"
    printf "INSERT INTO room_types(codigo,nombre,capacidad_max) VALUES ('%s','Prueba',1); INSERT INTO rooms(codigo,room_type_id,estado,nombre) SELECT '%s',id,'ACTIVA','' FROM room_types WHERE codigo='%s';" \
      "$codigo_tipo" "$codigo_habitacion" "$codigo_tipo"
  else
    printf "INSERT INTO rooms(codigo,estado,nombre) VALUES ('%s','ACTIVA','');" "$codigo_habitacion"
  fi
}

tarifa_prueba_sql() {
  precio_prueba="$1"
  minimo_prueba="$2"
  maximo_prueba="$3"
  codigo_plan="__hotel_restore_plan_$$"
  codigo_tipo="__hotel_restore_type_$$"
  printf "INSERT INTO rate_plans(codigo,nombre,moneda) VALUES ('%s','Prueba','COP'); INSERT INTO room_types(codigo,nombre,capacidad_max) VALUES ('%s','Prueba',1); INSERT INTO rates(rate_plan_id,room_type_id,fecha,precio_cents,min_estancia,max_estancia,cerrado) SELECT p.id,t.id,'2031-01-01',%s,%s,%s,0 FROM rate_plans p,room_types t WHERE p.codigo='%s' AND t.codigo='%s';" \
    "$codigo_plan" "$codigo_tipo" "$precio_prueba" "$minimo_prueba" "$maximo_prueba" "$codigo_plan" "$codigo_tipo"
}

lugar_prueba_sql() {
  nombre_lugar="$1"
  latitud_prueba="$2"
  longitud_prueba="$3"
  printf "INSERT INTO lugares_interes(nombre,latitud,longitud,creado_en) VALUES ('%s',%s,%s,CURRENT_TIMESTAMP);" \
    "$nombre_lugar" "$latitud_prueba" "$longitud_prueba"
}

validar_autoincrementos() {
  codigo_reserva_auto="__hotel_restore_autoincrement_reserva_$$"
  codigo_reserva_siguiente="${codigo_reserva_auto}_siguiente"
  reserva_auto="$(reserva_prueba_sql PENDIENTE WEB 1 2026-10-11 2026-10-12 "$codigo_reserva_auto")"
  reserva_siguiente="$(reserva_prueba_sql PENDIENTE WEB 1 2026-10-11 2026-10-12 "$codigo_reserva_siguiente")"
  exigir_autoincrement reservations "$reserva_auto" "$reserva_siguiente" || return 1

  codigo_habitacion_auto="__hotel_restore_room_$$_autoincrement-item"
  codigo_habitacion_sql="$(habitacion_prueba_sql autoincrement-item)"
  insercion_item_auto="$reserva_auto $codigo_habitacion_sql INSERT INTO reservation_items(reservation_id,room_id,desde,hasta) SELECT id,(SELECT id FROM rooms WHERE codigo='$codigo_habitacion_auto'),'2026-10-11','2026-10-12' FROM reservations WHERE codigo='$codigo_reserva_auto';"
  insercion_item_siguiente="INSERT INTO reservation_items(reservation_id,room_id,desde,hasta) SELECT id,(SELECT id FROM rooms WHERE codigo='$codigo_habitacion_auto'),'2026-10-12','2026-10-13' FROM reservations WHERE codigo='$codigo_reserva_auto';"
  exigir_autoincrement reservation_items "$insercion_item_auto" "$insercion_item_siguiente" || return 1

  insercion_historial_auto="$reserva_auto INSERT INTO reservation_history(reservation_id,estado_ant,estado_nuevo,actor,en) SELECT id,'PENDIENTE','CONFIRMADA','restauracion',CURRENT_TIMESTAMP FROM reservations WHERE codigo='$codigo_reserva_auto';"
  insercion_historial_siguiente="INSERT INTO reservation_history(reservation_id,estado_ant,estado_nuevo,actor,en) SELECT id,'CONFIRMADA','CANCELADA','restauracion',CURRENT_TIMESTAMP FROM reservations WHERE codigo='$codigo_reserva_auto';"
  exigir_autoincrement reservation_history "$insercion_historial_auto" "$insercion_historial_siguiente" || return 1

  if [ "$BASE_VERSION" -ge 12 ]; then
    insercion_pago_auto="$reserva_auto INSERT INTO pagos(reservation_id,monto_cents,moneda,concepto,actor,creado_en) SELECT id,1000,'COP','sonda','restauracion',CURRENT_TIMESTAMP FROM reservations WHERE codigo='$codigo_reserva_auto';"
    insercion_pago_siguiente="INSERT INTO pagos(reservation_id,monto_cents,moneda,concepto,actor,creado_en) SELECT id,2000,'COP','sonda','restauracion',CURRENT_TIMESTAMP FROM reservations WHERE codigo='$codigo_reserva_auto';"
    exigir_autoincrement pagos "$insercion_pago_auto" "$insercion_pago_siguiente" || return 1
  fi

  if [ "$BASE_VERSION" -ge 15 ]; then
    insercion_mensaje_auto="$reserva_auto INSERT INTO mensajes(reservation_id,autor,texto,creado_en,visto) SELECT id,'HUESPED','x',CURRENT_TIMESTAMP,0 FROM reservations WHERE codigo='$codigo_reserva_auto';"
    insercion_mensaje_siguiente="INSERT INTO mensajes(reservation_id,autor,texto,creado_en,visto) SELECT id,'HOTEL','y',CURRENT_TIMESTAMP,0 FROM reservations WHERE codigo='$codigo_reserva_auto';"
    exigir_autoincrement mensajes "$insercion_mensaje_auto" "$insercion_mensaje_siguiente" || return 1
  fi
}

exigir_fk() {
  tabla_hija="$1"
  tabla_padre="$2"
  columna_hija="$3"
  columna_padre="$4"
  accion_borrado="$5"
  BASE_HAY_FK="$(consulta_sqlite "$validar_archivo" "SELECT COUNT(*) FROM pragma_foreign_key_list('$tabla_hija') WHERE \"table\" = '$tabla_padre' AND \"from\" = '$columna_hija' AND \"to\" = '$columna_padre' AND on_delete = '$accion_borrado';")" || return 1
  if [ "$BASE_HAY_FK" != "1" ]; then
    echo "ERROR: $validar_archivo no conserva la clave foránea $tabla_hija.$columna_hija -> $tabla_padre.$columna_padre" >&2
    return 1
  fi
}

exigir_columnas_no_nulas() {
  BASE_COLUMNAS_NO_NULAS_FALTANTES="$(consulta_sqlite "$validar_archivo" "
WITH columnas_obligatorias(version_min, version_max, tabla, columna) AS (
  VALUES
    (1, 21, 'blocks', 'desde'), (1, 21, 'blocks', 'hasta'), (1, 21, 'blocks', 'motivo'),
    (1, 21, 'hotel_config', 'valor'), (1, 21, 'hotel_config', 'actualizado_en'),
    (1, 21, 'reservation_history', 'reservation_id'), (1, 21, 'reservation_history', 'estado_nuevo'),
    (1, 21, 'reservation_history', 'actor'), (1, 21, 'reservation_history', 'en'),
    (1, 21, 'reservation_items', 'reservation_id'), (1, 21, 'reservation_items', 'room_id'),
    (1, 21, 'reservation_items', 'desde'), (1, 21, 'reservation_items', 'hasta'),
    (1, 5, 'reservations', 'nombre'), (1, 21, 'reservations', 'codigo'),
    (1, 21, 'reservations', 'email'), (1, 21, 'reservations', 'llegada'),
    (1, 21, 'reservations', 'salida'), (1, 21, 'reservations', 'huespedes'),
    (1, 21, 'reservations', 'estado'), (1, 21, 'reservations', 'origen'),
    (1, 21, 'reservations', 'idempotencia'), (1, 21, 'reservations', 'creado_en'),
    (1, 21, 'room_types', 'codigo'), (1, 21, 'room_types', 'nombre'),
    (1, 21, 'room_types', 'capacidad_max'), (1, 21, 'rooms', 'codigo'),
    (1, 21, 'rooms', 'estado'), (1, 21, 'rooms', 'nombre'),
    (1, 21, 'users', 'email'), (1, 21, 'users', 'hash'), (1, 21, 'users', 'rol'),
    (1, 21, 'users', 'activo'), (1, 21, 'users', 'creado_en'),
    (2, 21, 'channel_mappings', 'channel_codigo'), (2, 21, 'channel_mappings', 'external_id'),
    (2, 21, 'channels', 'nombre'), (2, 21, 'channels', 'activo'),
    (2, 21, 'ota_syncs', 'channel_codigo'), (2, 21, 'ota_syncs', 'operacion'),
    (2, 21, 'ota_syncs', 'exitosa'), (2, 21, 'ota_syncs', 'en'),
    (2, 21, 'rate_plans', 'codigo'), (2, 21, 'rate_plans', 'nombre'),
    (2, 21, 'rate_plans', 'moneda'), (2, 21, 'rate_plans', 'activo'),
    (2, 21, 'rates', 'rate_plan_id'), (2, 21, 'rates', 'room_type_id'),
    (2, 21, 'rates', 'fecha'), (2, 21, 'rates', 'precio_cents'), (2, 21, 'rates', 'cerrado'),
    (3, 21, 'adoption_activities', 'tipo'), (3, 21, 'adoption_activities', 'descripcion'),
    (3, 21, 'adoption_activities', 'fecha'), (3, 21, 'adoption_activities', 'con_datos_de_origen'),
    (3, 21, 'indicator_definitions', 'fase'), (3, 21, 'indicator_definitions', 'nombre'),
    (3, 21, 'indicator_definitions', 'definicion'), (3, 21, 'indicator_definitions', 'formula'),
    (3, 21, 'indicator_definitions', 'fuente'), (3, 21, 'indicator_definitions', 'unidad'),
    (3, 21, 'indicator_definitions', 'periodo_por_defecto'), (3, 21, 'indicator_definitions', 'activa'),
    (3, 21, 'indicator_results', 'clave'), (3, 21, 'indicator_results', 'periodo'),
    (3, 21, 'indicator_results', 'generado_en'),
    (5, 21, 'admin_actions', 'actor'), (5, 21, 'admin_actions', 'metodo'),
    (5, 21, 'admin_actions', 'ruta'), (5, 21, 'admin_actions', 'estado'), (5, 21, 'admin_actions', 'en'),
    (7, 21, 'usuarios', 'google_sub'), (7, 21, 'usuarios', 'email'),
    (7, 21, 'usuarios', 'nombre'), (7, 21, 'usuarios', 'creado_en'),
    (9, 21, 'rate_plans', 'descuento_pct'), (10, 21, 'users', 'debe_cambiar_clave'),
    (12, 21, 'pagos', 'reservation_id'), (12, 21, 'pagos', 'monto_cents'),
    (12, 21, 'pagos', 'moneda'), (12, 21, 'pagos', 'concepto'),
    (12, 21, 'pagos', 'actor'), (12, 21, 'pagos', 'creado_en'),
    (13, 21, 'amenidades', 'codigo'), (13, 21, 'amenidades', 'nombre'),
    (13, 21, 'room_type_amenidades', 'room_type_id'), (13, 21, 'room_type_amenidades', 'amenidad_id'),
    (14, 21, 'lugares_interes', 'nombre'), (14, 21, 'lugares_interes', 'descripcion'),
    (14, 21, 'lugares_interes', 'latitud'), (14, 21, 'lugares_interes', 'longitud'),
    (14, 21, 'lugares_interes', 'activo'), (14, 21, 'lugares_interes', 'creado_en'),
    (15, 21, 'mensajes', 'reservation_id'), (15, 21, 'mensajes', 'autor'),
    (15, 21, 'mensajes', 'texto'), (15, 21, 'mensajes', 'creado_en'), (15, 21, 'mensajes', 'visto'),
    (16, 21, 'users', 'nombre'), (21, 21, 'lugares_interes', 'categoria')
)
SELECT COALESCE(group_concat(o.tabla || '.' || o.columna, ', '), '')
FROM columnas_obligatorias o
WHERE $BASE_VERSION BETWEEN o.version_min AND o.version_max
  AND NOT EXISTS (
    SELECT 1 FROM pragma_table_info(o.tabla) p
    WHERE p.name = o.columna AND p.\"notnull\" = 1
  );")" || return 1
  if [ -n "$BASE_COLUMNAS_NO_NULAS_FALTANTES" ]; then
    echo "ERROR: $validar_archivo (v$BASE_VERSION) no conserva NOT NULL en: $BASE_COLUMNAS_NO_NULAS_FALTANTES" >&2
    return 1
  fi
}

validar_reglas_semanticas() {
  sufijo_prueba="$$"
  estados_permitidos="PENDIENTE CONFIRMADA CANCELADA"
  origenes_permitidos="WEB BOOKING DESPEGAR AIRBNB"
  if [ "$BASE_VERSION" -le 5 ] || [ "$BASE_VERSION" -ge 8 ]; then
    estados_permitidos="$estados_permitidos RECHAZADA"
  fi
  if [ "$BASE_VERSION" -ge 19 ]; then
    estados_permitidos="$estados_permitidos NO_PRESENTADA"
  fi
  if [ "$BASE_VERSION" -le 5 ] || [ "$BASE_VERSION" -ge 18 ]; then
    origenes_permitidos="$origenes_permitidos OTRO"
  fi

  for estado_valido in $estados_permitidos; do
    prueba="$(reserva_prueba_sql "$estado_valido" WEB)"
    probar_regla_acepta "$validar_archivo" "estado permitido de reserva ($estado_valido)" "$prueba" || return 1
  done
  for estado_invalido in __INVALIDO__ INVALIDA ''; do
    prueba="$(reserva_prueba_sql "$estado_invalido" WEB)"
    probar_regla_rechaza "$validar_archivo" "estado de reserva ($estado_invalido)" "$prueba" || return 1
  done
  for origen_valido in $origenes_permitidos; do
    prueba="$(reserva_prueba_sql PENDIENTE "$origen_valido")"
    probar_regla_acepta "$validar_archivo" "origen permitido de reserva ($origen_valido)" "$prueba" || return 1
  done
  for origen_invalido in __INVALIDO__ DESCONOCIDO ''; do
    prueba="$(reserva_prueba_sql PENDIENTE "$origen_invalido")"
    probar_regla_rechaza "$validar_archivo" "origen de reserva ($origen_invalido)" "$prueba" || return 1
  done

  if [ "$BASE_VERSION" -le 5 ]; then
    prueba="$(reserva_prueba_sql PENDIENTE WEB 0)"
    probar_regla_rechaza "$validar_archivo" "cantidad positiva de huéspedes" "$prueba" || return 1
    prueba="$(reserva_prueba_sql PENDIENTE WEB 4)"
    probar_regla_acepta "$validar_archivo" "varias personas en la reserva" "$prueba" || return 1
  fi

  for rol_valido in ADMIN STAFF; do
    prueba="INSERT INTO users(email,hash,rol,activo,creado_en) VALUES ('restore-probe-$rol_valido-$$@example.invalid','hash','$rol_valido',1,CURRENT_TIMESTAMP);"
    probar_regla_acepta "$validar_archivo" "rol de usuario permitido ($rol_valido)" "$prueba" || return 1
  done
  for rol_invalido in __INVALIDO__ INVITADO ''; do
    prueba="INSERT INTO users(email,hash,rol,activo,creado_en) VALUES ('restore-probe-invalid-$$@example.invalid','hash','$rol_invalido',1,CURRENT_TIMESTAMP);"
    probar_regla_rechaza "$validar_archivo" "rol de usuario ($rol_invalido)" "$prueba" || return 1
  done

  probar_regla_acepta "$validar_archivo" "capacidad positiva de tipo de habitación" \
    "INSERT INTO room_types(codigo,nombre,capacidad_max) VALUES ('restore-probe-cap-$$','Prueba',1);" || return 1
  probar_regla_acepta "$validar_archivo" "capacidad plural de tipo de habitación" \
    "INSERT INTO room_types(codigo,nombre,capacidad_max) VALUES ('restore-probe-cap-plural-$$','Prueba',4);" || return 1
  probar_regla_rechaza "$validar_archivo" "capacidad positiva de tipo de habitación" \
    "INSERT INTO room_types(codigo,nombre,capacidad_max) VALUES ('restore-probe-cap-invalid-$$','Prueba',0);" || return 1
  for estado_habitacion in ACTIVA MANTENIMIENTO FUERA_DE_SERVICIO; do
    prueba="$(habitacion_prueba_sql "$estado_habitacion") INSERT INTO rooms(codigo,estado,nombre) VALUES ('restore-probe-state-$estado_habitacion-$$','$estado_habitacion','');"
    probar_regla_acepta "$validar_archivo" "estado de habitación permitido ($estado_habitacion)" "$prueba" || return 1
  done
  for estado_habitacion_invalido in __INVALIDO__ RESERVADA ''; do
    prueba="$(habitacion_prueba_sql invalid) INSERT INTO rooms(codigo,estado,nombre) VALUES ('restore-probe-state-invalid-$$','$estado_habitacion_invalido','');"
    probar_regla_rechaza "$validar_archivo" "estado de habitación ($estado_habitacion_invalido)" "$prueba" || return 1
  done

  if [ "$BASE_VERSION" -ge 2 ]; then
    prueba="INSERT INTO channel_mappings(channel_codigo,room_id,room_type_id,rate_plan_id,external_id) VALUES ('WEB',NULL,NULL,NULL,'restore-probe-$$');"
    probar_regla_rechaza "$validar_archivo" "asignación de canal a habitación" "$prueba" || return 1
    prefijo_mapeo="INSERT INTO room_types(codigo,nombre,capacidad_max) VALUES ('restore-probe-type-$$','Prueba',1); INSERT INTO rooms(codigo,room_type_id,estado,nombre) SELECT 'restore-probe-room-$$',id,'ACTIVA','' FROM room_types WHERE codigo='restore-probe-type-$$';"
    prueba="$prefijo_mapeo INSERT INTO channel_mappings(channel_codigo,room_id,room_type_id,rate_plan_id,external_id) SELECT 'WEB',id,NULL,NULL,'restore-probe-mapping-room-$$' FROM rooms WHERE codigo='restore-probe-room-$$';"
    probar_regla_acepta "$validar_archivo" "mapeo válido por habitación" "$prueba" || return 1
    prueba="$prefijo_mapeo INSERT INTO channel_mappings(channel_codigo,room_id,room_type_id,rate_plan_id,external_id) SELECT 'WEB',NULL,id,NULL,'restore-probe-mapping-type-$$' FROM room_types WHERE codigo='restore-probe-type-$$';"
    probar_regla_acepta "$validar_archivo" "mapeo válido por tipo de habitación" "$prueba" || return 1

    for tarifa_prueba in "0|NULL|NULL" "10|1|1" "4500|2|4"; do
      precio="${tarifa_prueba%%|*}"
      estancia_prueba="${tarifa_prueba#*|}"
      min_estancia="${estancia_prueba%%|*}"
      max_estancia="${estancia_prueba#*|}"
      prueba="$(tarifa_prueba_sql "$precio" "$min_estancia" "$max_estancia")"
      probar_regla_acepta "$validar_archivo" "tarifa válida ($tarifa_prueba)" "$prueba" || return 1
    done
    for tarifa_prueba in "-1|1|1" "10|0|NULL" "10|NULL|0" "10|2|1"; do
      precio="${tarifa_prueba%%|*}"
      estancia_prueba="${tarifa_prueba#*|}"
      min_estancia="${estancia_prueba%%|*}"
      max_estancia="${estancia_prueba#*|}"
      prueba="$(tarifa_prueba_sql "$precio" "$min_estancia" "$max_estancia")"
      probar_regla_rechaza "$validar_archivo" "tarifa ($tarifa_prueba)" "$prueba" || return 1
    done
  fi

  if [ "$BASE_VERSION" -ge 3 ]; then
    for fase_valida in 1 2 3; do
      prueba="INSERT INTO indicator_definitions(clave,fase,nombre,definicion,formula,fuente,unidad,periodo_por_defecto) VALUES ('restore-probe-fase-$fase_valida-$$',$fase_valida,'Prueba','Prueba','Prueba','Prueba','conteo','MENSUAL');"
      probar_regla_acepta "$validar_archivo" "fase de indicador permitida ($fase_valida)" "$prueba" || return 1
    done
    prueba="INSERT INTO indicator_definitions(clave,fase,nombre,definicion,formula,fuente,unidad,periodo_por_defecto) VALUES ('restore-probe-fase-invalida-$$',0,'Prueba','Prueba','Prueba','Prueba','conteo','MENSUAL');"
    probar_regla_rechaza "$validar_archivo" "fase de indicador" "$prueba" || return 1
  fi

  if [ "$BASE_VERSION" -le 5 ]; then
    prueba="$(reserva_prueba_sql PENDIENTE WEB 1 2026-10-12 2026-10-11)"
    probar_regla_rechaza "$validar_archivo" "intervalo de reserva" "$prueba" || return 1
    prueba="$(reserva_prueba_sql PENDIENTE WEB 1 2026-10-10 2026-10-13)"
    probar_regla_acepta "$validar_archivo" "intervalo de reserva de varias noches" "$prueba" || return 1
  fi

  cuarto_probe="__hotel_restore_room_$sufijo_prueba"
  prueba="$(habitacion_prueba_sql intervalo) $(reserva_prueba_sql PENDIENTE WEB) INSERT INTO reservation_items(reservation_id,room_id,desde,hasta) SELECT id,(SELECT id FROM rooms WHERE codigo='__hotel_restore_room_$$_intervalo'),'2026-10-12','2026-10-11' FROM reservations WHERE codigo='__hotel_restore_probe_$$';"
  probar_regla_rechaza "$validar_archivo" "intervalo de asignación de habitación" "$prueba" || return 1
  prueba="$(habitacion_prueba_sql intervalo) $(reserva_prueba_sql PENDIENTE WEB) INSERT INTO reservation_items(reservation_id,room_id,desde,hasta) SELECT id,(SELECT id FROM rooms WHERE codigo='__hotel_restore_room_$$_intervalo'),'2026-10-11','2026-10-14' FROM reservations WHERE codigo='__hotel_restore_probe_$$';"
  probar_regla_acepta "$validar_archivo" "intervalo válido de asignación de habitación" "$prueba" || return 1
  probar_regla_rechaza "$validar_archivo" "intervalo de bloqueo" \
    "INSERT INTO blocks(desde,hasta) VALUES ('2026-10-12','2026-10-11');" || return 1
  probar_regla_acepta "$validar_archivo" "intervalo válido de bloqueo" \
    "INSERT INTO blocks(desde,hasta) VALUES ('2026-10-11','2026-10-14');" || return 1

  if [ "$BASE_VERSION" -ge 12 ]; then
    prueba="$(reserva_prueba_sql PENDIENTE WEB) INSERT INTO pagos(reservation_id,monto_cents,moneda,concepto,actor,creado_en) SELECT id,0,'COP','','restauracion',CURRENT_TIMESTAMP FROM reservations WHERE codigo='__hotel_restore_probe_$$';"
    probar_regla_rechaza "$validar_archivo" "monto de pago positivo" "$prueba" || return 1
    prueba="$(reserva_prueba_sql PENDIENTE WEB) INSERT INTO pagos(reservation_id,monto_cents,moneda,concepto,actor,creado_en) SELECT id,1,'COP','','restauracion',CURRENT_TIMESTAMP FROM reservations WHERE codigo='__hotel_restore_probe_$$';"
    probar_regla_acepta "$validar_archivo" "monto positivo de pago" "$prueba" || return 1
    prueba="$(reserva_prueba_sql PENDIENTE WEB) INSERT INTO pagos(reservation_id,monto_cents,moneda,concepto,actor,creado_en) SELECT id,5000,'COP','','restauracion',CURRENT_TIMESTAMP FROM reservations WHERE codigo='__hotel_restore_probe_$$';"
    probar_regla_acepta "$validar_archivo" "monto plural de pago" "$prueba" || return 1
  fi
  if [ "$BASE_VERSION" -ge 15 ]; then
    for autor_valido in HUESPED HOTEL; do
      prueba="$(reserva_prueba_sql PENDIENTE WEB) INSERT INTO mensajes(reservation_id,autor,texto,creado_en,visto) SELECT id,'$autor_valido','x',CURRENT_TIMESTAMP,0 FROM reservations WHERE codigo='__hotel_restore_probe_$$';"
      probar_regla_acepta "$validar_archivo" "autor de mensaje permitido ($autor_valido)" "$prueba" || return 1
    done
    for autor_invalido in __INVALIDO__ ADMIN ''; do
      prueba="$(reserva_prueba_sql PENDIENTE WEB) INSERT INTO mensajes(reservation_id,autor,texto,creado_en,visto) SELECT id,'$autor_invalido','x',CURRENT_TIMESTAMP,0 FROM reservations WHERE codigo='__hotel_restore_probe_$$';"
      probar_regla_rechaza "$validar_archivo" "autor de mensaje ($autor_invalido)" "$prueba" || return 1
    done
    prueba="$(reserva_prueba_sql PENDIENTE WEB) INSERT INTO mensajes(reservation_id,autor,texto,creado_en,visto) SELECT id,'HOTEL','',CURRENT_TIMESTAMP,0 FROM reservations WHERE codigo='__hotel_restore_probe_$$';"
    probar_regla_rechaza "$validar_archivo" "texto de mensaje no vacío" "$prueba" || return 1
    for texto_mensaje in "'x'" "'mensaje de prueba'" "printf('%1000s','x')"; do
      prueba="$(reserva_prueba_sql PENDIENTE WEB) INSERT INTO mensajes(reservation_id,autor,texto,creado_en,visto) SELECT id,'HOTEL',$texto_mensaje,CURRENT_TIMESTAMP,0 FROM reservations WHERE codigo='__hotel_restore_probe_$$';"
      probar_regla_acepta "$validar_archivo" "texto de mensaje válido ($texto_mensaje)" "$prueba" || return 1
    done
    prueba="$(reserva_prueba_sql PENDIENTE WEB) INSERT INTO mensajes(reservation_id,autor,texto,creado_en,visto) SELECT id,'HOTEL',printf('%1001s','x'),CURRENT_TIMESTAMP,0 FROM reservations WHERE codigo='__hotel_restore_probe_$$';"
    probar_regla_rechaza "$validar_archivo" "longitud máxima de mensaje" "$prueba" || return 1
  fi

  if [ "$BASE_VERSION" -ge 14 ]; then
    for coordenada_prueba in "-90:0" "90:0" "0:-180" "0:180" "-45.5:-74.1"; do
      latitud="${coordenada_prueba%%:*}"
      longitud="${coordenada_prueba#*:}"
      prueba="$(lugar_prueba_sql "restore-probe-coordinate-$latitud-$longitud-$$" "$latitud" "$longitud")"
      probar_regla_acepta "$validar_archivo" "coordenada permitida ($coordenada_prueba)" "$prueba" || return 1
    done
    for coordenada_prueba in "-90.01:0" "90.01:0" "0:-180.01" "0:180.01"; do
      latitud="${coordenada_prueba%%:*}"
      longitud="${coordenada_prueba#*:}"
      prueba="$(lugar_prueba_sql "restore-probe-outside-$latitud-$longitud-$$" "$latitud" "$longitud")"
      probar_regla_rechaza "$validar_archivo" "rango geográfico ($coordenada_prueba)" "$prueba" || return 1
    done
  fi
  if [ "$BASE_VERSION" -ge 21 ]; then
    for categoria_valida in COMER VISITAR ALOJARSE; do
      prueba="INSERT INTO lugares_interes(nombre,latitud,longitud,creado_en,categoria) VALUES ('restore-probe-category-$categoria_valida-$$',0,0,CURRENT_TIMESTAMP,'$categoria_valida');"
      probar_regla_acepta "$validar_archivo" "categoría de lugar permitida ($categoria_valida)" "$prueba" || return 1
    done
    prueba="INSERT INTO lugares_interes(nombre,latitud,longitud,creado_en,categoria) VALUES ('restore-probe-category-invalid-$$',0,0,CURRENT_TIMESTAMP,'__INVALIDO__');"
    probar_regla_rechaza "$validar_archivo" "categoría de lugar" "$prueba" || return 1
  fi
}

recuperar_estado_anterior() {
  if [ -z "$COPIA" ] || [ ! -f "$COPIA" ]; then
    echo "ERROR: no hay copia previa para recuperación automática." >&2
    return 1
  fi

  RECUPERACION="$TEMPORAL_DIR/estado-anterior.sqlite3"
  RECUPERACION_EN_CURSO="1"
  if sqlite3 "$COPIA" ".backup '$RECUPERACION'" \
    && chown "$HOTEL_UID:$HOTEL_GID" "$RECUPERACION" \
    && chmod 660 "$RECUPERACION" \
    && rm -f "$DESTINO-wal" "$DESTINO-shm" \
    && mv "$RECUPERACION" "$DESTINO" \
    && validar_base "$DESTINO" \
    && validar_escritura_api "$DESTINO"; then
    FASE_DESTRUCTIVA="0"
    RECUPERACION_EN_CURSO="0"
    echo "estado anterior recuperado desde $COPIA" >&2
    return 0
  fi

  RECUPERACION_EN_CURSO="0"
  echo "ERROR: no se pudo recuperar automáticamente. Conserva y restaura $COPIA" >&2
  return 1
}

manejar_senal() {
  codigo_senal="$1"
  trap - INT TERM
  if [ "$FASE_DESTRUCTIVA" = "1" ]; then
    echo "ERROR: restauración interrumpida; se intenta recuperar el estado anterior." >&2
    if [ "$RECUPERACION_EN_CURSO" = "1" ]; then
      echo "ERROR: la recuperación también fue interrumpida. Conserva la copia $COPIA" >&2
    elif [ -n "$COPIA" ]; then
      recuperar_estado_anterior || true
    else
      echo "ERROR: no existía una base previa para recuperar; revisa $DESTINO antes de iniciar la API." >&2
    fi
  fi
  exit "$codigo_senal"
}

trap 'manejar_senal 130' INT
trap 'manejar_senal 143' TERM

validar_base() {
  validar_archivo="$1"
  modo_validacion="${2:-completa}"

  BASE_INTEGRIDAD="$(consulta_sqlite "$validar_archivo" 'PRAGMA integrity_check;')" || return 1
  if [ "$BASE_INTEGRIDAD" != "ok" ]; then
    echo "ERROR: $validar_archivo no pasa integrity_check ($BASE_INTEGRIDAD)" >&2
    return 1
  fi

  BASE_HAY_TABLA="$(consulta_sqlite "$validar_archivo" "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='flyway_schema_history';")" || return 1
  if [ "$BASE_HAY_TABLA" != "1" ]; then
    echo "ERROR: $validar_archivo no contiene el historial de migraciones Flyway" >&2
    return 1
  fi

  for columna in installed_rank version description type script checksum installed_by installed_on execution_time success; do
    BASE_HAY_COLUMNA="$(consulta_sqlite "$validar_archivo" "SELECT COUNT(*) FROM pragma_table_info('flyway_schema_history') WHERE name='$columna';")" || return 1
    if [ "$BASE_HAY_COLUMNA" != "1" ]; then
      echo "ERROR: $validar_archivo no contiene la columna requerida flyway_schema_history.$columna" >&2
      return 1
    fi
  done

  exigir_clave_primaria flyway_schema_history installed_rank || return 1

  BASE_TIPOS_MIGRACION_NO_ADMITIDOS="$(consulta_sqlite "$validar_archivo" "SELECT COUNT(*) FROM flyway_schema_history WHERE COALESCE(type, '') NOT IN ('SQL', 'BASELINE');")" || return 1
  if [ "$BASE_TIPOS_MIGRACION_NO_ADMITIDOS" != "0" ]; then
    echo "ERROR: $validar_archivo registra un tipo de migración Flyway no admitido por esta aplicación" >&2
    return 1
  fi

  BASE_SQL_SIN_VERSION_LEGIBLE="$(consulta_sqlite "$validar_archivo" "SELECT COUNT(*) FROM flyway_schema_history WHERE type = 'SQL' AND (version IS NULL OR trim(version) = '' OR version GLOB '*[^0-9]*');")" || return 1
  if [ "$BASE_SQL_SIN_VERSION_LEGIBLE" != "0" ]; then
    echo "ERROR: $validar_archivo registra una migración SQL Flyway con versión SQL ilegible" >&2
    return 1
  fi

  BASE_MIGRACIONES_FALLIDAS="$(consulta_sqlite "$validar_archivo" 'SELECT COUNT(*) FROM flyway_schema_history WHERE COALESCE(success, 0) <> 1;')" || return 1
  if [ "$BASE_MIGRACIONES_FALLIDAS" != "0" ]; then
    echo "ERROR: $validar_archivo registra migraciones Flyway fallidas" >&2
    return 1
  fi
  BASE_MIGRACIONES_SIN_CHECKSUM="$(consulta_sqlite "$validar_archivo" "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1 AND type = 'SQL' AND version IS NOT NULL AND checksum IS NULL;")" || return 1
  if [ "$BASE_MIGRACIONES_SIN_CHECKSUM" != "0" ]; then
    echo "ERROR: $validar_archivo registra migraciones SQL Flyway sin checksum" >&2
    return 1
  fi

  BASELINE_CANTIDAD="$(consulta_sqlite "$validar_archivo" "SELECT COUNT(*) FROM flyway_schema_history WHERE type = 'BASELINE';")" || return 1
  if [ "$BASELINE_CANTIDAD" -gt 1 ]; then
    echo "ERROR: $validar_archivo registra más de un baseline Flyway" >&2
    return 1
  fi
  BASELINE_VERSION="-1"
  if [ "$BASELINE_CANTIDAD" -eq 1 ]; then
    BASELINE_VERSION="$(consulta_sqlite "$validar_archivo" "SELECT COALESCE(MAX(CASE WHEN success = 1 AND version IS NOT NULL AND trim(version) <> '' AND version NOT GLOB '*[^0-9]*' THEN CAST(version AS INTEGER) END), -1) FROM flyway_schema_history WHERE type = 'BASELINE';")" || return 1
    case "$BASELINE_VERSION" in
      ''|*[!0-9]*) echo "ERROR: $validar_archivo tiene un baseline Flyway sin versión legible" >&2; return 1;;
    esac
    if [ "$BASELINE_VERSION" -ne 1 ]; then
      echo "ERROR: $validar_archivo tiene un baseline Flyway distinto de v1, la versión configurada por la API" >&2
      return 1
    fi
  fi

  BASE_VERSION="$(consulta_sqlite "$validar_archivo" "SELECT COALESCE(MAX(CASE WHEN success = 1 AND type IN ('SQL', 'BASELINE') AND version IS NOT NULL AND trim(version) <> '' AND version NOT GLOB '*[^0-9]*' THEN CAST(version AS INTEGER) END), -1) FROM flyway_schema_history;")" || return 1
  case "$BASE_VERSION" in
    ''|*[!0-9]*) echo "ERROR: $validar_archivo no tiene una migración o baseline Flyway exitoso y legible" >&2; return 1;;
  esac
  if [ "$BASE_VERSION" -lt 1 ]; then
    echo "ERROR: $validar_archivo no registra una migración SQL o baseline Flyway exitoso" >&2
    return 1
  fi
  if [ "$BASE_VERSION" -gt 21 ]; then
    echo "ERROR: $validar_archivo tiene una versión Flyway posterior a la que soporta esta imagen (v21)" >&2
    return 1
  fi

  # baseline-on-migrate está activo en la API con la versión predeterminada 1:
  # solo se admite un marcador BASELINE v1 y migraciones SQL desde V2. Exige una
  # fila SQL por cada versión pendiente, sin huecos ni duplicados, hasta la versión máxima.
  BASE_SQL_INICIO=1
  if [ "$BASELINE_CANTIDAD" -eq 1 ]; then
    BASE_SQL_INICIO=$((BASELINE_VERSION + 1))
  fi
  BASE_MIGRACIONES_SQL="$(consulta_sqlite "$validar_archivo" "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1 AND type = 'SQL';")" || return 1
  BASE_SQL_ESPERADAS=$((BASE_VERSION - BASE_SQL_INICIO + 1))
  if [ "$BASE_SQL_ESPERADAS" -lt 0 ]; then
    BASE_SQL_ESPERADAS=0
  fi
  if [ "$BASE_MIGRACIONES_SQL" != "$BASE_SQL_ESPERADAS" ]; then
    echo "ERROR: $validar_archivo no registra un historial Flyway SQL completo desde v$BASE_SQL_INICIO hasta v$BASE_VERSION" >&2
    return 1
  fi
  if [ "$BASE_SQL_ESPERADAS" -gt 0 ]; then
    for version_esperada in $(seq "$BASE_SQL_INICIO" "$BASE_VERSION"); do
      BASE_HAY_MIGRACION="$(consulta_sqlite "$validar_archivo" "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1 AND type = 'SQL' AND version = '$version_esperada';")" || return 1
      if [ "$BASE_HAY_MIGRACION" != "1" ]; then
        echo "ERROR: $validar_archivo no registra exactamente una migración SQL exitosa v$version_esperada" >&2
        return 1
      fi

      if [ ! -r "$FLYWAY_CHECKSUMS_DB" ]; then
        echo "ERROR: no está disponible el manifiesto de checksums Flyway de esta imagen" >&2
        return 1
      fi
      DESCRIPCION_ESPERADA="$(consulta_sqlite "$FLYWAY_CHECKSUMS_DB" "SELECT description FROM migration_checksum WHERE version = '$version_esperada';")" || return 1
      SCRIPT_ESPERADO="$(consulta_sqlite "$FLYWAY_CHECKSUMS_DB" "SELECT script FROM migration_checksum WHERE version = '$version_esperada';")" || return 1
      CHECKSUM_ESPERADO="$(consulta_sqlite "$FLYWAY_CHECKSUMS_DB" "SELECT checksum FROM migration_checksum WHERE version = '$version_esperada';")" || return 1
      if [ -z "$DESCRIPCION_ESPERADA" ] || [ -z "$SCRIPT_ESPERADO" ]; then
        echo "ERROR: el manifiesto de la imagen no define los metadatos de V$version_esperada" >&2
        return 1
      fi
      case "$CHECKSUM_ESPERADO" in
        -*) CHECKSUM_DIGITOS="${CHECKSUM_ESPERADO#-}";;
        *) CHECKSUM_DIGITOS="$CHECKSUM_ESPERADO";;
      esac
      case "$CHECKSUM_DIGITOS" in
        ''|*[!0-9]*) echo "ERROR: el manifiesto de la imagen no define un checksum válido para V$version_esperada" >&2; return 1;;
      esac
      CHECKSUM_APLICADO="$(consulta_sqlite "$validar_archivo" "SELECT CAST(checksum AS TEXT) FROM flyway_schema_history WHERE success = 1 AND type = 'SQL' AND version = '$version_esperada';")" || return 1
      if [ "$CHECKSUM_APLICADO" != "$CHECKSUM_ESPERADO" ]; then
        echo "ERROR: $validar_archivo tiene un checksum distinto al de la migración V$version_esperada incluida en esta imagen" >&2
        return 1
      fi
      DESCRIPCION_APLICADA="$(consulta_sqlite "$validar_archivo" "SELECT description FROM flyway_schema_history WHERE success = 1 AND type = 'SQL' AND version = '$version_esperada';")" || return 1
      if [ "$DESCRIPCION_APLICADA" != "$DESCRIPCION_ESPERADA" ]; then
        echo "ERROR: $validar_archivo tiene una descripción distinta a la migración V$version_esperada incluida en esta imagen" >&2
        return 1
      fi
      SCRIPT_APLICADO="$(consulta_sqlite "$validar_archivo" "SELECT script FROM flyway_schema_history WHERE success = 1 AND type = 'SQL' AND version = '$version_esperada';")" || return 1
      if [ "$SCRIPT_APLICADO" != "$SCRIPT_ESPERADO" ]; then
        echo "ERROR: $validar_archivo tiene un nombre de script distinto a la migración V$version_esperada incluida en esta imagen" >&2
        return 1
      fi
    done
  fi

  # Cada tabla y columna se exige a partir de la migración que la creó. Esto permite restaurar
  # respaldos válidos antiguos para que Flyway complete las migraciones al arrancar la API.
  for requerido in \
    1:hotel_config 1:users 1:reservations 1:reservation_items \
    1:reservation_history 1:blocks 1:rooms 1:room_types \
    2:rate_plans 2:rates 2:channels 2:channel_mappings 2:ota_syncs \
    3:indicator_definitions 3:indicator_results 3:adoption_activities \
    5:admin_actions 7:usuarios 12:pagos 13:amenidades \
    13:room_type_amenidades 14:lugares_interes 15:mensajes; do
    tabla_version="${requerido%%:*}"
    tabla="${requerido#*:}"
    if [ "$BASE_VERSION" -ge "$tabla_version" ]; then
      BASE_HAY_TABLA="$(consulta_sqlite "$validar_archivo" "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='$tabla';")" || return 1
      if [ "$BASE_HAY_TABLA" != "1" ]; then
        echo "ERROR: $validar_archivo (v$BASE_VERSION) no contiene la tabla requerida $tabla" >&2
        return 1
      fi
    fi
  done

  for requerido in \
    1:hotel_config:clave 1:hotel_config:valor 1:hotel_config:actualizado_en \
    1:users:id 1:users:email 1:users:hash 1:users:rol 1:users:activo 1:users:creado_en \
    1:reservations:id 1:reservations:codigo 1:reservations:email 1:reservations:nombre \
    1:reservations:llegada 1:reservations:salida 1:reservations:huespedes \
    1:reservations:estado 1:reservations:origen 1:reservations:idempotencia 1:reservations:creado_en \
    1:reservation_items:reservation_id 1:reservation_items:room_id \
    1:reservation_items:desde 1:reservation_items:hasta \
    1:reservation_history:reservation_id 1:reservation_history:estado_nuevo \
    1:reservation_history:actor 1:reservation_history:en \
    1:blocks:room_id 1:blocks:desde 1:blocks:hasta 1:blocks:motivo \
    1:rooms:id 1:rooms:codigo 1:rooms:room_type_id 1:rooms:estado 1:rooms:nombre \
    1:room_types:id 1:room_types:codigo 1:room_types:nombre 1:room_types:capacidad_max \
    2:rate_plans:id 2:rate_plans:codigo 2:rate_plans:nombre 2:rate_plans:moneda 2:rate_plans:activo \
    2:rates:id 2:rates:rate_plan_id 2:rates:room_type_id 2:rates:fecha 2:rates:precio_cents \
    2:rates:min_estancia 2:rates:max_estancia 2:rates:cerrado \
    2:channels:codigo 2:channels:nombre 2:channels:activo \
    2:channel_mappings:channel_codigo 2:channel_mappings:external_id \
    2:ota_syncs:channel_codigo 2:ota_syncs:operacion 2:ota_syncs:exitosa 2:ota_syncs:en \
    3:indicator_definitions:clave 3:indicator_definitions:fase 3:indicator_definitions:nombre \
    3:indicator_definitions:definicion 3:indicator_definitions:formula 3:indicator_definitions:fuente \
    3:indicator_definitions:unidad 3:indicator_definitions:periodo_por_defecto 3:indicator_definitions:activa \
    3:indicator_results:clave 3:indicator_results:periodo 3:indicator_results:generado_en \
    3:adoption_activities:tipo 3:adoption_activities:descripcion \
    3:adoption_activities:fecha 3:adoption_activities:con_datos_de_origen \
    4:reservations:total_cents 4:reservations:moneda 4:reservations:rate_plan_id \
    5:admin_actions:actor 5:admin_actions:metodo 5:admin_actions:ruta 5:admin_actions:estado 5:admin_actions:en \
    7:usuarios:google_sub 7:usuarios:email 7:usuarios:nombre 7:usuarios:creado_en \
    7:reservations:usuario_id \
    12:pagos:reservation_id 12:pagos:monto_cents 12:pagos:moneda 12:pagos:actor 12:pagos:creado_en \
    13:amenidades:id 13:amenidades:codigo 13:amenidades:nombre \
    13:room_type_amenidades:room_type_id 13:room_type_amenidades:amenidad_id \
    14:lugares_interes:id 14:lugares_interes:nombre 14:lugares_interes:descripcion \
    14:lugares_interes:latitud 14:lugares_interes:longitud 14:lugares_interes:activo 14:lugares_interes:creado_en \
    15:mensajes:reservation_id 15:mensajes:autor 15:mensajes:texto 15:mensajes:creado_en 15:mensajes:visto \
    9:rate_plans:descuento_pct 10:users:debe_cambiar_clave \
    11:reservation_history:detalle 16:users:nombre \
    1:reservation_history:estado_ant \
    19:pagos:id 19:pagos:concepto 19:pagos:anulado_en 19:pagos:anulado_por 19:mensajes:id \
    20:reservations:hora_entrada 20:reservations:hora_salida 20:reservations:politica_cancelacion \
    21:lugares_interes:categoria; do
    minimo="${requerido%%:*}"
    restantes="${requerido#*:}"
    tabla="${restantes%%:*}"
    columna="${restantes#*:}"
    if [ "$BASE_VERSION" -lt "$minimo" ]; then
      continue
    fi
    BASE_HAY_COLUMNA="$(consulta_sqlite "$validar_archivo" "SELECT COUNT(*) FROM pragma_table_info('$tabla') WHERE name='$columna';")" || return 1
    if [ "$BASE_HAY_COLUMNA" != "1" ]; then
      echo "ERROR: $validar_archivo (v$BASE_VERSION) no contiene la columna requerida $tabla.$columna" >&2
      return 1
    fi
  done

  exigir_columnas_no_nulas || return 1

  # Las reglas se verifican con PRAGMA y operaciones temporales sobre la candidata,
  # no buscando texto en sqlite_master, que puede estar en comentarios o índices parciales.
  exigir_clave_primaria reservations id || return 1
  exigir_clave_primaria reservation_items id || return 1
  exigir_clave_primaria reservation_history id || return 1
  exigir_clave_primaria hotel_config clave TEXT || return 1
  exigir_clave_primaria users id || return 1
  exigir_indice_unico users email || return 1
  exigir_clave_primaria rooms id || return 1
  exigir_indice_unico rooms codigo || return 1
  exigir_clave_primaria room_types id || return 1
  exigir_indice_unico room_types codigo || return 1
  exigir_clave_primaria blocks id || return 1
  exigir_indice_unico reservations codigo || return 1
  if [ "$BASE_VERSION" -ge 6 ]; then
    exigir_indice_unico reservations "idempotencia,email" || return 1
  else
    exigir_indice_unico reservations idempotencia || return 1
  fi

  exigir_indice reservations idx_reservations_llegada "llegada,estado" || return 1
  exigir_indice reservation_items idx_reservation_items_solape "room_id,desde,hasta" || return 1
  exigir_indice blocks idx_blocks_solape "room_id,desde,hasta" || return 1
  if [ "$BASE_VERSION" -ge 2 ]; then
    exigir_clave_primaria rate_plans id || return 1
    exigir_indice_unico rate_plans codigo || return 1
    exigir_clave_primaria rates id || return 1
    exigir_clave_primaria channels codigo TEXT || return 1
    exigir_clave_primaria channel_mappings id || return 1
    exigir_clave_primaria ota_syncs id || return 1
    exigir_indice rates idx_rates_busqueda "room_type_id,fecha" || return 1
    exigir_indice channel_mappings idx_channel_mappings_room "channel_codigo,room_id" || return 1
    exigir_indice ota_syncs idx_ota_syncs_canal "channel_codigo,en" || return 1
    exigir_indice_unico rates "rate_plan_id,room_type_id,fecha" || return 1
    exigir_indice_unico channel_mappings "channel_codigo,external_id" || return 1
  fi
  if [ "$BASE_VERSION" -ge 3 ]; then
    exigir_clave_primaria indicator_definitions clave TEXT || return 1
    exigir_clave_primaria indicator_results id || return 1
    exigir_clave_primaria adoption_activities id || return 1
    exigir_indice indicator_results idx_indicator_results_clave "clave,periodo" || return 1
    exigir_indice adoption_activities idx_adoption_activities_fecha fecha || return 1
    exigir_indice_unico indicator_results "clave,periodo" || return 1
  fi
  if [ "$BASE_VERSION" -ge 7 ]; then
    exigir_clave_primaria usuarios id || return 1
    exigir_indice_unico usuarios google_sub || return 1
    exigir_indice reservations idx_reservations_usuario usuario_id || return 1
    exigir_indice_expresion usuarios idx_usuarios_email "lower(email)" || return 1
  fi
  if [ "$BASE_VERSION" -ge 5 ]; then
    exigir_clave_primaria admin_actions id || return 1
  fi
  exigir_fk reservation_items reservations reservation_id id CASCADE || return 1
  exigir_fk reservation_items rooms room_id id "NO ACTION" || return 1
  exigir_fk reservation_history reservations reservation_id id CASCADE || return 1
  exigir_fk rooms room_types room_type_id id "NO ACTION" || return 1
  exigir_fk blocks rooms room_id id "NO ACTION" || return 1
  if [ "$BASE_VERSION" -ge 2 ]; then
    exigir_fk rates rate_plans rate_plan_id id "NO ACTION" || return 1
    exigir_fk rates room_types room_type_id id "NO ACTION" || return 1
    exigir_fk channel_mappings channels channel_codigo codigo "NO ACTION" || return 1
    exigir_fk channel_mappings rooms room_id id "NO ACTION" || return 1
    exigir_fk channel_mappings room_types room_type_id id "NO ACTION" || return 1
    exigir_fk channel_mappings rate_plans rate_plan_id id "NO ACTION" || return 1
  fi
  if [ "$BASE_VERSION" -ge 3 ]; then
    exigir_fk indicator_results indicator_definitions clave clave "NO ACTION" || return 1
  fi
  if [ "$BASE_VERSION" -ge 4 ]; then
    exigir_fk reservations rate_plans rate_plan_id id "NO ACTION" || return 1
  fi
  if [ "$BASE_VERSION" -ge 7 ]; then
    exigir_fk reservations usuarios usuario_id id "NO ACTION" || return 1
  fi
  if [ "$BASE_VERSION" -ge 13 ]; then
    exigir_clave_primaria amenidades id || return 1
    exigir_indice_unico amenidades codigo || return 1
    exigir_clave_primaria_compuesta room_type_amenidades "room_type_id,amenidad_id" || return 1
    exigir_fk room_type_amenidades room_types room_type_id id CASCADE || return 1
    exigir_fk room_type_amenidades amenidades amenidad_id id CASCADE || return 1
  fi
  if [ "$BASE_VERSION" -ge 14 ]; then
    exigir_clave_primaria lugares_interes id || return 1
  fi
  if [ "$BASE_VERSION" -ge 12 ]; then
    exigir_clave_primaria pagos id || return 1
    exigir_indice pagos idx_pagos_reserva reservation_id || return 1
    exigir_fk pagos reservations reservation_id id CASCADE || return 1
  fi
  if [ "$BASE_VERSION" -ge 15 ]; then
    exigir_clave_primaria mensajes id || return 1
    exigir_indice mensajes idx_mensajes_reserva reservation_id || return 1
    exigir_fk mensajes reservations reservation_id id CASCADE || return 1
  fi

  BASE_FK_INTEGRIDAD="$(consulta_sqlite "$validar_archivo" 'PRAGMA foreign_key_check;')" || return 1
  if [ -n "$BASE_FK_INTEGRIDAD" ]; then
    echo "ERROR: $validar_archivo contiene filas que violan claves foráneas: $BASE_FK_INTEGRIDAD" >&2
    return 1
  fi

  BASE_TABLAS="$(consulta_sqlite "$validar_archivo" "SELECT COUNT(*) FROM sqlite_master WHERE type='table';")" || return 1
  case "$BASE_TABLAS" in
    ''|*[!0-9]*) echo "ERROR: no se pudo contar las tablas de $validar_archivo" >&2; return 1;;
  esac

  BASE_RESERVAS="$(consulta_sqlite "$validar_archivo" 'SELECT COUNT(*) FROM reservations;')" || return 1
  case "$BASE_RESERVAS" in
    ''|*[!0-9]*) echo "ERROR: no se pudo contar las reservas de $validar_archivo" >&2; return 1;;
  esac
  if [ "$modo_validacion" = "completa" ]; then
    validar_reglas_semanticas || return 1
    validar_autoincrementos || return 1
  fi
}

if [ ! -f "$RESPALDO" ]; then
  echo "ERROR: no existe el respaldo $RESPALDO" >&2
  exit 1
fi

if ! id hotel >/dev/null 2>&1; then
  echo "ERROR: el contenedor de operación no define el usuario hotel de la API." >&2
  exit 1
fi
HOTEL_UID="$(id -u hotel)"
HOTEL_GID="$(id -g hotel)"

# El archivo debe tener integridad, historial de migraciones y el esquema del hotel antes de
# abrir, respaldar o modificar el destino y sus archivos WAL/SHM.
if ! validar_base "$RESPALDO" estructura; then
  echo "ERROR: el respaldo no es una base del hotel. No se toca el destino." >&2
  exit 1
fi
if ! validar_directorio_api "$(dirname "$DESTINO")"; then
  exit 1
fi
echo "respaldo verificado: esquema v$BASE_VERSION, $BASE_TABLAS tablas, $BASE_RESERVAS reservas, integridad ok"

# Se crea y comprueba la copia candidata junto al destino. La sustitución posterior usa mv en
# el mismo sistema de archivos; una copia incompleta nunca obliga a borrar la base existente.
TEMPORAL_DIR="$(mktemp -d "$(dirname "$DESTINO")/.restaurar-XXXXXX")"
TEMPORAL="$TEMPORAL_DIR/base.sqlite3"
if ! sqlite3 "$RESPALDO" ".backup '$TEMPORAL'"; then
  echo "ERROR: no se pudo preparar una copia restaurable. El destino sigue intacto." >&2
  exit 1
fi
if ! validar_base "$TEMPORAL"; then
  echo "ERROR: la copia candidata no pasó la validación. El destino sigue intacto." >&2
  exit 1
fi
if ! chown "$HOTEL_UID:$HOTEL_GID" "$TEMPORAL" || ! chmod 660 "$TEMPORAL"; then
  echo "ERROR: no se pudieron preparar propietario y permisos compatibles con la API." >&2
  exit 1
fi

# Se conserva una copia coherente del estado anterior antes de instalar la candidata.
if [ -f "$DESTINO" ]; then
  COPIA_DIR="$(mktemp -d "$(dirname "$DESTINO")/.hotel-antes-de-restaurar-XXXXXX")"
  COPIA="$COPIA_DIR/estado.sqlite3"
  if ! sqlite3 "$DESTINO" ".backup '$COPIA'" || ! chmod 600 "$COPIA"; then
    rm -rf "$COPIA_DIR"
    COPIA=""
    echo "ERROR: no se pudo guardar el estado actual. El destino sigue intacto." >&2
    exit 1
  fi
  echo "estado actual guardado en $COPIA"
fi

# Quitar el WAL viejo y reemplazar el archivo principal de forma atómica en el mismo volumen.
# Desde aquí una señal debe intentar volver a la copia previa antes de terminar.
FASE_DESTRUCTIVA="1"
if ! rm -f "$DESTINO-wal" "$DESTINO-shm"; then
  echo "ERROR: no se pudieron limpiar los archivos WAL/SHM anteriores." >&2
  recuperar_estado_anterior || true
  exit 1
fi
if ! mv "$TEMPORAL" "$DESTINO"; then
  echo "ERROR: no se pudo instalar la copia validada; no se anuncia la restauración." >&2
  recuperar_estado_anterior || true
  exit 1
fi

# La validación posterior también es obligatoria. Si falla, restaurar la copia de seguridad
# anterior si existía; en cualquier caso se conserva el diagnóstico y no se imprime éxito.
if ! validar_base "$DESTINO"; then
  echo "ERROR: la base instalada no pasó la verificación posterior." >&2
  if [ -n "$COPIA" ]; then
    recuperar_estado_anterior || true
  else
    echo "ERROR: no había una base anterior; revisa el archivo $DESTINO antes de iniciar la aplicación." >&2
  fi
  exit 1
fi

if ! validar_escritura_api "$DESTINO"; then
  if [ -n "$COPIA" ]; then
    recuperar_estado_anterior || true
  else
    echo "ERROR: no había una base anterior a la cual volver." >&2
  fi
  exit 1
fi

FASE_DESTRUCTIVA="0"
echo "restauracion completada"
echo "  archivo:   $DESTINO"
echo "  integridad: $BASE_INTEGRIDAD"
echo "  esquema:   v$BASE_VERSION ($BASE_TABLAS tablas)"
echo "  reservas:  $BASE_RESERVAS"
echo
echo "Arranca la aplicacion y comprueba /api/health antes de darla por buena."
