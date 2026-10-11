#!/bin/sh
# Prueba aislada de la restauración. Solo crea bases sintéticas bajo un directorio temporal.
set -eu

RESTAURAR="${RESTAURAR_BIN:-/usr/local/bin/restaurar.sh}"
FLYWAY_CHECKSUMS_DB="${FLYWAY_CHECKSUMS_DB:-/usr/local/share/hotel/flyway-checksums.sqlite3}"
FLYWAY_MIGRATIONS_DIR="${FLYWAY_MIGRATIONS_DIR:-/usr/local/share/hotel/migrations}"
TEMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/probar-restaurar-segura-XXXXXX")"
HOLDER_PID=""
chown 100:101 "$TEMP_DIR"
chmod 777 "$TEMP_DIR"

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
  variante="${4:-}"
  nombre_reserva="nombre TEXT"
  if [ "$version" -le 5 ]; then nombre_reserva="nombre TEXT NOT NULL DEFAULT ''"; fi
  if [ "$version" -ge 21 ]; then
    regla_categoria="CHECK (categoria IN ('COMER','VISITAR','ALOJARSE'))"
    columna_categoria=", categoria TEXT NOT NULL DEFAULT 'VISITAR' $regla_categoria"
  else
    columna_categoria=""
  fi
  regla_capacidad="CHECK (capacidad_max > 0)"
  regla_huespedes=""
  if [ "$version" -le 5 ]; then regla_huespedes="CHECK (huespedes > 0)"; fi
  regla_estado_habitacion="CHECK (estado IN ('ACTIVA','MANTENIMIENTO','FUERA_DE_SERVICIO'))"
  regla_rol_usuario="CHECK (rol IN ('ADMIN','STAFF'))"
  regla_channel_mapping="CHECK (room_id IS NOT NULL OR room_type_id IS NOT NULL)"
  regla_fase_indicador="CHECK (fase IN (1,2,3))"
  regla_precio="CHECK (precio_cents >= 0)"
  regla_precio_pago="CHECK (monto_cents > 0)"
  regla_texto_mensaje="CHECK (length(texto) >= 1 AND length(texto) <= 1000)"
  regla_estancia_min="CHECK (min_estancia IS NULL OR min_estancia >= 1)"
  regla_estancia_max="CHECK (max_estancia IS NULL OR max_estancia >= 1)"
  regla_estancia_orden="CHECK (max_estancia IS NULL OR min_estancia IS NULL OR min_estancia <= max_estancia)"
  regla_latitud="CHECK (latitud >= -90 AND latitud <= 90)"
  regla_longitud="CHECK (longitud >= -180 AND longitud <= 180)"
  if [ "$version" -ge 19 ]; then
    estado_reserva="estado TEXT NOT NULL CHECK (estado IN ('PENDIENTE','CONFIRMADA','CANCELADA','RECHAZADA','NO_PRESENTADA'))"
  elif [ "$version" -ge 8 ]; then
    estado_reserva="estado TEXT NOT NULL CHECK (estado IN ('PENDIENTE','CONFIRMADA','CANCELADA','RECHAZADA'))"
  elif [ "$version" -ge 6 ]; then
    estado_reserva="estado TEXT NOT NULL CHECK (estado IN ('PENDIENTE','CONFIRMADA','CANCELADA'))"
  else
    estado_reserva="estado TEXT NOT NULL CHECK (estado IN ('PENDIENTE','CONFIRMADA','CANCELADA','RECHAZADA'))"
  fi
  if [ "$version" -ge 18 ] || [ "$version" -le 5 ]; then
    origen_reserva="origen TEXT NOT NULL CHECK (origen IN ('WEB','BOOKING','DESPEGAR','AIRBNB','OTRO'))"
  else
    origen_reserva="origen TEXT NOT NULL CHECK (origen IN ('WEB','BOOKING','DESPEGAR','AIRBNB'))"
  fi
  if [ "$version" -ge 6 ]; then
    unicidad_reserva=", UNIQUE (idempotencia, email)"
  else
    unicidad_reserva=", UNIQUE (idempotencia)"
  fi
  unicidad_codigo=" UNIQUE"
  estado_semilla="PENDIENTE"
  origen_semilla="WEB"
  restriccion_reserva_intervalo=""
  if [ "$version" -le 5 ]; then
    restriccion_reserva_intervalo=", CHECK (llegada < salida)"
  fi
  restriccion_intervalo=", CHECK (desde < hasta)"
  fk_reservation_items="reservation_id INTEGER NOT NULL REFERENCES reservations(id) ON DELETE CASCADE"
  fk_reservation_room="room_id INTEGER NOT NULL REFERENCES rooms(id)"
  fk_reservation_history="reservation_id INTEGER NOT NULL REFERENCES reservations(id) ON DELETE CASCADE"
  fk_reservation_plan="rate_plan_id INTEGER"
  fk_reservation_user="usuario_id INTEGER"
  if [ "$version" -ge 4 ]; then fk_reservation_plan="rate_plan_id INTEGER REFERENCES rate_plans(id)"; fi
  if [ "$version" -ge 7 ]; then fk_reservation_user="usuario_id INTEGER REFERENCES usuarios(id)"; fi
  fk_block_room="room_id INTEGER REFERENCES rooms(id)"
  fk_rates_plan="rate_plan_id INTEGER NOT NULL REFERENCES rate_plans(id)"
  fk_rates_room_type="room_type_id INTEGER NOT NULL REFERENCES room_types(id)"
  fk_channel="channel_codigo TEXT NOT NULL REFERENCES channels(codigo)"
  fk_channel_room="room_id INTEGER REFERENCES rooms(id)"
  fk_channel_room_type="room_type_id INTEGER REFERENCES room_types(id)"
  fk_channel_plan="rate_plan_id INTEGER REFERENCES rate_plans(id)"
  fk_indicator="clave TEXT NOT NULL REFERENCES indicator_definitions(clave)"
  fk_room_amenities_type="room_type_id INTEGER NOT NULL REFERENCES room_types(id) ON DELETE CASCADE"
  fk_room_amenities_amenity="amenidad_id INTEGER NOT NULL REFERENCES amenidades(id) ON DELETE CASCADE"
  fk_pago_reserva="reservation_id INTEGER NOT NULL"
  fk_mensaje_reserva="reservation_id INTEGER NOT NULL"
  if [ "$version" -ge 12 ]; then fk_pago_reserva="reservation_id INTEGER NOT NULL REFERENCES reservations(id) ON DELETE CASCADE"; fi
  if [ "$version" -ge 15 ]; then fk_mensaje_reserva="reservation_id INTEGER NOT NULL REFERENCES reservations(id) ON DELETE CASCADE"; fi
  indice_intervalo="CREATE INDEX idx_reservation_items_solape ON reservation_items(room_id, desde, hasta);"
  indice_email="CREATE INDEX idx_usuarios_email ON usuarios(lower(email));"
  id_reservations="id INTEGER PRIMARY KEY AUTOINCREMENT,"
  id_reservation_items="id INTEGER PRIMARY KEY AUTOINCREMENT,"
  id_reservation_history="id INTEGER PRIMARY KEY AUTOINCREMENT,"
  id_users="id INTEGER PRIMARY KEY"
  id_rooms="id INTEGER PRIMARY KEY"
  id_room_types="id INTEGER PRIMARY KEY"
  id_blocks="id INTEGER PRIMARY KEY"
  id_rate_plans="id INTEGER PRIMARY KEY"
  id_rates="id INTEGER PRIMARY KEY"
  id_channel_mappings="id INTEGER PRIMARY KEY"
  id_ota_syncs="id INTEGER PRIMARY KEY"
  id_indicator_results="id INTEGER PRIMARY KEY"
  id_adoption_activities="id INTEGER PRIMARY KEY"
  id_admin_actions="id INTEGER PRIMARY KEY"
  id_usuarios="id INTEGER PRIMARY KEY"
  id_amenidades="id INTEGER PRIMARY KEY"
  id_lugares="id INTEGER PRIMARY KEY"
  clave_hotel_config="clave TEXT PRIMARY KEY"
  pk_room_type_amenidades=", PRIMARY KEY (room_type_id, amenidad_id)"
  unicidad_email_admin="UNIQUE"
  unicidad_codigo_room="UNIQUE"
  unicidad_codigo_room_type="UNIQUE"
  unicidad_codigo_rate_plan="UNIQUE"
  unicidad_google_sub="UNIQUE"
  unicidad_codigo_amenidad="UNIQUE"
  id_pagos=""
  id_mensajes=""
  if [ "$version" -ge 12 ]; then
    id_pagos="id INTEGER PRIMARY KEY AUTOINCREMENT,"
  fi
  if [ "$version" -ge 15 ]; then
    id_mensajes="id INTEGER PRIMARY KEY AUTOINCREMENT,"
  fi
  notnull_estado_reserva="NOT NULL"
  notnull_codigo_reserva="NOT NULL"
  case "${4:-}" in
    origen-antiguo) origen_reserva="origen TEXT NOT NULL CHECK (origen IN ('WEB','BOOKING','DESPEGAR','AIRBNB'))" ;;
    estado-antiguo) estado_reserva="estado TEXT NOT NULL CHECK (estado IN ('PENDIENTE','CONFIRMADA','CANCELADA','RECHAZADA'))" ;;
    estado-solo-no-presentada) estado_reserva="estado TEXT NOT NULL CHECK (estado IN ('NO_PRESENTADA'))"; estado_semilla="NO_PRESENTADA" ;;
    origen-solo-otro) origen_reserva="origen TEXT NOT NULL CHECK (origen IN ('OTRO'))"; origen_semilla="OTRO" ;;
    estado-falso) estado_reserva="estado TEXT NOT NULL /* CHECK (estado IN ('PENDIENTE','CONFIRMADA','CANCELADA','RECHAZADA','NO_PRESENTADA')) */" ;;
    huespedes-exactamente-uno) regla_huespedes="CHECK (huespedes = 1)" ;;
    capacidad-exactamente-uno) regla_capacidad="CHECK (capacidad_max = 1)" ;;
    pago-exactamente-uno) regla_precio_pago="CHECK (monto_cents = 1)" ;;
    mensaje-solo-longitudes-extremas) regla_texto_mensaje="CHECK (length(texto) IN (1,1000))" ;;
    reserva-solo-un-dia) restriccion_reserva_intervalo=", CHECK (julianday(salida) - julianday(llegada) = 1)" ;;
    item-solo-una-noche) restriccion_intervalo=", CHECK (julianday(hasta) - julianday(desde) = 1)" ;;
    fase-indicador-solo-uno) regla_fase_indicador="CHECK (fase = 1)" ;;
    tarifa-solo-casos-basicos) regla_precio="CHECK (precio_cents IN (0,10))"; regla_estancia_min="CHECK (min_estancia IS NULL OR min_estancia = 1)"; regla_estancia_max="CHECK (max_estancia IS NULL OR max_estancia = 1)"; regla_estancia_orden="CHECK (max_estancia IS NULL OR min_estancia IS NULL OR min_estancia <= max_estancia)" ;;
    room-state-solo-activa) regla_estado_habitacion="CHECK (estado = 'ACTIVA')" ;;
    channel-solo-habitacion) regla_channel_mapping="CHECK (room_id IS NOT NULL)" ;;
    channel-solo-tipo) regla_channel_mapping="CHECK (room_type_id IS NOT NULL)" ;;
    categoria-solo-visitar) regla_categoria="CHECK (categoria = 'VISITAR')" ;;
    latitud-solo-extremos) regla_latitud="CHECK (latitud IN (-90,90))" ;;
    longitud-solo-extremos) regla_longitud="CHECK (longitud IN (-180,180))" ;;
    sin-unicidad) unicidad_reserva="" ;;
    unicidad-falsa) unicidad_reserva=", CHECK (idempotencia IS NOT NULL) /* UNIQUE (idempotencia, email) */" ;;
    sin-intervalo) restriccion_intervalo="" ;;
    sin-indice-intervalo) indice_intervalo="" ;;
    indice-parcial) indice_intervalo="CREATE INDEX idx_reservation_items_solape ON reservation_items(room_id, desde, hasta) WHERE 0;" ;;
    indice-expresion-falsa) indice_email="CREATE INDEX idx_usuarios_email ON usuarios(upper(email)) /* on usuarios(lower(email)) */;" ;;
    sin-fk-item-reserva) fk_reservation_items="reservation_id INTEGER NOT NULL" ;;
    sin-fk-item-habitacion) fk_reservation_room="room_id INTEGER NOT NULL" ;;
    sin-fk-historial) fk_reservation_history="reservation_id INTEGER NOT NULL" ;;
    sin-fk-bloque) fk_block_room="room_id INTEGER" ;;
    sin-fk-reservation-plan) fk_reservation_plan="rate_plan_id INTEGER" ;;
    sin-fk-reservation-user) fk_reservation_user="usuario_id INTEGER" ;;
    sin-fk-rates-plan) fk_rates_plan="rate_plan_id INTEGER NOT NULL" ;;
    sin-fk-rates-room-type) fk_rates_room_type="room_type_id INTEGER NOT NULL" ;;
    sin-fk-channel) fk_channel="channel_codigo TEXT NOT NULL" ;;
    sin-fk-channel-room) fk_channel_room="room_id INTEGER" ;;
    sin-fk-channel-room-type) fk_channel_room_type="room_type_id INTEGER" ;;
    sin-fk-channel-plan) fk_channel_plan="rate_plan_id INTEGER" ;;
    sin-fk-indicator) fk_indicator="clave TEXT NOT NULL" ;;
    sin-fk-room-amenities-type) fk_room_amenities_type="room_type_id INTEGER NOT NULL" ;;
    sin-fk-room-amenities-amenity) fk_room_amenities_amenity="amenidad_id INTEGER NOT NULL" ;;
    sin-fk-pago) fk_pago_reserva="reservation_id INTEGER NOT NULL" ;;
    sin-fk-mensaje) fk_mensaje_reserva="reservation_id INTEGER NOT NULL" ;;
    sin-id-reservation_items) id_reservation_items="" ;;
    sin-id-reservation_history) id_reservation_history="" ;;
    sin-id-pagos) id_pagos="" ;;
    sin-id-mensajes) id_mensajes="" ;;
    sin-unicidad-codigo) unicidad_codigo="" ;;
    sin-unicidad-users-email) unicidad_email_admin="" ;;
    sin-unicidad-rooms-codigo) unicidad_codigo_room="" ;;
    sin-unicidad-room-types-codigo) unicidad_codigo_room_type="" ;;
    sin-unicidad-rate-plans-codigo) unicidad_codigo_rate_plan="" ;;
    sin-unicidad-usuarios-google-sub) unicidad_google_sub="" ;;
    sin-unicidad-amenidades-codigo) unicidad_codigo_amenidad="" ;;
    sin-pk-hotel-config) clave_hotel_config="clave TEXT" ;;
    sin-pk-blocks) id_blocks="id INTEGER" ;;
    sin-pk-rates) id_rates="id INTEGER" ;;
    sin-pk-room-type-amenidades) pk_room_type_amenidades="" ;;
    sin-not-null-reservations-nombre) nombre_reserva="nombre TEXT" ;;
    sin-not-null-reservations-estado) notnull_estado_reserva="" ;;
    sin-not-null-reservations-codigo) notnull_codigo_reserva="" ;;
    sin-autoincrement-reservations) id_reservations="id INTEGER PRIMARY KEY," ;;
    id-text-reservations) id_reservations="id TEXT PRIMARY KEY," ;;
    sin-autoincrement-items) id_reservation_items="id INTEGER PRIMARY KEY," ;;
    sin-autoincrement-history) id_reservation_history="id INTEGER PRIMARY KEY," ;;
    sin-autoincrement-pagos) id_pagos="id INTEGER PRIMARY KEY," ;;
    sin-autoincrement-mensajes) id_mensajes="id INTEGER PRIMARY KEY," ;;
  esac
  if [ "$version" -ge 21 ]; then
    columna_categoria=", categoria TEXT NOT NULL DEFAULT 'VISITAR' $regla_categoria"
  fi
  sqlite3 "$archivo" <<SQL
