#!/bin/sh
# Simulacro de restauración: demuestra que el último respaldo sirve, sin tocar la base real.
#
# Copia el respaldo más reciente a un temporal, le pasa integrity_check, confirma que trae
# el esquema migrado (flyway_schema_history) y las tablas del negocio, y cuenta reservas.
# Todo contra la copia: /data nunca se toca. Sale 1 si algo no cuadra.
#
# Uso:  ./verificar-respaldo.sh [/backups]
set -eu

RESPALDOS="${1:-/backups}"

ULTIMO="$(ls -1t "$RESPALDOS"/hotel-*.sqlite3 2>/dev/null | head -1 || true)"
if [ -z "$ULTIMO" ]; then
  echo "ERROR: no hay respaldos hotel-*.sqlite3 en $RESPALDOS" >&2
  exit 1
fi

COPIA="$(mktemp /tmp/simulacro-XXXXXX)"
trap 'rm -f "$COPIA"' EXIT INT TERM
cp "$ULTIMO" "$COPIA"

# Ojo con `set -eu` y `VAR=$(sqlite3 ...)`: si sqlite3 falla, el shell muere ahí mismo sin
# mensaje y con el código que sqlite quiera (visto en vivo: 0 y en silencio). Por eso toda
# consulta pasa por este ayudante, que convierte cualquier fallo en ERROR con motivo y 1.
consulta() {
  local salida codigo
  set +e
  salida="$(sqlite3 "$COPIA" "$1" 2>&1)"
  codigo=$?
  set -e
  if [ "$codigo" -ne 0 ]; then
    echo "ERROR: sqlite falló ($1): $salida" >&2
    exit 1
  fi
  printf '%s' "$salida"
}

INTEGRIDAD="$(consulta 'PRAGMA integrity_check;')"
if [ "$INTEGRIDAD" != "ok" ]; then
  echo "ERROR: el respaldo no pasa integrity_check: $INTEGRIDAD" >&2
  exit 1
fi

VERSION="$(consulta 'SELECT COALESCE(MAX(CAST(version AS INTEGER)), -1) FROM flyway_schema_history;')"
case "$VERSION" in
  ''|*[!0-9]*) echo "ERROR: sin historial de migraciones legible en el respaldo" >&2; exit 1;;
esac

for tabla in users reservations rooms room_types rates; do
  HAY="$(consulta "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='$tabla';")"
  if [ "$HAY" != "1" ]; then
    echo "ERROR: al respaldo le falta la tabla $tabla" >&2
    exit 1
  fi
done

NRESERVAS="$(consulta 'SELECT COUNT(*) FROM reservations;')"
echo "restauración simulada ok: $ULTIMO (esquema v$VERSION, $NRESERVAS reservas legibles)"
