#!/bin/sh
# Copia de seguridad consistente de SQLite.
#
# ⚠️  Copiar el archivo a pelo con `cp` NO es una copia válida: si hay una escritura en curso, el
#     WAL contiene cambios que el archivo principal aún no tiene y la copia sale corrupta.
#     La forma correcta es `VACUUM INTO` (o la API .backup), que produce un archivo consistente
#     mientras la base sigue en uso.
#
# Uso:  ./respaldar.sh /data/hotel.sqlite3 /backups
set -eu

ORIGEN="${1:-/data/hotel.sqlite3}"
DESTINO="${2:-/backups}"
RETENCION_DIAS="${BACKUP_RETENTION_DAYS:-30}"

if [ ! -f "$ORIGEN" ]; then
  echo "ERROR: no existe el archivo de base de datos en $ORIGEN" >&2
  exit 1
fi

mkdir -p "$DESTINO"
SELLO="$(date -u +%Y%m%dT%H%M%SZ)"
ARCHIVO="$DESTINO/hotel-$SELLO.sqlite3"

# `sqlite3 origen ".backup destino"` es la API de copia en caliente de SQLite: toma el
# lock de lectura, copia de forma consistente y deja la base usable durante el proceso.
sqlite3 "$ORIGEN" ".backup '$ARCHIVO'"

# Comprobación: un respaldo que no se puede leer no es un respaldo. Ojo con el caso trampa:
# un archivo vacío ES una base SQLite válida y pasa integrity_check, así que el tamaño y la
# tabla de reservas se verifican aparte. Sin eso, un fallo de apertura dejaba un "respaldo"
# de 0 bytes dado por bueno.
if [ ! -s "$ARCHIVO" ]; then
  echo "ERROR: el respaldo salió vacío (¿origen ilegible?): $ARCHIVO" >&2
  rm -f "$ARCHIVO"
  exit 1
fi

# Comprobación: un respaldo que no se puede leer no es un respaldo.
INTEGRIDAD="$(sqlite3 "$ARCHIVO" 'PRAGMA integrity_check;' 2>&1)"
if [ "$INTEGRIDAD" != "ok" ]; then
  echo "ERROR: el respaldo fallo la verificación de integridad: $INTEGRIDAD" >&2
  rm -f "$ARCHIVO"
  exit 1
fi

# Ojo: en SQL de SQLite el literal va con comillas simples. Con comillas dobles lo que hay
# entrecomillado se interpreta como identificador y la consulta falla.
TABLAS="$(sqlite3 "$ARCHIVO" "SELECT COUNT(*) FROM sqlite_master WHERE type='table';")"
RESERVAS_TABLA="$(sqlite3 "$ARCHIVO" "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='reservations';")"
if [ "$RESERVAS_TABLA" != "1" ]; then
  echo "ERROR: el respaldo no trae la tabla reservations: $ARCHIVO" >&2
  rm -f "$ARCHIVO"
  exit 1
fi
TAMANO="$(du -h "$ARCHIVO" | cut -f1)"
echo "respaldo creado: $ARCHIVO ($TAMANO, $TABLAS tablas, integridad ok)"

# Retención: solo se borran respaldos más antiguos que el plazo configurado.
BORRADOS="$(find "$DESTINO" -name 'hotel-*.sqlite3' -type f -mtime "+$RETENCION_DIAS" -print -delete | wc -l)"
if [ "$BORRADOS" -gt 0 ]; then
  echo "retencion: $BORRADOS respaldo(s) de mas de $RETENCION_DIAS dias eliminados"
fi

echo "ultimo respaldo: $(ls -1t "$DESTINO"/hotel-*.sqlite3 | head -1)"