CREATE TABLE flyway_schema_history (
  installed_rank INTEGER PRIMARY KEY,
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
ATTACH DATABASE '$FLYWAY_CHECKSUMS_DB' AS checksum_catalog;
WITH RECURSIVE versiones(version_num) AS (
  SELECT 1
  UNION ALL SELECT version_num + 1 FROM versiones WHERE version_num < $version
)
INSERT INTO flyway_schema_history
SELECT version_num, CAST(version_num AS TEXT),
  (SELECT description FROM checksum_catalog.migration_checksum WHERE version = CAST(version_num AS TEXT)), 'SQL',
  (SELECT script FROM checksum_catalog.migration_checksum WHERE version = CAST(version_num AS TEXT)),
  (SELECT checksum FROM checksum_catalog.migration_checksum WHERE version = CAST(version_num AS TEXT)),
  'hotel', CURRENT_TIMESTAMP, 1, 1
FROM versiones;
DETACH DATABASE checksum_catalog;
CREATE TABLE users (
  $id_users, email TEXT $unicidad_email_admin NOT NULL, hash TEXT NOT NULL,
  rol TEXT NOT NULL $regla_rol_usuario, activo INTEGER NOT NULL DEFAULT 1, creado_en TEXT NOT NULL,
  debe_cambiar_clave INTEGER NOT NULL DEFAULT 1, nombre TEXT NOT NULL DEFAULT ''
);
CREATE TABLE reservations (
  $id_reservations
  codigo TEXT$unicidad_codigo $notnull_codigo_reserva, email TEXT NOT NULL, $nombre_reserva,
  llegada TEXT NOT NULL, salida TEXT NOT NULL,
  huespedes INTEGER NOT NULL $regla_huespedes,
  ${estado_reserva%%NOT NULL*}$notnull_estado_reserva${estado_reserva#*NOT NULL}, $origen_reserva, idempotencia TEXT NOT NULL,
  creado_en TEXT NOT NULL, total_cents INTEGER, moneda TEXT,
  $fk_reservation_plan, $fk_reservation_user,
  hora_entrada TEXT, hora_salida TEXT, politica_cancelacion TEXT$unicidad_reserva$restriccion_reserva_intervalo
);
CREATE TABLE hotel_config ($clave_hotel_config, valor TEXT NOT NULL, actualizado_en TEXT NOT NULL DEFAULT '');
CREATE TABLE reservation_items (
  $id_reservation_items
  $fk_reservation_items,
  $fk_reservation_room, desde TEXT NOT NULL, hasta TEXT NOT NULL$restriccion_intervalo
);
$indice_intervalo
CREATE INDEX idx_reservations_llegada ON reservations(llegada, estado);
CREATE INDEX idx_reservations_usuario ON reservations(usuario_id);
CREATE TABLE reservation_history (
  $id_reservation_history
  $fk_reservation_history,
  estado_ant TEXT, estado_nuevo TEXT NOT NULL, detalle TEXT, actor TEXT NOT NULL, en TEXT NOT NULL
);
CREATE TABLE blocks ($id_blocks, $fk_block_room, desde TEXT NOT NULL, hasta TEXT NOT NULL, motivo TEXT NOT NULL DEFAULT '', CHECK (desde < hasta));
CREATE INDEX idx_blocks_solape ON blocks(room_id, desde, hasta);
CREATE TABLE rooms ($id_rooms, codigo TEXT $unicidad_codigo_room NOT NULL, room_type_id INTEGER REFERENCES room_types(id), estado TEXT NOT NULL DEFAULT 'ACTIVA' $regla_estado_habitacion, nombre TEXT NOT NULL DEFAULT '');
CREATE TABLE room_types ($id_room_types, codigo TEXT $unicidad_codigo_room_type NOT NULL, nombre TEXT NOT NULL, capacidad_max INTEGER NOT NULL $regla_capacidad);
CREATE TABLE rate_plans ($id_rate_plans, codigo TEXT $unicidad_codigo_rate_plan NOT NULL, nombre TEXT NOT NULL, moneda TEXT NOT NULL, activo INTEGER NOT NULL DEFAULT 1, descuento_pct INTEGER NOT NULL DEFAULT 0);
CREATE TABLE rates (
  $id_rates, $fk_rates_plan,
  $fk_rates_room_type, fecha TEXT NOT NULL, precio_cents INTEGER NOT NULL $regla_precio,
  min_estancia INTEGER $regla_estancia_min, max_estancia INTEGER $regla_estancia_max, cerrado INTEGER NOT NULL DEFAULT 0,
  UNIQUE (rate_plan_id, room_type_id, fecha),
  $regla_estancia_orden
);
CREATE INDEX idx_rates_busqueda ON rates(room_type_id, fecha);
CREATE TABLE channels (codigo TEXT PRIMARY KEY, nombre TEXT NOT NULL, activo INTEGER NOT NULL DEFAULT 1);
CREATE TABLE channel_mappings (
  $id_channel_mappings, $fk_channel, $fk_channel_room,
  $fk_channel_room_type, $fk_channel_plan,
  external_id TEXT NOT NULL, $regla_channel_mapping,
  UNIQUE (channel_codigo, external_id)
);
INSERT INTO channels(codigo,nombre,activo) VALUES ('WEB','Web propia',1),('BOOKING','Booking',0),('DESPEGAR','Despegar',0),('AIRBNB','Airbnb',0),('OTRO','Otro',0);
CREATE INDEX idx_channel_mappings_room ON channel_mappings(channel_codigo, room_id);
CREATE TABLE ota_syncs ($id_ota_syncs, channel_codigo TEXT NOT NULL, operacion TEXT NOT NULL, exitosa INTEGER NOT NULL, en TEXT NOT NULL);
CREATE INDEX idx_ota_syncs_canal ON ota_syncs(channel_codigo, en);
CREATE TABLE indicator_definitions (
  clave TEXT PRIMARY KEY, fase INTEGER NOT NULL $regla_fase_indicador, nombre TEXT NOT NULL, definicion TEXT NOT NULL, formula TEXT NOT NULL, fuente TEXT NOT NULL,
  unidad TEXT NOT NULL, periodo_por_defecto TEXT NOT NULL, activa INTEGER NOT NULL DEFAULT 1
);
CREATE TABLE indicator_results (
  $id_indicator_results, $fk_indicator, periodo TEXT NOT NULL,
  generado_en TEXT NOT NULL, UNIQUE (clave, periodo)
);
CREATE INDEX idx_indicator_results_clave ON indicator_results(clave, periodo);
CREATE TABLE adoption_activities ($id_adoption_activities, tipo TEXT NOT NULL, descripcion TEXT NOT NULL, fecha TEXT NOT NULL, con_datos_de_origen INTEGER NOT NULL DEFAULT 0);
CREATE INDEX idx_adoption_activities_fecha ON adoption_activities(fecha);
CREATE TABLE admin_actions ($id_admin_actions, actor TEXT NOT NULL, metodo TEXT NOT NULL, ruta TEXT NOT NULL, estado INTEGER NOT NULL, en TEXT NOT NULL);
CREATE TABLE usuarios ($id_usuarios, google_sub TEXT $unicidad_google_sub NOT NULL, email TEXT NOT NULL, nombre TEXT NOT NULL, creado_en TEXT NOT NULL);
$indice_email
CREATE TABLE pagos (
  $id_pagos
  $fk_pago_reserva,
  monto_cents INTEGER NOT NULL $regla_precio_pago, moneda TEXT NOT NULL,
  concepto TEXT NOT NULL DEFAULT '', actor TEXT NOT NULL, creado_en TEXT NOT NULL,
  anulado_en TEXT, anulado_por TEXT
);
CREATE INDEX idx_pagos_reserva ON pagos(reservation_id);
CREATE TABLE amenidades ($id_amenidades, codigo TEXT $unicidad_codigo_amenidad NOT NULL, nombre TEXT NOT NULL);
CREATE TABLE room_type_amenidades (
  $fk_room_amenities_type,
  $fk_room_amenities_amenity$pk_room_type_amenidades
);
CREATE TABLE lugares_interes (
  $id_lugares, nombre TEXT NOT NULL, descripcion TEXT NOT NULL DEFAULT '', latitud REAL NOT NULL $regla_latitud, longitud REAL NOT NULL $regla_longitud,
  activo INTEGER NOT NULL DEFAULT 1, creado_en TEXT NOT NULL$columna_categoria
);
CREATE TABLE mensajes (
  $id_mensajes
  $fk_mensaje_reserva,
  autor TEXT NOT NULL CHECK (autor IN ('HUESPED','HOTEL')),
  texto TEXT NOT NULL $regla_texto_mensaje,
  creado_en TEXT NOT NULL, visto INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_mensajes_reserva ON mensajes(reservation_id);
INSERT INTO reservations (codigo,email,llegada,salida,huespedes,estado,origen,idempotencia,creado_en)
VALUES ('$codigo','hotel@example.test','2026-10-11','2026-10-12',1,'$estado_semilla','$origen_semilla','id-$codigo',CURRENT_TIMESTAMP);
SQL
  case "$variante" in
    flyway-tipo-no-admitido)
      sqlite3 -bail "$archivo" "INSERT INTO flyway_schema_history (installed_rank,version,description,type,script,checksum,installed_by,installed_on,execution_time,success) VALUES (22,'22','Java migration','JDBC','V22__java.java',NULL,'hotel',CURRENT_TIMESTAMP,1,1);"
      ;;
    flyway-sql-sin-version)
      sqlite3 -bail "$archivo" "UPDATE flyway_schema_history SET version=NULL WHERE version='21';"
      ;;
    flyway-sin-pk-rank)
      sqlite3 -bail "$archivo" <<'SQL'
