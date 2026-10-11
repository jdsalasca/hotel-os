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

  BASE_VERSION="$(consulta_sqlite "$validar_archivo" "SELECT COALESCE(MAX(CASE WHEN success = 1 AND type = 'SQL' AND version IS NOT NULL AND trim(version) <> '' AND version NOT GLOB '*[^0-9]*' THEN CAST(version AS INTEGER) END), -1) FROM flyway_schema_history;")" || return 1
  case "$BASE_VERSION" in
    ''|*[!0-9]*) echo "ERROR: $validar_archivo no tiene una migración SQL Flyway exitosa y legible" >&2; return 1;;
  esac
  if [ "$BASE_VERSION" -lt 1 ]; then
    echo "ERROR: $validar_archivo no registra una migración SQL Flyway exitosa" >&2
    return 1
  fi
  if [ "$BASE_VERSION" -gt 21 ]; then
    echo "ERROR: $validar_archivo tiene una versión Flyway posterior a la que soporta esta imagen (v21)" >&2
    return 1
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
