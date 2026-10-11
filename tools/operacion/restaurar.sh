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
TEMPORAL_DIR=""
COPIA=""
HOTEL_UID=""
HOTEL_GID=""

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

validar_escritura_api() {
  validar_archivo_api="$1"
  if ! HOTEL_DB_API="$validar_archivo_api" su -p -s /bin/sh -c \
    'sqlite3 "$HOTEL_DB_API" "PRAGMA journal_mode=WAL; BEGIN IMMEDIATE; CREATE TABLE __hotel_restore_probe (id INTEGER); ROLLBACK;" >/dev/null' hotel; then
    echo "ERROR: el usuario hotel ($HOTEL_UID:$HOTEL_GID) no puede abrir y escribir $validar_archivo_api en WAL" >&2
    return 1
  fi
}

validar_directorio_api() {
  validar_directorio="$1"
  if ! HOTEL_DIR_API="$validar_directorio" su -p -s /bin/sh -c \
    'prueba="$(mktemp "$HOTEL_DIR_API/.restaurar-permisos-XXXXXX")" && rm -f "$prueba"' hotel; then
    echo "ERROR: el directorio $validar_directorio no permite crear WAL/SHM al usuario hotel ($HOTEL_UID:$HOTEL_GID). No se toca el destino." >&2
    return 1
  fi
}

recuperar_estado_anterior() {
  if [ -z "$COPIA" ] || [ ! -f "$COPIA" ]; then
    echo "ERROR: no hay copia previa para recuperación automática." >&2
    return 1
  fi

  RECUPERACION="$TEMPORAL_DIR/estado-anterior.sqlite3"
  if sqlite3 "$COPIA" ".backup '$RECUPERACION'" \
    && chown "$HOTEL_UID:$HOTEL_GID" "$RECUPERACION" \
    && chmod 660 "$RECUPERACION" \
    && rm -f "$DESTINO-wal" "$DESTINO-shm" \
    && mv "$RECUPERACION" "$DESTINO" \
    && validar_base "$DESTINO" \
    && validar_escritura_api "$DESTINO"; then
    echo "estado anterior recuperado desde $COPIA" >&2
    return 0
  fi

  echo "ERROR: no se pudo recuperar automáticamente. Conserva y restaura $COPIA" >&2
  return 1
}