ALTER TABLE flyway_schema_history RENAME TO flyway_schema_history_con_pk;
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
INSERT INTO flyway_schema_history SELECT * FROM flyway_schema_history_con_pk;
DROP TABLE flyway_schema_history_con_pk;
SQL
      ;;
  esac
}

aplicar_migracion_real() {
  archivo="$1"
  migracion="$2"
  nombre="${migracion##*/}"
  version_aplicada="${nombre#V}"
  version_aplicada="${version_aplicada%%__*}"
  { printf 'PRAGMA foreign_keys=OFF;\n.read %s\n' "$migracion"; } | sqlite3 -bail "$archivo"
  sqlite3 -bail "$archivo" "ATTACH DATABASE '$FLYWAY_CHECKSUMS_DB' AS manifest; INSERT INTO flyway_schema_history (installed_rank,version,description,type,script,checksum,installed_by,installed_on,execution_time,success) SELECT COALESCE((SELECT MAX(installed_rank)+1 FROM flyway_schema_history),1),version,description,'SQL',script,checksum,'hotel-test',CURRENT_TIMESTAMP,0,1 FROM manifest.migration_checksum WHERE version='$version_aplicada'; DETACH DATABASE manifest;"
  violaciones_fk="$(sqlite3 -separator '|' "$archivo" 'PRAGMA foreign_key_check;')"
  [ -z "$violaciones_fk" ] || fallo "la migración $nombre dejó relaciones huérfanas: $violaciones_fk"
}

