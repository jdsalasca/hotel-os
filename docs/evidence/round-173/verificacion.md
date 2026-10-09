# Ronda 173 — cierre de una no presentación

## Cambio

- `NO_PRESENTADA` es un estado terminal distinto de cancelar o rechazar.
- El panel solo ofrece la acción después del día de llegada en la zona horaria configurada por el
  hotel. El servidor responde 409 si recibe la acción antes.
- El actor queda en el historial y el estado terminal deja de bloquear inventario ni admite nuevos
  abonos.
- El panel localiza el nombre del estado, incorpora el filtro y muestra la acción que autoriza la
  API.

## Verificación automatizada

- API: llegada hoy/futura no anuncia la acción y la rechaza con 409; llegada vencida conserva
  actor en historial y vuelve a ofrecer la habitación. Suite completa: **450 pruebas, 0 fallos,
  0 errores, 1 omitida; BUILD SUCCESS** (11 min 37 s).
- Migración Flyway V18→V19 con el `DataSource` de producción y claves foráneas activas: 1/1.
  Verifica fila de reserva, asignación, historial y su detalle, abonos, mensajes, cinco índices,
  secuencias `AUTOINCREMENT` y `PRAGMA foreign_key_check` sin errores.
- Frontend: 24 archivos de prueba, 115 pruebas pasaron.
- `npm run build`: completó el chequeo de tipos y `vite build` sin errores.
- `git diff --check`: sin errores.

## Revisión visual

La ruta local `/admin/reservas` se abrió en escritorio y en móvil (393×852), con una sesión y una
reserva de demostración inyectadas en la capa de red. En móvil, documento y cuerpo miden 393 px,
la tarjeta conserva el botón «No se presentó» y no hay desbordamiento horizontal. Consola del
navegador: sin errores ni advertencias.

- Escritorio: `no-show-panel.png`
- Móvil: `no-show-panel-mobile.png`

Las capturas usan datos ficticios; no se autenticó contra producción ni se desplegó esta ronda.

## Revalidación tras cherry-pick sobre `develop`

Base remota `8025daa` (R174 y R176), con R173 integrado:

- API completa: **451 pruebas, 0 fallos, 0 errores, 1 omitida; BUILD SUCCESS** (10 min 05 s).
- Frontend: **24 archivos, 118 pruebas pasaron**.
- `npm run build`: chequeo TypeScript y build de Vite completados; salida de JS 373,16 kB
  (107,31 kB gzip) y SCSS/CSS 22,14 kB (4,74 kB gzip).
- `git diff --check`: sin errores.
