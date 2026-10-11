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

validar_base() {
  validar_archivo="$1"

  BASE_INTEGRIDAD="$(consulta_sqlite "$validar_archivo" 'PRAGMA integrity_check;')" || return 1
  if [ "$BASE_INTEGRIDAD" != "ok" ]; then
    echo "ERROR: $validar_archivo no pasa integrity_check ($BASE_INTEGRIDAD)" >&2
    return 1
  fi

  BASE_VERSION="$(consulta_sqlite "$validar_archivo" 'SELECT COALESCE(MAX(CAST(version AS INTEGER)), -1) FROM flyway_schema_history;')" || return 1
  case "$BASE_VERSION" in
    ''|*[!0-9]*) echo "ERROR: $validar_archivo no tiene un historial Flyway legible" >&2; return 1;;
  esac

  for tabla in users reservations rooms room_types rates; do
    BASE_HAY_TABLA="$(consulta_sqlite "$validar_archivo" "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='$tabla';")" || return 1
    if [ "$BASE_HAY_TABLA" != "1" ]; then
      echo "ERROR: $validar_archivo no contiene la tabla requerida $tabla" >&2
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

# El archivo debe tener integridad, historial de migraciones y el esquema del hotel antes de
# abrir, respaldar o modificar el destino y sus archivos WAL/SHM.
if ! validar_base "$RESPALDO"; then
  echo "ERROR: el respaldo no es una base del hotel. No se toca el destino." >&2
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

# Se conserva una copia coherente del estado anterior antes de instalar la candidata.
if [ -f "$DESTINO" ]; then
  COPIA="$(dirname "$DESTINO")/hotel-antes-de-restaurar-$(date -u +%Y%m%dT%H%M%SZ)-$$.sqlite3"
  if ! sqlite3 "$DESTINO" ".backup '$COPIA'"; then
    echo "ERROR: no se pudo guardar el estado actual en $COPIA. El destino sigue intacto." >&2
    exit 1
  fi
  echo "estado actual guardado en $COPIA"
fi

# Quitar el WAL viejo y reemplazar el archivo principal de forma atómica en el mismo volumen.
rm -f "$DESTINO-wal" "$DESTINO-shm"
if ! mv "$TEMPORAL" "$DESTINO"; then
  echo "ERROR: no se pudo instalar la copia validada; no se anuncia la restauración." >&2
  if [ -n "$COPIA" ] && [ -f "$COPIA" ]; then
    RECUPERACION="$TEMPORAL_DIR/estado-anterior.sqlite3"
    if sqlite3 "$COPIA" ".backup '$RECUPERACION'" \
      && rm -f "$DESTINO-wal" "$DESTINO-shm" \
      && mv "$RECUPERACION" "$DESTINO"; then
      echo "estado anterior recuperado desde $COPIA" >&2
    else
      echo "ERROR: restaura manualmente el estado anterior desde $COPIA" >&2
    fi
  fi
  exit 1
fi

# La validación posterior también es obligatoria. Si falla, restaurar la copia de seguridad
# anterior si existía; en cualquier caso se conserva el diagnóstico y no se imprime éxito.
if ! validar_base "$DESTINO"; then
  echo "ERROR: la base instalada no pasó la verificación posterior." >&2
  if [ -n "$COPIA" ] && [ -f "$COPIA" ]; then
    RECUPERACION="$TEMPORAL_DIR/estado-anterior.sqlite3"
    if sqlite3 "$COPIA" ".backup '$RECUPERACION'" \
      && rm -f "$DESTINO-wal" "$DESTINO-shm" \
      && mv "$RECUPERACION" "$DESTINO" \
      && validar_base "$DESTINO"; then
      echo "estado anterior recuperado desde $COPIA" >&2
    else
      echo "ERROR: no se pudo recuperar automáticamente. Conserva y restaura $COPIA" >&2
    fi
  else
    echo "ERROR: no había una base anterior; revisa el archivo $DESTINO antes de iniciar la aplicación." >&2
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