crear_base_hotel_real() {
  archivo="$1"
  codigo="$2"
  version_objetivo="${3:-21}"
  variante="${4:-}"
  rm -f "$archivo"
  sqlite3 -bail "$archivo" <<'SQL'
CREATE TABLE flyway_schema_history (
  installed_rank INTEGER NOT NULL PRIMARY KEY,
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
CREATE INDEX flyway_schema_history_s_idx ON flyway_schema_history(success);
SQL
  version_migracion=1
  while [ "$version_migracion" -le "$version_objetivo" ]; do
    for migracion in "$FLYWAY_MIGRATIONS_DIR"/V"$version_migracion"__*.sql; do
      [ -f "$migracion" ] || continue
      aplicar_migracion_real "$archivo" "$migracion"
    done
    version_migracion=$((version_migracion + 1))
  done

  case "$variante" in
    sin-id-reservation_items)
      sqlite3 -bail "$archivo" <<SQL
PRAGMA foreign_keys=OFF;
ALTER TABLE reservation_items RENAME TO reservation_items_con_id;
CREATE TABLE reservation_items (
  reservation_id INTEGER NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
  room_id INTEGER NOT NULL REFERENCES rooms(id),
  desde TEXT NOT NULL,
  hasta TEXT NOT NULL,
  CHECK (desde < hasta)
);
INSERT INTO reservation_items(reservation_id,room_id,desde,hasta)
  SELECT reservation_id,room_id,desde,hasta FROM reservation_items_con_id;
DROP TABLE reservation_items_con_id;
CREATE INDEX idx_reservation_items_solape ON reservation_items(room_id,desde,hasta);
PRAGMA foreign_keys=ON;
SQL
      ;;
    sin-id-reservation_history)
      if [ "$version_objetivo" -ge 11 ]; then detalle_historial="detalle TEXT,"; else detalle_historial=""; fi
      sqlite3 -bail "$archivo" <<SQL
PRAGMA foreign_keys=OFF;
ALTER TABLE reservation_history RENAME TO reservation_history_con_id;
CREATE TABLE reservation_history (
  reservation_id INTEGER NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
  estado_ant TEXT,
  estado_nuevo TEXT NOT NULL,
  $detalle_historial
  actor TEXT NOT NULL,
  en TEXT NOT NULL
);
INSERT INTO reservation_history(reservation_id,estado_ant,estado_nuevo,actor,en)
  SELECT reservation_id,estado_ant,estado_nuevo,actor,en FROM reservation_history_con_id;
DROP TABLE reservation_history_con_id;
PRAGMA foreign_keys=ON;
SQL
      ;;
    sin-estado-ant-reservation_history)
      if [ "$version_objetivo" -ge 11 ]; then detalle_historial="detalle TEXT,"; else detalle_historial=""; fi
      sqlite3 -bail "$archivo" <<SQL
PRAGMA foreign_keys=OFF;
ALTER TABLE reservation_history RENAME TO reservation_history_con_estado_ant;
CREATE TABLE reservation_history (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  reservation_id INTEGER NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
  estado_nuevo TEXT NOT NULL,
  $detalle_historial
  actor TEXT NOT NULL,
  en TEXT NOT NULL
);
INSERT INTO reservation_history(id,reservation_id,estado_nuevo,actor,en)
  SELECT id,reservation_id,estado_nuevo,actor,en FROM reservation_history_con_estado_ant;
DROP TABLE reservation_history_con_estado_ant;
PRAGMA foreign_keys=ON;
SQL
      ;;
    sin-fk-mensaje)
      sqlite3 -bail "$archivo" <<'SQL'
PRAGMA foreign_keys=OFF;
ALTER TABLE mensajes RENAME TO mensajes_con_fk;
CREATE TABLE mensajes (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  reservation_id INTEGER NOT NULL,
  autor TEXT NOT NULL CHECK (autor IN ('HUESPED','HOTEL')),
  texto TEXT NOT NULL CHECK (length(texto) >= 1 AND length(texto) <= 1000),
  creado_en TEXT NOT NULL,
  visto INTEGER NOT NULL DEFAULT 0
);
INSERT INTO mensajes(id,reservation_id,autor,texto,creado_en,visto)
  SELECT id,reservation_id,autor,texto,creado_en,visto FROM mensajes_con_fk;
DROP TABLE mensajes_con_fk;
CREATE INDEX idx_mensajes_reserva ON mensajes(reservation_id);
PRAGMA foreign_keys=ON;
SQL
      ;;
  esac

  sqlite3 -bail "$archivo" "INSERT INTO reservations(codigo,email,nombre,llegada,salida,huespedes,estado,origen,idempotencia,creado_en) VALUES ('$codigo','hotel@example.invalid','Prueba','2026-10-11','2026-10-12',1,'PENDIENTE','WEB','id-$codigo',CURRENT_TIMESTAMP);"
}

migrar_base_hotel_real() {
  archivo="$1"
  desde="${2:-1}"
  hasta="${3:-21}"
  version_migracion=$((desde + 1))
  while [ "$version_migracion" -le "$hasta" ]; do
    for migracion in "$FLYWAY_MIGRATIONS_DIR"/V"$version_migracion"__*.sql; do
      [ -f "$migracion" ] || continue
      aplicar_migracion_real "$archivo" "$migracion"
    done
    version_migracion=$((version_migracion + 1))
  done
}

echo "=== 1. Una restauración válida conserva su esquema y sus reservas ==="
BUENO="$TEMP_DIR/hotel-valido.sqlite3"
DESTINO_VALIDO="$TEMP_DIR/destino-valido.sqlite3"
crear_base_hotel "$BUENO" "H-SINTETICA"
chown 100:101 "$BUENO"
BUENO_ANTES="$(huella "$BUENO")"
chmod 440 "$BUENO"
sqlite3 "$DESTINO_VALIDO" "CREATE TABLE anterior (valor TEXT); INSERT INTO anterior VALUES ('reemplazable');"
HOTEL_RESTORE_BIN="$RESTAURAR" HOTEL_RESTORE_DEST="$DESTINO_VALIDO" HOTEL_RESTORE_SOURCE="$BUENO" \
  su -p -s /bin/sh -c '"$HOTEL_RESTORE_BIN" "$HOTEL_RESTORE_DEST" "$HOTEL_RESTORE_SOURCE"' hotel
[ "$(huella "$BUENO")" = "$BUENO_ANTES" ] \
  || fallo "la validación modificó el archivo original del respaldo"
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
echo "=== 10. Un checksum Flyway incorrecto se rechaza sin tocar el destino ==="
CHECKSUM_MALO="$TEMP_DIR/hotel-checksum-incorrecto.sqlite3"
DESTINO_CHECKSUM_MALO="$TEMP_DIR/destino-checksum-incorrecto.sqlite3"
crear_base_hotel "$CHECKSUM_MALO" "H-CHECKSUM-MALO"
sqlite3 "$CHECKSUM_MALO" "UPDATE flyway_schema_history SET checksum=checksum+1 WHERE version='9' AND type='SQL';"
sqlite3 "$DESTINO_CHECKSUM_MALO" "CREATE TABLE sentinel (valor TEXT); INSERT INTO sentinel VALUES ('intacto');"
CHECKSUM_ANTES="$(huella "$DESTINO_CHECKSUM_MALO")"
set +e
SALIDA="$("$RESTAURAR" "$DESTINO_CHECKSUM_MALO" "$CHECKSUM_MALO" 2>&1)"
CODIGO=$?
set -e
[ "$CODIGO" -ne 0 ] || fallo "se aceptó un checksum distinto al de V9: $SALIDA"
[ "$(huella "$DESTINO_CHECKSUM_MALO")" = "$CHECKSUM_ANTES" ] \
  || fallo "el destino cambió ante un checksum Flyway incorrecto"
echo "checksum Flyway incorrecto rechazado; destino intacto"

