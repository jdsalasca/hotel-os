-- V3: indicadores de la práctica en sus tres fases.
-- Regla central: un indicador sin datos NO tiene resultado. `resultado` queda NULL y
-- `datos_faltantes` explica por qué. Un cero real es un cero; un dato ausente no.

CREATE TABLE indicator_definitions (
  clave TEXT PRIMARY KEY,
  fase INTEGER NOT NULL CHECK (fase IN (1,2,3)),   -- 1 implementación, 2 adopción, 3 operación
  nombre TEXT NOT NULL,
  definicion TEXT NOT NULL,
  formula TEXT NOT NULL,
  fuente TEXT NOT NULL,
  unidad TEXT NOT NULL,
  periodo_por_defecto TEXT NOT NULL,              -- DIARIO | SEMANAL | MENSUAL
  linea_base TEXT,                                 -- NULL si el hotel no la ha fijado
  meta TEXT,                                       -- NULL si el hotel no la ha fijado
  responsable TEXT,                                -- NULL si no hay responsable asignado
  activa INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE indicator_results (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  clave TEXT NOT NULL REFERENCES indicator_definitions(clave),
  periodo TEXT NOT NULL,                           -- p.ej. 2026-11
  resultado REAL,                                   -- NULL cuando no hay datos
  datos_faltantes TEXT,                             -- por qué no hay resultado
  generado_en TEXT NOT NULL,
  UNIQUE (clave, periodo)
);

-- Fase 2: lo que el hotel confirme (capacitaciones, difusión). Sin datos medibles de origen,
-- no se atribuye ninguna venta a ninguna campaña.
CREATE TABLE adoption_activities (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  tipo TEXT NOT NULL,                              -- CAPACITACION | USO_PERSONAL | DIFUSION
  descripcion TEXT NOT NULL,
  fecha TEXT NOT NULL,
  participantes INTEGER,                            -- NULL si no aplica
  confirmada_por TEXT,                              -- quien confirmó la actividad
  con_datos_de_origen INTEGER NOT NULL DEFAULT 0    -- 0 = no se puede atribuir ventas
);

CREATE INDEX idx_indicator_results_clave ON indicator_results(clave, periodo);
CREATE INDEX idx_adoption_activities_fecha ON adoption_activities(fecha);

-- Las definiciones se registran con línea base y meta NULL: el hotel las fija cuando tiene datos.
-- No se inventan metas ni líneas base.
INSERT INTO indicator_definitions(clave, fase, nombre, definicion, formula, fuente, unidad, periodo_por_defecto)
VALUES
  ('f1_inventario_cargado', 1, 'Inventario cargado',
   'Habitaciones y tipos dados de alta frente a los que el hotel definió que debía cargar',
   'habitaciones cargadas / habitaciones esperadas × 100', 'rooms y room_types', 'porcentaje', 'PUNTO'),
  ('f1_mapeos_por_canal', 1, 'Mapeos por canal',
   'Mapeos de habitaciones y tarifas completados por canal sobre el total planificado',
   'mapeos completados / mapeos planificados × 100', 'channel_mappings', 'porcentaje', 'PUNTO'),
  ('f1_canales_conectados', 1, 'Canales conectados',
   'Canales con sincronización autorizada exitosa frente a los canales configurados',
   'canales conectados / canales configurados × 100', 'ota_syncs', 'porcentaje', 'PUNTO'),
  ('f1_pruebas_sync', 1, 'Pruebas de sincronización',
   'Sincronizaciones exitosas sobre el total de sincronizaciones intentadas',
   'sincronizaciones exitosas / sincronizaciones intentadas × 100', 'ota_syncs', 'porcentaje', 'MENSUAL'),
  ('f2_capacitaciones', 2, 'Capacitaciones',
   'Actividades de capacitación confirmadas por el hotel en el periodo',
   'conteo de actividades de tipo CAPACITACION', 'adoption_activities', 'conteo', 'MENSUAL'),
  ('f2_uso_personal', 2, 'Uso por parte del personal',
   'Actividades de uso del sistema confirmadas por el hotel en el periodo',
   'conteo de actividades de tipo USO_PERSONAL', 'adoption_activities', 'conteo', 'MENSUAL'),
  ('f2_difusion', 2, 'Difusión',
   'Actividades de difusión o marketing confirmadas por el hotel, sin atribución de ventas',
   'conteo de actividades de tipo DIFUSION', 'adoption_activities', 'conteo', 'MENSUAL'),
  ('f3_ocupacion', 3, 'Ocupación',
   'Noches de habitación ocupadas sobre noches disponibles para la venta',
   'noches ocupadas / noches disponibles para venta × 100', 'reservation_items y rooms', 'porcentaje', 'MENSUAL'),
  ('f3_reservas_por_canal', 3, 'Reservas por canal',
   'Reservas registradas por canal en el periodo',
   'conteo de reservas agrupado por origen', 'reservations.origen', 'conteo', 'MENSUAL'),
  ('f3_tasa_cancelacion', 3, 'Tasa de cancelación',
   'Reservas canceladas sobre el total de reservas creadas en el periodo',
   'reservas canceladas / reservas creadas × 100', 'reservations.estado', 'porcentaje', 'MENSUAL'),
  ('f3_sync_exitosas', 3, 'Sincronizaciones exitosas',
   'Sincronizaciones exitosas sobre el total de sincronizaciones intentadas',
   'sincronizaciones exitosas / sincronizaciones intentadas × 100', 'ota_syncs', 'porcentaje', 'MENSUAL'),
  ('f3_sobreventa', 3, 'Incidentes de sobreventa',
   'Reservas aceptadas que excedían el inventario disponible',
   'conteo de incidentes detectados', 'auditoría de reservas', 'conteo', 'MENSUAL');