validar_base() {
  validar_archivo="$1"

  BASE_INTEGRIDAD="$(consulta_sqlite "$validar_archivo" 'PRAGMA integrity_check;')" || return 1
  if [ "$BASE_INTEGRIDAD" != "ok" ]; then
    echo "ERROR: $validar_archivo no pasa integrity_check ($BASE_INTEGRIDAD)" >&2
    return 1
  fi

  for tabla in hotel_config users reservations reservation_items reservation_history blocks \
    rooms room_types rate_plans rates channels channel_mappings ota_syncs \
    indicator_definitions indicator_results adoption_activities admin_actions usuarios \
    pagos amenidades room_type_amenidades lugares_interes mensajes; do
    BASE_HAY_TABLA="$(consulta_sqlite "$validar_archivo" "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='$tabla';")" || return 1
    if [ "$BASE_HAY_TABLA" != "1" ]; then
      echo "ERROR: $validar_archivo no contiene la tabla requerida $tabla" >&2
      return 1
    fi
  done

  for requerido in \
    flyway_schema_history:installed_rank flyway_schema_history:version \
    flyway_schema_history:description flyway_schema_history:type flyway_schema_history:script \
    flyway_schema_history:installed_by flyway_schema_history:installed_on \
    flyway_schema_history:execution_time flyway_schema_history:success \
    hotel_config:clave hotel_config:valor hotel_config:actualizado_en \
    users:id users:email users:hash users:rol users:activo users:creado_en \
    reservations:id reservations:codigo reservations:email reservations:nombre \
    reservations:llegada reservations:salida reservations:huespedes reservations:estado \
    reservations:origen reservations:idempotencia reservations:creado_en reservations:usuario_id \
    reservations:total_cents reservations:moneda reservations:rate_plan_id \
    reservations:hora_entrada reservations:hora_salida reservations:politica_cancelacion \
    reservation_items:reservation_id reservation_items:room_id reservation_items:desde reservation_items:hasta \
    reservation_history:reservation_id reservation_history:estado_nuevo reservation_history:actor reservation_history:en \
    blocks:room_id blocks:desde blocks:hasta blocks:motivo \
    rooms:id rooms:codigo rooms:room_type_id rooms:estado rooms:nombre \
    room_types:id room_types:codigo room_types:nombre room_types:capacidad_max \
    rate_plans:id rate_plans:codigo rate_plans:nombre rate_plans:moneda rate_plans:activo \
    rates:id rates:rate_plan_id rates:room_type_id rates:fecha rates:precio_cents \
    rates:min_estancia rates:max_estancia rates:cerrado \
    channels:codigo channels:nombre channels:activo \
    channel_mappings:channel_codigo channel_mappings:external_id \
    ota_syncs:channel_codigo ota_syncs:operacion ota_syncs:exitosa ota_syncs:en \
    indicator_definitions:clave indicator_definitions:fase indicator_definitions:nombre \
    indicator_definitions:definicion indicator_definitions:formula indicator_definitions:fuente \
    indicator_definitions:unidad indicator_definitions:periodo_por_defecto indicator_definitions:activa \
    indicator_results:clave indicator_results:periodo indicator_results:generado_en \
    adoption_activities:tipo adoption_activities:descripcion adoption_activities:fecha \
    adoption_activities:con_datos_de_origen \
    admin_actions:actor admin_actions:metodo admin_actions:ruta admin_actions:estado admin_actions:en \
    usuarios:google_sub usuarios:email usuarios:nombre usuarios:creado_en \
    pagos:reservation_id pagos:monto_cents pagos:moneda pagos:actor pagos:creado_en \
    amenidades:id amenidades:codigo amenidades:nombre \
    room_type_amenidades:room_type_id room_type_amenidades:amenidad_id \
    lugares_interes:id lugares_interes:nombre lugares_interes:descripcion lugares_interes:latitud \
    lugares_interes:longitud lugares_interes:activo lugares_interes:creado_en lugares_interes:categoria \
    mensajes:reservation_id mensajes:autor mensajes:texto mensajes:creado_en mensajes:visto; do
    tabla="${requerido%%:*}"
    columna="${requerido#*:}"
    BASE_HAY_COLUMNA="$(consulta_sqlite "$validar_archivo" "SELECT COUNT(*) FROM pragma_table_info('$tabla') WHERE name='$columna';")" || return 1
    if [ "$BASE_HAY_COLUMNA" != "1" ]; then
      echo "ERROR: $validar_archivo no contiene la columna requerida $tabla.$columna" >&2
      return 1
    fi
  done

  BASE_MIGRACIONES_FALLIDAS="$(consulta_sqlite "$validar_archivo" 'SELECT COUNT(*) FROM flyway_schema_history WHERE COALESCE(success, 0) <> 1;')" || return 1
  if [ "$BASE_MIGRACIONES_FALLIDAS" != "0" ]; then
    echo "ERROR: $validar_archivo registra migraciones Flyway fallidas" >&2
    return 1
  fi

  BASE_VERSION="$(consulta_sqlite "$validar_archivo" "SELECT COALESCE(MAX(CASE WHEN success = 1 AND type = 'SQL' AND version IS NOT NULL AND trim(version) <> '' AND version NOT GLOB '*[^0-9]*' THEN CAST(version AS INTEGER) END), -1) FROM flyway_schema_history;")" || return 1
  case "$BASE_VERSION" in
    ''|*[!0-9]*) echo "ERROR: $validar_archivo no tiene una migración SQL Flyway exitosa y legible" >&2; return 1;;
  esac
  if [ "$BASE_VERSION" -lt 1 ]; then
    echo "ERROR: $validar_archivo no registra una migración SQL Flyway exitosa" >&2
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
if ! validar_base "$RESPALDO"; then
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

echo "restauracion completada"
echo "  archivo:   $DESTINO"
echo "  integridad: $BASE_INTEGRIDAD"
echo "  esquema:   v$BASE_VERSION ($BASE_TABLAS tablas)"
echo "  reservas:  $BASE_RESERVAS"
echo
echo "Arranca la aplicacion y comprueba /api/health antes de darla por buena."