MANIFIESTO_MALO="$TEMP_DIR/flyway-checksums-malformado.sqlite3"
DESTINO_MANIFIESTO_MALO="$TEMP_DIR/destino-manifiesto-malformado.sqlite3"
sqlite3 "$FLYWAY_CHECKSUMS_DB" ".backup '$MANIFIESTO_MALO'"
sqlite3 "$MANIFIESTO_MALO" "UPDATE migration_checksum SET checksum='12-34' WHERE version='1';"
sqlite3 "$DESTINO_MANIFIESTO_MALO" "CREATE TABLE sentinel (valor TEXT); INSERT INTO sentinel VALUES ('intacto');"
MANIFIESTO_ANTES="$(huella "$DESTINO_MANIFIESTO_MALO")"
set +e
SALIDA="$(FLYWAY_CHECKSUMS_DB="$MANIFIESTO_MALO" "$RESTAURAR" "$DESTINO_MANIFIESTO_MALO" "$BUENO" 2>&1)"
CODIGO=$?
set -e
[ "$CODIGO" -ne 0 ] || fallo "se aceptó un checksum malformado del manifiesto: $SALIDA"
case "$SALIDA" in
  *"no define un checksum válido"*) ;;
  *) fallo "el checksum malformado no produjo un error de formato claro: $SALIDA" ;;
esac
[ "$(huella "$DESTINO_MANIFIESTO_MALO")" = "$MANIFIESTO_ANTES" ] \
  || fallo "el destino cambió ante un checksum malformado del manifiesto"
echo "checksum malformado del manifiesto rechazado; destino intacto"

for campo_metadata in description script; do
  METADATA_MALO="$TEMP_DIR/hotel-metadata-$campo_metadata.sqlite3"
  DESTINO_METADATA_MALO="$TEMP_DIR/destino-metadata-$campo_metadata.sqlite3"
  crear_base_hotel "$METADATA_MALO" "H-METADATA-$campo_metadata"
  sqlite3 "$METADATA_MALO" "UPDATE flyway_schema_history SET $campo_metadata='alterado' WHERE version='9' AND type='SQL';"
  sqlite3 "$DESTINO_METADATA_MALO" "CREATE TABLE sentinel (valor TEXT); INSERT INTO sentinel VALUES ('intacto');"
  METADATA_ANTES="$(huella "$DESTINO_METADATA_MALO")"
  set +e
  SALIDA="$("$RESTAURAR" "$DESTINO_METADATA_MALO" "$METADATA_MALO" 2>&1)"
  CODIGO=$?
  set -e
  [ "$CODIGO" -ne 0 ] || fallo "se aceptó metadata Flyway alterada ($campo_metadata): $SALIDA"
  [ "$(huella "$DESTINO_METADATA_MALO")" = "$METADATA_ANTES" ] \
    || fallo "el destino cambió ante metadata Flyway alterada ($campo_metadata)"
done
echo "description y script divergentes rechazados; destino intacto"

echo
echo "=== 11. Un historial Flyway válido con baseline v1 se conserva ==="
BASELINE="$TEMP_DIR/hotel-baseline-v21.sqlite3"
DESTINO_BASELINE="$TEMP_DIR/destino-baseline-v21.sqlite3"
crear_base_hotel_real "$BASELINE" "H-BASELINE" 21
sqlite3 "$BASELINE" "UPDATE flyway_schema_history SET description='<< Flyway Baseline >>', type='BASELINE', script='<< Flyway Baseline >>', checksum=NULL, execution_time=0 WHERE version='1' AND type='SQL';"
SALIDA="$("$RESTAURAR" "$DESTINO_BASELINE" "$BASELINE" 2>&1)" \
  || fallo "se rechazó un historial válido con baseline v1: $SALIDA"
[ "$(sqlite3 "$DESTINO_BASELINE" "SELECT COUNT(*) FROM flyway_schema_history WHERE type='BASELINE' AND version='1';")" = "1" ] \
  || fallo "el backup con baseline no conservó su historial"
echo "baseline v1 con migraciones SQL v2-v21 restaurado"

BASELINE_V2="$TEMP_DIR/hotel-baseline-v2.sqlite3"
DESTINO_BASELINE_V2="$TEMP_DIR/destino-baseline-v2.sqlite3"
crear_base_hotel_real "$BASELINE_V2" "H-BASELINE-V2" 21
sqlite3 "$BASELINE_V2" <<'SQL'
UPDATE flyway_schema_history SET description='<< Flyway Baseline >>', type='BASELINE', script='<< Flyway Baseline >>', checksum=NULL, execution_time=0 WHERE version='1' AND type='SQL';
UPDATE flyway_schema_history SET version='2' WHERE type='BASELINE';
DELETE FROM flyway_schema_history WHERE version='2' AND type='SQL';
SQL
sqlite3 "$DESTINO_BASELINE_V2" "CREATE TABLE sentinel (valor TEXT); INSERT INTO sentinel VALUES ('intacto');"
BASELINE_V2_ANTES="$(huella "$DESTINO_BASELINE_V2")"
set +e
SALIDA="$("$RESTAURAR" "$DESTINO_BASELINE_V2" "$BASELINE_V2" 2>&1)"
CODIGO=$?
set -e
[ "$CODIGO" -ne 0 ] || fallo "se aceptó un baseline fuera de la versión configurada: $SALIDA"
[ "$(huella "$DESTINO_BASELINE_V2")" = "$BASELINE_V2_ANTES" ] \
  || fallo "el destino cambió ante un baseline fuera de la versión configurada"
echo "baseline v2 rechazado; destino intacto"

echo
echo "=== 12. Un historial con una migración versionada ausente se rechaza ==="
HISTORIAL_INCOMPLETO="$TEMP_DIR/hotel-sin-migracion-v9.sqlite3"
DESTINO_HISTORIAL_INCOMPLETO="$TEMP_DIR/destino-historial-incompleto.sqlite3"
crear_base_hotel "$HISTORIAL_INCOMPLETO" "H-SIN-V9"
sqlite3 "$HISTORIAL_INCOMPLETO" "DELETE FROM flyway_schema_history WHERE version='9';"
sqlite3 "$DESTINO_HISTORIAL_INCOMPLETO" "CREATE TABLE sentinel (valor TEXT); INSERT INTO sentinel VALUES ('intacto');"
HISTORIAL_ANTES="$(huella "$DESTINO_HISTORIAL_INCOMPLETO")"
set +e
SALIDA="$("$RESTAURAR" "$DESTINO_HISTORIAL_INCOMPLETO" "$HISTORIAL_INCOMPLETO" 2>&1)"
CODIGO=$?
set -e
[ "$CODIGO" -ne 0 ] || fallo "se aceptó una base v21 sin la migración V9: $SALIDA"
[ "$(huella "$DESTINO_HISTORIAL_INCOMPLETO")" = "$HISTORIAL_ANTES" ] \
  || fallo "el destino cambió ante una migración versionada ausente"
echo "migración V9 ausente rechazada; destino intacto"

echo
echo "=== 13. Una migración SQL duplicada se rechaza sin tocar el destino ==="
DUPLICADO="$TEMP_DIR/hotel-migracion-duplicada.sqlite3"
DESTINO_DUPLICADO="$TEMP_DIR/destino-migracion-duplicada.sqlite3"
crear_base_hotel "$DUPLICADO" "H-DUPLICADO"
sqlite3 "$DUPLICADO" "INSERT INTO flyway_schema_history SELECT 100, version, description, type, script, checksum, installed_by, installed_on, execution_time, success FROM flyway_schema_history WHERE version='9' AND type='SQL';"
sqlite3 "$DESTINO_DUPLICADO" "CREATE TABLE sentinel (valor TEXT); INSERT INTO sentinel VALUES ('intacto');"
DUPLICADO_ANTES="$(huella "$DESTINO_DUPLICADO")"
set +e
SALIDA="$("$RESTAURAR" "$DESTINO_DUPLICADO" "$DUPLICADO" 2>&1)"
CODIGO=$?
set -e
[ "$CODIGO" -ne 0 ] || fallo "se aceptó una migración SQL duplicada: $SALIDA"
[ "$(huella "$DESTINO_DUPLICADO")" = "$DUPLICADO_ANTES" ] \
  || fallo "el destino cambió ante una migración duplicada"
echo "migración SQL v9 duplicada rechazada; destino intacto"

echo
echo "=== 14. Restricciones e índices críticos son obligatorios en v21 ==="
for variante in origen-antiguo estado-antiguo estado-falso sin-unicidad sin-unicidad-codigo id-text-reservations unicidad-falsa sin-intervalo \
  sin-indice-intervalo indice-parcial indice-expresion-falsa \
  sin-fk-item-reserva sin-fk-item-habitacion sin-fk-historial sin-fk-bloque \
  sin-fk-reservation-plan sin-fk-reservation-user sin-fk-rates-plan sin-fk-rates-room-type \
  sin-fk-channel sin-fk-channel-room sin-fk-channel-room-type sin-fk-channel-plan \
  sin-fk-indicator sin-fk-room-amenities-type sin-fk-room-amenities-amenity \
  sin-fk-pago sin-fk-mensaje; do
  RESPALDO_REGLA="$TEMP_DIR/hotel-regla-$variante.sqlite3"
  DESTINO_REGLA="$TEMP_DIR/destino-regla-$variante.sqlite3"
  crear_base_hotel "$RESPALDO_REGLA" "H-REGLA-$variante" 21 "$variante"
  sqlite3 "$DESTINO_REGLA" "CREATE TABLE sentinel (valor TEXT); INSERT INTO sentinel VALUES ('intacto');"
  REGLA_ANTES="$(huella "$DESTINO_REGLA")"
  set +e
  SALIDA="$("$RESTAURAR" "$DESTINO_REGLA" "$RESPALDO_REGLA" 2>&1)"
  CODIGO=$?
  set -e
  [ "$CODIGO" -ne 0 ] || fallo "se aceptó una base v21 sin la regla $variante: $SALIDA"
  [ "$(huella "$DESTINO_REGLA")" = "$REGLA_ANTES" ] \
    || fallo "el destino cambió ante la ausencia de la regla $variante"
done
echo "se rechazaron reglas CHECK ausentes o falsas, unicidad ausente o falsa, índices parciales o expresión distinta y 17 claves foráneas ausentes"

echo
echo "=== 14b. Las sondas positivas detectan CHECK demasiado restrictivos ==="
for variante in estado-solo-no-presentada origen-solo-otro capacidad-exactamente-uno \
  pago-exactamente-uno mensaje-solo-longitudes-extremas \
  item-solo-una-noche tarifa-solo-casos-basicos room-state-solo-activa \
  channel-solo-habitacion channel-solo-tipo categoria-solo-visitar \
  latitud-solo-extremos longitud-solo-extremos fase-indicador-solo-uno; do
  RESPALDO_RESTRICTIVO="$TEMP_DIR/hotel-regla-restrictiva-$variante.sqlite3"
  DESTINO_RESTRICTIVO="$TEMP_DIR/destino-regla-restrictiva-$variante.sqlite3"
  crear_base_hotel "$RESPALDO_RESTRICTIVO" "H-RESTRICTIVA-$variante" 21 "$variante"
  sqlite3 "$DESTINO_RESTRICTIVO" "CREATE TABLE sentinel (valor TEXT); INSERT INTO sentinel VALUES ('intacto');"
  RESTRICTIVO_ANTES="$(huella "$DESTINO_RESTRICTIVO")"
  set +e
  SALIDA="$("$RESTAURAR" "$DESTINO_RESTRICTIVO" "$RESPALDO_RESTRICTIVO" 2>&1)"
  CODIGO=$?
  set -e
  [ "$CODIGO" -ne 0 ] || fallo "se aceptó una regla semántica demasiado restrictiva: $variante"
  case "$SALIDA" in
    *"rechaza un dato permitido por la regla"*) ;;
    *) fallo "$variante se rechazó por un motivo distinto a las sondas positivas: $SALIDA" ;;
  esac
  [ "$(huella "$DESTINO_RESTRICTIVO")" = "$RESTRICTIVO_ANTES" ] \
    || fallo "el destino cambió ante la regla demasiado restrictiva $variante"
done

echo
echo "=== 14c. Las tablas históricas conservan AUTOINCREMENT ==="
for variante_tabla in \
  sin-autoincrement-reservations:reservations \
  sin-autoincrement-items:reservation_items \
  sin-autoincrement-history:reservation_history \
  sin-autoincrement-pagos:pagos \
  sin-autoincrement-mensajes:mensajes; do
  variante="${variante_tabla%%:*}"
  tabla="${variante_tabla#*:}"
  RESPALDO_AUTOINCREMENT="$TEMP_DIR/hotel-$variante.sqlite3"
  DESTINO_AUTOINCREMENT="$TEMP_DIR/destino-$variante.sqlite3"
  crear_base_hotel "$RESPALDO_AUTOINCREMENT" "H-$variante" 21 "$variante"
  sqlite3 "$DESTINO_AUTOINCREMENT" "CREATE TABLE sentinel (valor TEXT); INSERT INTO sentinel VALUES ('intacto');"
  AUTOINCREMENT_ANTES="$(huella "$DESTINO_AUTOINCREMENT")"
  set +e
  SALIDA="$("$RESTAURAR" "$DESTINO_AUTOINCREMENT" "$RESPALDO_AUTOINCREMENT" 2>&1)"
  CODIGO=$?
  set -e
  [ "$CODIGO" -ne 0 ] || fallo "se aceptó $tabla sin AUTOINCREMENT"
  case "$SALIDA" in
    *"AUTOINCREMENT en $tabla"*) ;;
    *) fallo "$tabla se rechazó por una causa distinta a AUTOINCREMENT: $SALIDA" ;;
  esac
  [ "$(huella "$DESTINO_AUTOINCREMENT")" = "$AUTOINCREMENT_ANTES" ] \
    || fallo "el destino cambió ante la ausencia de AUTOINCREMENT en $tabla"
done
echo "reservations, reservation_items, reservation_history, pagos y mensajes requieren AUTOINCREMENT; destinos intactos"

echo
echo "=== 14d. El historial Flyway solo admite filas versionadas SQL y baseline v1 ==="
for variante_historial in flyway-tipo-no-admitido flyway-sql-sin-version flyway-sin-pk-rank; do
  RESPALDO_HISTORIAL="$TEMP_DIR/hotel-$variante_historial.sqlite3"
  DESTINO_HISTORIAL="$TEMP_DIR/destino-$variante_historial.sqlite3"
  crear_base_hotel "$RESPALDO_HISTORIAL" "H-$variante_historial" 21 "$variante_historial"
  sqlite3 "$DESTINO_HISTORIAL" "CREATE TABLE sentinel (valor TEXT); INSERT INTO sentinel VALUES ('intacto');"
  HISTORIAL_ANTES="$(huella "$DESTINO_HISTORIAL")"
  set +e
  SALIDA="$("$RESTAURAR" "$DESTINO_HISTORIAL" "$RESPALDO_HISTORIAL" 2>&1)"
  CODIGO=$?
  set -e
  [ "$CODIGO" -ne 0 ] || fallo "se aceptó el historial Flyway inválido: $variante_historial"
  case "$variante_historial:$SALIDA" in
    flyway-tipo-no-admitido:*"tipo de migración Flyway no admitido"*) ;;
    flyway-sql-sin-version:*"registra una migración SQL Flyway con versión SQL ilegible"*) ;;
    flyway-sin-pk-rank:*"no conserva INTEGER PRIMARY KEY en flyway_schema_history.installed_rank"*) ;;
    *) fallo "$variante_historial se rechazó por una causa distinta a la anomalía del historial: $SALIDA" ;;
  esac
  [ "$(huella "$DESTINO_HISTORIAL")" = "$HISTORIAL_ANTES" ] \
    || fallo "el destino cambió ante el historial Flyway inválido $variante_historial"
done
echo "tipos de migración inesperados, SQL sin versión y installed_rank sin clave primaria rechazados; destino intacto"

echo
echo "=== 14e. Claves e identificadores del esquema son obligatorios ==="
for caso_clave in \
  sin-unicidad-users-email:users:email:1:UNIQUE \
  sin-unicidad-rooms-codigo:rooms:codigo:21:UNIQUE \
  sin-unicidad-room-types-codigo:room_types:codigo:21:UNIQUE \
  sin-unicidad-rate-plans-codigo:rate_plans:codigo:21:UNIQUE \
  sin-unicidad-usuarios-google-sub:usuarios:google_sub:21:UNIQUE \
  sin-unicidad-amenidades-codigo:amenidades:codigo:21:UNIQUE \
  sin-pk-hotel-config:hotel_config:clave:1:TEXT \
  sin-pk-blocks:blocks:id:21:INTEGER \
  sin-pk-rates:rates:id:21:INTEGER \
  sin-pk-room-type-amenidades:room_type_amenidades:room_type_id,amenidad_id:21:COMPOSITE; do
  variante_clave="${caso_clave%%:*}"
  restante_clave="${caso_clave#*:}"
  tabla_clave="${restante_clave%%:*}"
  restante_clave="${restante_clave#*:}"
  columnas_clave="${restante_clave%%:*}"
  restante_clave="${restante_clave#*:}"
  version_clave="${restante_clave%%:*}"
  tipo_clave="${restante_clave#*:}"
  RESPALDO_CLAVE="$TEMP_DIR/hotel-$variante_clave.sqlite3"
  DESTINO_CLAVE="$TEMP_DIR/destino-$variante_clave.sqlite3"
  crear_base_hotel "$RESPALDO_CLAVE" "H-$variante_clave" "$version_clave" "$variante_clave"
  sqlite3 "$DESTINO_CLAVE" "CREATE TABLE sentinel (valor TEXT); INSERT INTO sentinel VALUES ('intacto');"
  CLAVE_ANTES="$(huella "$DESTINO_CLAVE")"
  set +e
  SALIDA="$("$RESTAURAR" "$DESTINO_CLAVE" "$RESPALDO_CLAVE" 2>&1)"
  CODIGO=$?
  set -e
  [ "$CODIGO" -ne 0 ] || fallo "se aceptó la ausencia de clave $tabla_clave.$columnas_clave"
  if [ "$tipo_clave" = "UNIQUE" ]; then
    esperado_clave="no conserva unicidad en $tabla_clave($columnas_clave)"
  elif [ "$tipo_clave" = "COMPOSITE" ]; then
    esperado_clave="no conserva la clave primaria compuesta ($columnas_clave) en $tabla_clave"
  elif [ -n "$tipo_clave" ]; then
    esperado_clave="no conserva $tipo_clave PRIMARY KEY en $tabla_clave.$columnas_clave"
  else
    esperado_clave="no conserva unicidad en $tabla_clave($columnas_clave)"
  fi
  case "$SALIDA" in *"$esperado_clave"*) ;; *) fallo "$variante_clave se rechazó por una causa distinta a $esperado_clave: $SALIDA" ;; esac
  [ "$(huella "$DESTINO_CLAVE")" = "$CLAVE_ANTES" ] \
    || fallo "el destino cambió ante la ausencia de $tabla_clave.$columnas_clave"
done
echo "unicidad de acceso/códigos OAuth e inventario, PK simples y compuesta rechazadas al faltar; destino intacto"

echo
echo "=== 14f. Las columnas obligatorias conservan NOT NULL por versión ==="
for caso_notnull in reservations-estado reservations-codigo reservations-nombre-v1; do
  case "$caso_notnull" in
    reservations-estado) variante_notnull="sin-not-null-reservations-estado"; version_notnull=21; columna_notnull=estado ;;
    reservations-codigo) variante_notnull="sin-not-null-reservations-codigo"; version_notnull=21; columna_notnull=codigo ;;
    reservations-nombre-v1) variante_notnull="sin-not-null-reservations-nombre"; version_notnull=5; columna_notnull=nombre ;;
  esac
  RESPALDO_NOTNULL="$TEMP_DIR/hotel-$variante_notnull.sqlite3"
  DESTINO_NOTNULL="$TEMP_DIR/destino-$variante_notnull.sqlite3"
  crear_base_hotel "$RESPALDO_NOTNULL" "H-$variante_notnull" "$version_notnull" "$variante_notnull"
  sqlite3 "$DESTINO_NOTNULL" "CREATE TABLE sentinel (valor TEXT); INSERT INTO sentinel VALUES ('intacto');"
  NOTNULL_ANTES="$(huella "$DESTINO_NOTNULL")"
  set +e
  SALIDA="$("$RESTAURAR" "$DESTINO_NOTNULL" "$RESPALDO_NOTNULL" 2>&1)"
  CODIGO=$?
  set -e
  [ "$CODIGO" -ne 0 ] || fallo "se aceptó reservations.$columna_notnull nullable en v$version_notnull"
  case "$SALIDA" in
    *"no conserva NOT NULL en: reservations.$columna_notnull"*) ;;
    *) fallo "reservations.$columna_notnull nullable se rechazó por una causa distinta: $SALIDA" ;;
  esac
  [ "$(huella "$DESTINO_NOTNULL")" = "$NOTNULL_ANTES" ] \
    || fallo "el destino cambió ante reservations.$columna_notnull nullable en v$version_notnull"
done
echo "reservations.estado/codigo y nombre obligatorio hasta v5 rechazan NULL antes de sustituir; destino intacto"

RESPALDO_INTERVALO_RESTRICTIVO="$TEMP_DIR/hotel-regla-restrictiva-reserva-una-noche.sqlite3"
DESTINO_INTERVALO_RESTRICTIVO="$TEMP_DIR/destino-regla-restrictiva-reserva-una-noche.sqlite3"
crear_base_hotel "$RESPALDO_INTERVALO_RESTRICTIVO" "H-RESTRICTIVA-UNA-NOCHE" 5 reserva-solo-un-dia
sqlite3 "$DESTINO_INTERVALO_RESTRICTIVO" "CREATE TABLE sentinel (valor TEXT); INSERT INTO sentinel VALUES ('intacto');"
INTERVALO_RESTRICTIVO_ANTES="$(huella "$DESTINO_INTERVALO_RESTRICTIVO")"
set +e
SALIDA="$("$RESTAURAR" "$DESTINO_INTERVALO_RESTRICTIVO" "$RESPALDO_INTERVALO_RESTRICTIVO" 2>&1)"
CODIGO=$?
set -e
[ "$CODIGO" -ne 0 ] || fallo "se aceptó una regla que solo permite reservas de una noche"
case "$SALIDA" in
  *"rechaza un dato permitido por la regla intervalo de reserva"*) ;;
  *) fallo "la reserva de una noche se rechazó por un motivo distinto a la sonda positiva: $SALIDA" ;;
esac
[ "$(huella "$DESTINO_INTERVALO_RESTRICTIVO")" = "$INTERVALO_RESTRICTIVO_ANTES" ] \
  || fallo "el destino cambió ante la regla que solo permite reservas de una noche"
echo "se rechazaron 14 CHECK demasiado restrictivos con sondas positivas; destinos intactos"

RESPALDO_HUESPED_RESTRICTIVO="$TEMP_DIR/hotel-regla-restrictiva-huespedes-una-persona.sqlite3"
DESTINO_HUESPED_RESTRICTIVO="$TEMP_DIR/destino-regla-restrictiva-huespedes-una-persona.sqlite3"
crear_base_hotel "$RESPALDO_HUESPED_RESTRICTIVO" "H-RESTRICTIVA-HUESPEDES" 5 huespedes-exactamente-uno
sqlite3 "$DESTINO_HUESPED_RESTRICTIVO" "CREATE TABLE sentinel (valor TEXT); INSERT INTO sentinel VALUES ('intacto');"
HUESPED_RESTRICTIVO_ANTES="$(huella "$DESTINO_HUESPED_RESTRICTIVO")"
set +e
SALIDA="$("$RESTAURAR" "$DESTINO_HUESPED_RESTRICTIVO" "$RESPALDO_HUESPED_RESTRICTIVO" 2>&1)"
CODIGO=$?
set -e
[ "$CODIGO" -ne 0 ] || fallo "se aceptó una regla que solo permite reservas de una persona"
case "$SALIDA" in
  *"rechaza un dato permitido por la regla varias personas en la reserva"*) ;;
  *) fallo "el huésped=1 se rechazó por un motivo distinto a la sonda positiva: $SALIDA" ;;
esac
[ "$(huella "$DESTINO_HUESPED_RESTRICTIVO")" = "$HUESPED_RESTRICTIVO_ANTES" ] \
  || fallo "el destino cambió ante la regla que solo permite una persona"
echo "regla que solo permite una persona rechazada por la sonda positiva; destino intacto"

ORFANO_V1="$TEMP_DIR/hotel-huerfano-v1.sqlite3"
DESTINO_ORFANO_V1="$TEMP_DIR/destino-huerfano-v1.sqlite3"
crear_base_hotel_real "$ORFANO_V1" "H-ORFANO-V1" 1
sqlite3 "$ORFANO_V1" "PRAGMA foreign_keys=OFF; INSERT INTO reservation_items(reservation_id,room_id,desde,hasta) VALUES (999,999,'2026-10-11','2026-10-12');"
sqlite3 "$DESTINO_ORFANO_V1" "CREATE TABLE sentinel (valor TEXT); INSERT INTO sentinel VALUES ('intacto');"
ORFANO_V1_ANTES="$(huella "$DESTINO_ORFANO_V1")"
set +e
SALIDA="$("$RESTAURAR" "$DESTINO_ORFANO_V1" "$ORFANO_V1" 2>&1)"
CODIGO=$?
set -e
[ "$CODIGO" -ne 0 ] || fallo "se aceptó una fila que viola claves foráneas en v1: $SALIDA"
[ "$(huella "$DESTINO_ORFANO_V1")" = "$ORFANO_V1_ANTES" ] \
  || fallo "el destino cambió ante una fila huérfana en v1"
echo "fila huérfana v1 rechazada; destino intacto"

echo
echo "=== 15. Se conservan respaldos válidos v1 y v15 en su esquema histórico ==="
for version in 1 15; do
  RESPALDO_HISTORICO="$TEMP_DIR/hotel-valido-v$version.sqlite3"
  DESTINO_HISTORICO="$TEMP_DIR/destino-v$version.sqlite3"
  crear_base_hotel_real "$RESPALDO_HISTORICO" "H-HISTORICO-V$version" "$version"
  "$RESTAURAR" "$DESTINO_HISTORICO" "$RESPALDO_HISTORICO" \
    || fallo "se rechazó el respaldo histórico v$version con su esquema válido"
  [ "$(sqlite3 "$DESTINO_HISTORICO" "SELECT MAX(CAST(version AS INTEGER)) FROM flyway_schema_history;")" = "$version" ] \
    || fallo "la restauración v$version alteró la versión Flyway"
  migrar_base_hotel_real "$DESTINO_HISTORICO" "$version" 21
  [ "$(sqlite3 "$DESTINO_HISTORICO" "SELECT MAX(CAST(version AS INTEGER)) FROM flyway_schema_history;")" = "21" ] \
    || fallo "el respaldo real v$version no pudo avanzar por las migraciones hasta v21"
  [ "$(sqlite3 "$DESTINO_HISTORICO" "SELECT COUNT(*) FROM reservations WHERE codigo='H-HISTORICO-V$version';")" = "1" ] \
    || fallo "la migración de v$version a v21 no conservó la reserva semilla"
  [ "$(sqlite3 "$DESTINO_HISTORICO" 'PRAGMA integrity_check;')" = "ok" ] \
    || fallo "la migración de v$version a v21 dejó una base inconsistente"
done
echo "respaldos reales v1 y v15 restaurados y migrados hasta v21 conservando sus reservas"

echo
echo "=== 15b. Las claves primarias de tablas de reservas son obligatorias desde v1 ==="
for version in 1 15; do
  for tabla in reservation_items reservation_history; do
    RESPALDO_SIN_ID="$TEMP_DIR/hotel-sin-id-$tabla-v$version.sqlite3"
    DESTINO_SIN_ID="$TEMP_DIR/destino-sin-id-$tabla-v$version.sqlite3"
    crear_base_hotel_real "$RESPALDO_SIN_ID" "H-SIN-ID-$tabla-V$version" "$version" "sin-id-$tabla"
    sqlite3 "$DESTINO_SIN_ID" "CREATE TABLE sentinel (valor TEXT); INSERT INTO sentinel VALUES ('intacto');"
    SIN_ID_ANTES="$(huella "$DESTINO_SIN_ID")"
    set +e
    SALIDA="$("$RESTAURAR" "$DESTINO_SIN_ID" "$RESPALDO_SIN_ID" 2>&1)"
    CODIGO=$?
    set -e
    [ "$CODIGO" -ne 0 ] || fallo "se aceptó v$version sin la clave primaria $tabla.id: $SALIDA"
    case "$SALIDA" in *"no conserva INTEGER PRIMARY KEY en $tabla.id"*) ;; *) fallo "v$version se rechazó por una regla ajena a la clave primaria $tabla.id: $SALIDA" ;; esac
    [ "$(huella "$DESTINO_SIN_ID")" = "$SIN_ID_ANTES" ] \
      || fallo "el destino cambió ante la ausencia de $tabla.id en v$version"
  done
done
echo "se rechazaron las claves primarias ausentes en reservation_items y reservation_history para v1 y v15; destinos intactos"

echo
echo "=== 15c. La columna de estado anterior del historial existe desde v1 ==="
for version in 1 15; do
  RESPALDO_SIN_ESTADO_ANT="$TEMP_DIR/hotel-sin-estado-ant-v$version.sqlite3"
  DESTINO_SIN_ESTADO_ANT="$TEMP_DIR/destino-sin-estado-ant-v$version.sqlite3"
  crear_base_hotel_real "$RESPALDO_SIN_ESTADO_ANT" "H-SIN-ESTADO-ANT-V$version" "$version" sin-estado-ant-reservation_history
  sqlite3 "$DESTINO_SIN_ESTADO_ANT" "CREATE TABLE sentinel (valor TEXT); INSERT INTO sentinel VALUES ('intacto');"
  ESTADO_ANT_ANTES="$(huella "$DESTINO_SIN_ESTADO_ANT")"
  set +e
  SALIDA="$("$RESTAURAR" "$DESTINO_SIN_ESTADO_ANT" "$RESPALDO_SIN_ESTADO_ANT" 2>&1)"
  CODIGO=$?
  set -e
  [ "$CODIGO" -ne 0 ] || fallo "se aceptó v$version sin reservation_history.estado_ant: $SALIDA"
  case "$SALIDA" in *"no contiene la columna requerida reservation_history.estado_ant"*) ;; *) fallo "v$version se rechazó por una regla ajena a reservation_history.estado_ant: $SALIDA" ;; esac
  [ "$(huella "$DESTINO_SIN_ESTADO_ANT")" = "$ESTADO_ANT_ANTES" ] \
    || fallo "el destino cambió ante la ausencia de reservation_history.estado_ant en v$version"
done
echo "v1 y v15 sin reservation_history.estado_ant rechazadas; destinos intactos"

RESPALDO_SIN_FK_MENSAJE_V15="$TEMP_DIR/hotel-sin-fk-mensaje-v15.sqlite3"
DESTINO_SIN_FK_MENSAJE_V15="$TEMP_DIR/destino-sin-fk-mensaje-v15.sqlite3"
crear_base_hotel_real "$RESPALDO_SIN_FK_MENSAJE_V15" "H-SIN-FK-MENSAJE-V15" 15 sin-fk-mensaje
sqlite3 "$DESTINO_SIN_FK_MENSAJE_V15" "CREATE TABLE sentinel (valor TEXT); INSERT INTO sentinel VALUES ('intacto');"
SIN_FK_MENSAJE_ANTES="$(huella "$DESTINO_SIN_FK_MENSAJE_V15")"
set +e
SALIDA="$("$RESTAURAR" "$DESTINO_SIN_FK_MENSAJE_V15" "$RESPALDO_SIN_FK_MENSAJE_V15" 2>&1)"
CODIGO=$?
set -e
[ "$CODIGO" -ne 0 ] || fallo "se aceptó un respaldo v15 sin la relación de mensajes: $SALIDA"
[ "$(huella "$DESTINO_SIN_FK_MENSAJE_V15")" = "$SIN_FK_MENSAJE_ANTES" ] \
  || fallo "el destino cambió ante la falta de FK en mensajes v15"
echo "relación de mensajes introducida en v15 es obligatoria desde esa versión"

echo
echo "=== 16. Un respaldo válido v20 se conserva para migrarlo al reiniciar ==="
BUENO_V20="$TEMP_DIR/hotel-valido-v20.sqlite3"
DESTINO_V20="$TEMP_DIR/destino-v20.sqlite3"
crear_base_hotel_real "$BUENO_V20" "H-ANTERIOR" 20
sqlite3 "$DESTINO_V20" "CREATE TABLE anterior (valor TEXT); INSERT INTO anterior VALUES ('reemplazable');"
"$RESTAURAR" "$DESTINO_V20" "$BUENO_V20"
[ "$(sqlite3 "$DESTINO_V20" "SELECT COUNT(*) FROM pragma_table_info('lugares_interes') WHERE name='categoria';")" = "0" ] \
  || fallo "la restauración de v20 fabricó la columna introducida en v21"
[ "$(sqlite3 "$DESTINO_V20" 'SELECT MAX(CAST(version AS INTEGER)) FROM flyway_schema_history;')" = "20" ] \
  || fallo "la restauración v20 no conservó su versión Flyway para migración posterior"
echo "respaldo v20 restaurado sin inventar el esquema de v21"

echo
echo "=== 17. Columnas añadidas por migraciones posteriores son obligatorias ==="
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
  if [ "$columna" = "id" ]; then
    crear_base_hotel "$BASE_INCOMPLETA" "H-SIN-$tabla-$columna" 21 "sin-id-$tabla"
  else
    crear_base_hotel "$BASE_INCOMPLETA" "H-SIN-$tabla-$columna"
    sqlite3 "$BASE_INCOMPLETA" "ALTER TABLE $tabla DROP COLUMN $columna;"
  fi
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
echo "=== 18. Una interrupción después de instalar la candidata recupera la base previa ==="
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
for intento in $(seq 1 1200); do
  [ -e "$FALLO_MV_MARCADO" ] && break
  kill -0 "$PID_RESTAURACION" 2>/dev/null || break
  sleep 0.1
done
PATH="$PATH_ANTERIOR"
export PATH
[ -f "$FALLO_MV_MARCADO" ] || {
  kill -TERM "$PID_RESTAURACION" 2>/dev/null || true
  wait "$PID_RESTAURACION" 2>/dev/null || true
  fallo "no se alcanzó la sustitución atómica para inyectar SIGTERM: $(cat "$FALLO_MV_SALIDA")"
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
