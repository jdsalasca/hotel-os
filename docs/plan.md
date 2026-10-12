# Rondas y criterios - Sistema hotelero Villa de Leyva / Sáchica

Fecha: 2026-10-06. Versiones verificadas contra Maven Central y documentación oficial en esa fecha.

## Objetivo
Monolito modular desplegable en instancia única con disco persistente, web pública + panel en
español, inventario centralizado transaccional, OTA solo con APIs oficiales y estado real,
indicadores en 3 fases, UI SCSS responsive accesible, SQLite en volumen nombrado.

## Restricciones globales
- Backend: Java 25, Spring Boot 4.1.1, Maven Wrapper, Spring MVC, Spring Security, Spring JDBC
  explícito, SQLite JDBC 3.53.4.0, Flyway 13.9.0 + `flyway-database-nc-sqlite`. Sin JPA/Hibernate.
- Frontend: React + TypeScript + Vite, SCSS con tokens (nunca CSS suelto ni inline), rutas
  pública/admin por roles.
- Persistencia: `jdbc:sqlite:/data/hotel.sqlite3`, volumen nombrado, **instancia única de escritura**,
  transacciones IMMEDIATE, sin escalar horizontalmente.
- Secretos: solo variables de entorno. `.env.example` vacío/de ejemplo.
- Sin datos reales inventados; sin datos demo en el esquema.
- TDD, commits atómicos en `develop`, evidencia en `docs/evidence/round-N/`.

## Rondas

- [x] **Ronda 1 - Contexto y modelo.** Arquitectura, modelo de datos y requisitos OTA documentados.
- [x] **Ronda 2 - Backend y reservas (versión antigua).** Sustituida por la ronda 3: contenía
  defectos que hacían imposible iniciar sesión y no usaba Flyway.
- [x] **Ronda 3 - Reconstrucción del backend + base de OTA.** Evidencia:
  `docs/evidence/round-3/verificacion.md`.
- [x] **Ronda 4 - Inventario, tarifas, canales y bloqueos.** Tipos, habitaciones, planes, tarifas
  por fecha y bloqueos, con API y pantallas de administración. Evidencia:
  `docs/evidence/round-4/verificacion.md`.
- [x] **Ronda 5 - Conectores OTA.** Un conector por canal, cliente HTTP con timeouts y reintentos,
  bitácora y pantalla con reintento.
  **Conexión validada: ninguna**, por falta de credenciales de partner. Los tres canales aparecen
  `NO_CONFIGURADO` con el bloqueo exacto. Evidencia: `docs/evidence/round-5/verificacion.md`.
- [x] **Ronda 6 - Indicadores.** Fases, fórmulas, fuentes, periodos, exportación CSV y vista
  imprimible, con `SIN_DATOS` cuando no hay respaldo verificable. Evidencia:
  `docs/evidence/round-6/verificacion.md`.
- [x] **Ronda 7 - Frontend y calidad visual.** Sistema SCSS, flujos públicos y admin, accesibilidad,
  responsive y capturas verificadas. Evidencia: `docs/evidence/round-7/verificacion.md`.
- [x] **Ronda 8 - Despliegue y operación.** Compose dev/prod, HTTPS, respaldos consistentes,
  restauración probada y guía de despliegue. Evidencia:
  `docs/evidence/round-8/verificacion.md`.
- [x] **Ronda 9 - Estilos y tipos.** 57 estilos inline extraídos a SCSS y typecheck real, que
  descriptor un bug de sesión. Evidencia: `docs/evidence/round-9/verificacion.md`.
- [x] **Ronda 10 - Planes y precios por noche desde el panel.** El hotel deja de necesitar curl
  para tarifar. Evidencia: `docs/evidence/round-10/verificacion.md`.
- [x] **Build reproducible.** La imagen del backend compila el jar; un clon limpio levanta sin
  tener Maven en el servidor. Evidencia: `docs/evidence/round-10/verificacion.md`.
- [x] **Ronda 11 - Calendario de ocupación.** Una llamada reemplaza N consultas por habitación y
  distingue reserva vigente, bloqueo, mantenimiento y habitación retirada. Evidencia:
  `docs/evidence/round-11/verificacion.md`.
- [x] **Ronda 12 - Fechas inválidas en cuerpos POST.** Bloqueos, tarifas y reserva pública
  devuelven 400 con mensaje en lugar de 500. Evidencia:
  `docs/evidence/round-12/verificacion.md`.
- [x] **Ronda 13 - Mapeos declarados por canal.** El hotel registra, ve y retira enlaces entre
  inventario local e identificadores externos. No publica ni importa reservas. Evidencia:
  `docs/evidence/round-13/verificacion.md`.
- [x] **Ronda 14 - Identidad operativa del hotel.** Nombre, contacto y horarios administrables y
  visibles en la web pública, sin exponer claves internas. Evidencia:
  `docs/evidence/round-14/verificacion.md`.
- [x] **Ronda 15 - Precio acordado y comprobante.** La reserva congela total, moneda y plan; el
  huésped y el panel ven el comprobante con habitación e historial. Evidencia:
  `docs/evidence/round-15/verificacion.md`.
- [x] **Ronda 16 - OAuth2 y autenticación de producción.** Canje OAuth2 como formulario (antes
  JSON que Google rechaza), `Caddyfile` que faltaba, cookie de sesión `Secure` + `SameSite=Strict`
  verificada con HTTP real. Evidencia: `docs/evidence/round-16/verificacion.md`.
- [x] **Ronda 17 - Auditoría real de acciones administrativas.** El actor salía del cuerpo de la
  petición y el panel escribía el literal "panel": el rastro era falsificable. Ahora un filtro
  registra todo `POST /api/admin/**` con el usuario de la sesión, sin guardar el cuerpo, y el
  panel lo muestra en "Actividad". Evidencia: `docs/evidence/round-17/verificacion.md`.
- [x] **Ronda 18 - Límite de reservas públicas.** `POST /api/reservas` no tenía tope y un bucle
  podía agotar el inventario con correos inventados. Ahora 10 por IP cada 15 minutos, con 429
  legible en el formulario. Al verificar salieron dos fallos reales: `MensajeError` pegaba un
  encabezado a todos los errores, y nginx quedaba en 504 si se recreaba solo la API (resolución
  dinámica del upstream, se recupera solo en ~4 s). Evidencia:
  `docs/evidence/round-18/verificacion.md`.
- [x] **Ronda 19 - El backend deja de correr como root.** El volumen nacía de root y eso se había
  puesto como excusa para no bajar privilegios. Creando `/data` en la imagen con el propietario del
  usuario, un volumen nuevo ya nace escribible. Para un volumen heredado, el arranque dice el
  `chown` exacto en vez de un `SQLITE_READONLY_DIRECTORY` sin contexto. Evidencia:
  `docs/evidence/round-19/verificacion.md`.
- [x] **Ronda 20 - La idempotencia filtraba datos de otros huéspedes.** Quien reutilizaba la clave
  de idempotencia de otro recibía su reserva completa: nombre, correo y total. Ahora la clave
  identifica un intento, no a una persona, y la restricción pasa a
  `UNIQUE (idempotencia, email)`. Evidencia: `docs/evidence/round-20/verificacion.md`.
- [x] **Ronda 21 - Tope de lecturas públicas por IP.** Los GET de consulta, comprobante y
  disponibilidad quedaron abiertos: sin tope servían para enumerar correos y martillear la base.
  30 por minuto por IP. Evidencia: `docs/evidence/round-21/limite-lecturas.md`.
- [x] **Ronda 22 - El panel acepta entrar con Google.** Allowlist `GOOGLE_ADMIN_EMAILS`: sin ella
  cualquier cuenta entraría. Un Client ID, dos registros, sesión con `ROLE_ADMIN` y contraseña como
  respaldo intacta. Evidencia: `docs/evidence/round-22/verificacion.md`.
- [x] **Ronda 23 - Huéspedes con Google.** Tabla `usuarios` + `reservas.usuario_id` (V7; la V6 ya la
  usó la ronda 20), manejador `google-huesped`, `/api/yo`, `/api/mis-reservas`, logout propio y la
  página Mis reservas con botón Google en la navegación. La reserva anónima sigue intacta.
  Evidencia: `docs/evidence/round-23/verificacion.md`.
- [x] **Ronda 24 - El login con Google estaba roto.** Desde Spring Security 6 el contexto no se
  guarda solo: el redirect no persistía la sesión y `/api/yo` daba 401 al volver de Google, en los
  dos caminos (panel y huésped). Y la vuelta del huésped aceptaba un `vuelve` de otro dominio, que
  es un redirect abierto. Evidencia: `docs/evidence/round-24/verificacion.md`.
- [x] **Ronda 25 - CSP estricta y cabeceras iguales en las tres rutas.** Sin
  `unsafe-inline` ni `unsafe-eval` (el build no emite nada inline) y verificada además de que el
  navegador la hace cumplir, no solo que la cabecera existe. `Permissions-Policy` faltaba en el
  nginx. Evidencia: `docs/evidence/round-25/verificacion.md`.
- [x] **Ronda 26 - Tope de intentos en el arranque del administrador.** `POST /api/admin/init` es la
  ruta más sensible (su única barrera es el token y crea la cuenta de admin) y era la única que
  aceptaba un secreto sin límite: 5 intentos por IP. Además `MessageDigest.isEqual` en vez de
  `String.equals`. Evidencia: `docs/evidence/round-26/verificacion.md`.
- [x] **Ronda 27 - Tope de intentos por IP además de por cuenta.** `LoginThrottle` solo miraba
  `correo|IP`, así que cinco contraseñas de mil correos distintos nunca topaban: credential stuffing
  sin límite. Ahora hay dos contadores y el de origen corta la pasada entera. Evidencia:
  `docs/evidence/round-27/verificacion.md`.
- [x] **Ronda 28 - Mirar el informe escribía en la base.** `/api/admin/indicadores` y su CSV
  guardaban el resultado en `indicator_results` en cada lectura, y esa tabla no la consulta nadie:
  el navegador prefetchea el enlace del CSV, así que se escribía solo. `calcular` deja de
  persistir y se borra el método muerto. Evidencia: `docs/evidence/round-28/verificacion.md`.
- [x] **Ronda 29 - La instrucción de despliegue de Vercel rompía el panel.** La guía mandaba poner
  `VITE_API_BASE` con la URL del backend, lo que anula el rewrite de `/api` y deja el panel sin
  sesión por cuatro razones a la vez. Ahora la guía dice que no se define, y el cliente avisa si
  la base cruza orígenes. Evidencia: `docs/evidence/round-29/verificacion.md`.
- [x] **Ronda 30 - Comprobación de la configuración de despliegue.** El flujo de Google se rompió
  dos veces por añadir rutas al backend sin pasarlas por el proxy, y ni los tests ni las capturas lo
  detectan: el backend respondía bien, faltaba el camino. `verificar-despliegue.mjs` mira rutas,
  cabeceras y CSP en nginx y Vercel, sin Docker ni despliegue, y falla si falta una. Evidencia:
  `docs/evidence/round-30/verificacion.md`.
- [x] **Ronda 32 - Los estados tienen reglas, y dos estaban rotas.** Se podía volver a confirmar una
  reserva cancelada, lo que devolvía inventario ya liberado; RECHAZADA no se podia ni guardar (V6 la
  perdio del CHECK) y V8 la devuelve. El panel ya no deduce los botones: los recibe del servidor.
  Evidencia: `docs/evidence/round-32/verificacion.md`.
- [x] **Ronda 31 - La reserva con sesión, probada y ofrecida.** El enganche de la ronda 23 nunca se
  probó de extremo a extremo; ahora tres tests lo cubren (engancha, anónima intacta, no pisa dueño).
  Y la confirmación ofrece "Ver mis reservas" cuando hay sesión, en lugar de insistir en el código.
  Evidencia: `docs/evidence/round-31/verificacion.md`.
- [x] **Ronda 33 - El panel por fin tiene salida.** «Cerrar sesión» junto a la navegación del
  panel, solo con sesión y solo en rutas del panel; tras cerrar, va a `/admin/entrar` y la cookie
  vieja responde 401. De paso, el cierre dejó de ser un 302 a `/login`: ahora es 200 con JSON.
  Evidencia: `docs/evidence/round-33/verificacion.md`.
- [x] **Ronda 34 - La reserva pública ya no se vende sin precio.** El alta aceptaba cualquier
  `roomId` e incluso ninguno, y guardaba la reserva con el total en NULL. Ahora el servicio exige
  precio acordado (activa, capacidad, tarifa completa) y sin `roomId` se elige la primera
  **vendible**, no la primera libre. Nueve clases de test recibieron inventario vendible mediante
  el ayudante compartido `HotelDePrueba`. Evidencia: `docs/evidence/round-34/verificacion.md`.
- [x] **Ronda 35 - La clave reutilizada con otro contenido es un 409 definido.** Repetir la
  clave de idempotencia con otras fechas u otra habitación devolvía la reserva vieja en silencio.
  Ahora el servicio compara el contenido en la transacción: lo idéntico devuelve el código sin
  duplicar, lo distinto es conflicto con motivo y sin escribir. Evidencia:
  `docs/evidence/round-35/verificacion.md`.
- [x] **Ronda 36 - Un cuerpo sin fechas ya es un 400, no un 500.** `POST /api/reservas` sin
  `llegada`/`salida` reventaba con NPE en `LocalDate.parse(null)`. El controlador lo comprueba
  antes de parsear. Evidencia: `docs/evidence/round-36/verificacion.md`.
- [x] **Ronda 37 - Calendario público de disponibilidad.** `GET /api/disponibilidad/calendario`
  dice qué días del mes tienen habitaciones a la venta y desde qué precio, con las reglas de la
  búsqueda. En la página de reserva, rejilla mensual: tocar un día libre busca esa noche y muestra
  las habitaciones con detalles. Evidencia: `docs/evidence/round-37/verificacion.md`.
- [x] **Ronda 38 - Si la tarifa se movió, el huésped lo sabe antes de confirmar.** La
  confirmación mostraba el total de la búsqueda pero el POST no lo verificaba: un cambio de tarifa
  colaba el precio nuevo en silencio. Ahora el importe viaja con la petición y se compara en la
  misma transacción del alta; si difiere, 409 con el vigente y reconfirmación explícita.
  Evidencia: `docs/evidence/round-38/verificacion.md`.
- [x] **Ronda 39 - Cada oferta muestra su precio noche por noche y su plan.** Nuevo
  `GET /api/disponibilidad/detalle` con las reglas de la búsqueda y el alta; en la tarjeta,
  «Ver detalle por noche» despliega plan y noches. La validación de noches quedó en un solo
  método compartido. Evidencia: `docs/evidence/round-39/verificacion.md`.
- [x] **Ronda 40 - El parte del día para la recepción.** Nuevo `GET /api/admin/ocupacion/dia`
  con llegadas y salidas (solo vigentes) y pantalla «Hoy en el hotel» con fecha seleccionable.
  Evidencia: `docs/evidence/round-40/verificacion.md`.
- [x] **Ronda 41 - El plan viaja explícito de la búsqueda al alta.** La oferta trae el plan,
  la tarjeta lo muestra y la confirmación lo envía; si no es el vigente, el 409 dice con cuál sí
  sale. Evidencia: `docs/evidence/round-41/verificacion.md`.
- [x] **Ronda 42 - Los errores del navegador hablan español.** Sin red, cuerpo no JSON y
  sesión vencida mostraban `TypeError`, `SyntaxError` o el «Unauthorized» de Boot. Ahora hay
  mensajes legibles y el 401 trae motivo en español desde el entry point. Evidencia:
  `docs/evidence/round-42/verificacion.md`.
- [x] **Ronda 43 - El simulacro de respaldo funciona de verdad.** Ciclo respaldo →
  destrucción → restauración → app sirviendo, en proyecto aislado sin tocar desarrollo.
  Hallazgo: no hay respaldos programados (siguiente ítem operativo). Evidencia:
  `docs/evidence/round-43/verificacion.md`.
- [x] **Ronda 44 - Descuentos por plan, de la tarifa al tag.** `descuento_pct` en el plan
  (V9), aplicado al totalizar con redondeo al céntimo; tags «−X %» con antes/ahora en ofertas,
  detalle y confirmación; gestión en el panel. Evidencia:
  `docs/evidence/round-44/verificacion.md`.
- [x] **Ronda 45 - El parte también dice quién duerme en casa.** `enCasa` en el parte (llegada
  ≤ día < salida, solo vigentes) y tercera lista en «Hoy en el hotel». Evidencia:
  `docs/evidence/round-45/verificacion.md`.
- [x] **Ronda 46 - La ocupación cuenta noches de verdad.** Intersección estancia/período,
  bloqueos restados del denominador y sobreventa como SIN_DATOS en vez de cero fijo. Evidencia:
  `docs/evidence/round-46/verificacion.md`.
- [x] **Ronda 47 - El CSV ya no ejecuta fórmulas inyectadas.** Las celdas que empiezan por
  `= + - @` salen con comilla simple inicial; entrecomillar solo no bastaba. Evidencia:
  `docs/evidence/round-47/verificacion.md`.
- [x] **Ronda 48 - Las creadas se cuentan cuando se crean.** Creadas, canceladas y por
  canal filtraban por llegada contra su propia definición; ahora filtran por `creado_en` y la
  cancelada cuenta en su cohorte. Evidencia: `docs/evidence/round-48/verificacion.md`.
- [x] **Ronda 49 - Respaldos programados que funcionan sin el agente.** Servicio `respaldo`
  en dev y producción (bucle sleep, sin cron). La prueba viva destapó que `:ro` rompe SQLite y
  que el script aceptaba respaldos vacíos: ambos corregidos. Evidencia:
  `docs/evidence/round-49/verificacion.md`.
- [x] **Ronda 50 - El huésped cancela su reserva sin llamar al hotel.** Nuevo
  `POST /api/reservas/{codigo}/cancelar` con la compuerta de código + correo y botón en dos
  pasos en la consulta. Evidencia: `docs/evidence/round-50/verificacion.md`.
- [x] **Ronda 51 - Cancelar también desde Mis reservas.** Columna de acciones con el mismo
  flujo en dos pasos y el correo de la sesión; la fila se actualiza sin recargar. Evidencia:
  `docs/evidence/round-51/verificacion.md`.
- [x] **Ronda 52 - Buscar y filtrar reservas en el panel.** `q` y `estado` en el listado y
  barra de filtros con vacío con motivo. Evidencia: `docs/evidence/round-52/verificacion.md`.
- [x] **Ronda 53 - Los bloqueos se retiran desde el panel.** Listado de vigentes y retiro
  con rastro en auditoría; la habitación vuelve a la venta. Evidencia:
  `docs/evidence/round-53/verificacion.md`.
- [x] **Ronda 54 - Reasignar la habitación desde el panel.** Endpoint con reglas de
  vigente/libre/vendible, precio recalculado y rastro con detalle (V11); control en el detalle.
  Evidencia: `docs/evidence/round-54/verificacion.md`.
- [x] **Ronda 55 - Cambiar las fechas desde el panel.** Endpoint con reglas de vigente, sin
  solape (propio excluido), con tarifa y precio recalculado; formulario en el detalle e historial
  con el movimiento. Evidencia: `docs/evidence/round-55/verificacion.md`.
- [x] **Ronda 56 - Libro de abonos y saldo, parte 1 (API).** V12, endpoints de abonar,
  anular y saldar con reglas de moneda e importe; la pantalla viene en la R57. Evidencia:
  `docs/evidence/round-56/verificacion.md`.
- [x] **Ronda 57 - La cuenta a la vista.** Sección de abonos en el detalle (registrar,
  anular, movimientos) y Abonado/Pendiente en el comprobante. Evidencia:
  `docs/evidence/round-57/verificacion.md`.
- [x] **Ronda 58 - La lista de reservas se descarga en CSV.** Endpoint con los filtros de
  la pantalla y helper `Csv` compartido; enlace que conserva filtros. Evidencia:
  `docs/evidence/round-58/verificacion.md`.
- [x] **Ronda 59 - La reconfirmación lleva el plan nuevo.** El retry conservaba el plan
  viejo y podía encadenar 409s; ahora lleva importe + moneda + plan vigentes y el resumen
  muestra el plan nuevo. Evidencia: `docs/evidence/round-59/verificacion.md`.
- [x] **Ronda 60 - Todos los planes válidos se ofrecen.** Una oferta por (habitación,
  plan con tarifa completa) con núcleo de precio compartido; el huésped elige plan de verdad.
  Evidencia: `docs/evidence/round-60/verificacion.md`.
- [x] **Ronda 60 - Entrar lleva a alguna parte y se ve quién eres.** El login con contraseña
  no navegaba y nadie mostraba la identidad: nuevo `GET /api/admin/sesion`, redirect a
  `/admin/reservas`, "Hola, {correo|nombre}" en la cabecera para admin y huésped. Evidencia:
  `docs/evidence/round-60/verificacion.md`.
- [x] **Ronda 61 - Mis reservas con estado de pago.** `abonado_cents`/`pendiente_cents` por fila
  (anulado excluido), columnas Abonado/Pendiente y badge PAGADA. Evidencia:
  `docs/evidence/round-61/verificacion.md`.
- [x] **Ronda 62 - Habitaciones fáciles para el hotelero.** Catálogo cerrado de 10 servicios
  (V13), checkboxes por tipo que reemplazan, moneda en select ISO y pastillas en las ofertas.
  Evidencia: `docs/evidence/round-62/verificacion.md`.
- [x] **Ronda 63 - GPS, distancias y mapa.** V14, CRUD de lugares, lat/lng en Hotel, sección
  Encuéntranos con OSM + distancias + cómo llegar. Evidencia:
  `docs/evidence/round-63/verificacion.md`.
- [x] **Ronda 64 - Chat huésped ↔ hotel.** Hilo por reserva en Mis reservas y en el detalle
  del panel, no-leídos que se apagan al leer, validación y componente compartido. Evidencia:
  `docs/evidence/round-64/verificacion.md`.
- [x] **Ronda 65 - El admin también reserva.** Identidad `panel:` al reservar con su correo;
  recepción y anónimo intactos; `/api/yo` y Mis reservas responden vacío con sesión sin fila.
  Evidencia: `docs/evidence/round-65/verificacion.md`.
- [x] **Ronda 61 - Tarifas atómicas, núcleo (Etapa C).** `fijarNoche()` valida todo antes
  de escribir en una transacción; lo omitido se conserva y lo rechazado no toca nada. Queda la
  edición masiva con preview. Evidencia: `docs/evidence/round-61/verificacion.md`.
- [x] **Ronda 66 - Las altas no revientan y el alta es guiada.** Tipos/planes duplicados,
  nombre nulo, tipo inexistente, estado inválido y moneda no-ISO son 400 en español sin
  escribir nada; `cop` se normaliza a `COP`. El panel guía en 4 pasos con foco y
  `aria-current`. Evidencia: `docs/evidence/round-66/verificacion.md`.
- [x] **Ronda 67 - Edición masiva de tarifas con preview (cierra Etapa C).**
  `POST /api/admin/tarifas/lote/preview` (200, sin escribir) y `/lote` (201 o 400 con
  detalle por fila, atómico). El panel revisa Antes/Después y confirma; al guardar
  relee del servidor. Evidencia: `docs/evidence/round-67/verificacion.md`.
- [x] **Ronda 68 - Indicadores: lo rechazado es 400 y no deja datos (saldos Etapa D).**
  Actividad con fecha inválida ya no se inserta antes del 400; inventario-esperado
  inválido es 400 (no 200); `2026-99` es 400 (no 500); el CSV neutraliza con espacios
  o tabs antes de la fórmula. Evidencia: `docs/evidence/round-68/verificacion.md`.
- [x] **Ronda 69 - Liveness y readiness con códigos correctos (Etapa M).**
  `/api/health/vivo` (proceso, siempre 200) y `/api/health` como readiness real:
  503 degradado o inaccesible en vez de 200 con aviso. Evidencia:
  `docs/evidence/round-69/verificacion.md`.
- [x] **Ronda 70 - Respuestas viejas no pintan + tests frontend (Etapa E/L).**
  `signal` hasta `fetch`, `AbortError` sin mensaje, 200 no-JSON como error de
  protocolo, `usePeticion` con id y `abortar`, búsqueda/detalle/elección con
  snapshot en la pública y `npm test` con vitest (11 verdes). Evidencia:
  `docs/evidence/round-70/verificacion.md`.
- [x] **Ronda 71 - Sesión compartida + `test:typecheck` (Etapa E).**
  `api/sesion.ts` comparte el vuelo (6 → 2 peticiones por navegación, sin TTL para
  no mentir) y los tests se comprueban en CI sin tocar el build de prod. Evidencia:
  `docs/evidence/round-71/verificacion.md`.
- [x] **Ronda 72 - Línea base, meta y responsable editables (Etapa D).**
  `POST …/definiciones/{clave}/referencia` (200/400/404, ausente conserva, vacío
  limpia) y editor por tarjeta en el panel con recarga. Evidencia:
  `docs/evidence/round-72/verificacion.md`.
- [x] **Ronda 74 - La búsqueda inválida es 400, no 200 con error (Etapa F).** (del colega)
  `GET /api/disponibilidad` con fechas malas o invertidas responde 400 con motivo;
  el 200 queda para respuestas de verdad (vacías o no). Número 74 porque el 73 ya
  estaba publicado por el colega. Evidencia:
  `docs/evidence/round-74/verificacion.md`.
- [x] **Ronda 76 - El detalle es oferta vendible (Etapa F).** `detalleOferta()` exige
  habitación libre con la misma regla de la búsqueda: ocupada o bloqueada es 404.
  Evidencia: `docs/evidence/round-76/verificacion.md`.
- [x] **Ronda 80 - Auditoría cubre PUT/DELETE y el logout queda atribuido (Etapa M).**
  Filtro primero en la cadena con actor de sesión antes/después; `crear` de lugares
  devuelve el id real. Deploy a prod bloqueado (sin usuario SSH). Evidencia:
  `docs/evidence/round-80/verificacion.md`.
- [x] **Ronda 82 - El calendario cuenta habitaciones y agrupa por moneda (Etapa I).**
  `disponibles` son habitaciones distintas y `precios` trae el mínimo por moneda
  (nuevo contrato, web actualizada). El `round-82/` lo ocupa la Ronda 82 del colega;
  esta evidencia vive en `round-82b/`. Evidencia:
  `docs/evidence/round-82b/verificacion.md`.
- [x] **Ronda 83 - La búsqueda agrupa lecturas: de 17 a 5 consultas (Etapa L).**
  Planes/tipos una vez y precio por (tipo, plan); `estaLibre()` para el detalle.
  `ContadorConsultas` + cotas que truenan ante un N+1. Evidencia:
  `docs/evidence/round-83/verificacion.md`.
- [x] **Ronda 86 - El calendario trae las tarifas del mes una vez (Etapa L).**
  Núcleo `ofertasDe()` compartido con caché mensual: de 150 a 34 consultas por mes,
  cota ≤ 50. Evidencia: `docs/evidence/round-86/verificacion.md`.
- [x] **Ronda 90 - El calendario filtra libres en memoria (Etapa L).**
  Habitaciones, reservas y bloqueos del mes una vez; por día se filtra en memoria
  con paridad probada contra la consulta SQL: de 34 a 7 consultas, cota ≤ 12.
  Evidencia: `docs/evidence/round-90/verificacion.md`.
- [x] **Ronda 91 - Bloquear lo inexistente o sin fechas es 400 (Etapa F).**
  Habitación inexistente y fechas nulas: 400 con motivo y sin filas, no 500.
  Evidencia: `docs/evidence/round-91/verificacion.md`.
- [x] **Ronda 104 - Tope técnico de precio contra desbordes (Etapa I).**
  `PRECIO_MAXIMO_CENTS` en todas las vías de escritura; un mes al tope suma exacto.
  Evidencia: `docs/evidence/round-104/verificacion.md`.
- [x] **Ronda 105 - Tope de 1 MB a cuerpos JSON con 413 (Etapa F).**
  Filtro nuevo: corta por longitud declarada o contando el flujo; la auditoría lo
  registra. Evidencia: `docs/evidence/round-105/verificacion.md`.
- [x] **Ronda 109 - f3_ocupación se nombra por lo que mide (Etapa D).**
  Migración V17 ("Noches comprometidas", clave intacta) + infra axe lista sin
  ejecutar (daemon caído, no tocado). Evidencia:
  `docs/evidence/round-109/verificacion.md`.
- [x] **Ronda 99 - El lote lee el rango una vez y no resuelve dos veces (Etapa L).**
  Previa de 10 en 3 lecturas y aplicar en 13 sentencias, con cotas que truenan.
  El `round-99/` lo ocupa la Ronda 99 del colega; ver `round-101/`. Evidencia:
  `docs/evidence/round-101/verificacion.md`.
- [x] **Ronda 101 - Tarifas por rango y días de semana (Etapa C/I).**
  Expansión a noches sobre el lote atómico (preview + confirmación). Evidencia:
  `docs/evidence/round-101/verificacion.md`.
- [x] **Ronda 103 - Tarifar por rango desde el panel (Etapa C/I).**
  Formulario por rango con días ISO reutilizando previa y confirmación; verificado
  en desktop y móvil sin escribir en dev. Evidencia:
  `docs/evidence/round-103/verificacion.md`.
- [x] **Ronda 94 - Canales conectados cuenta canales, no intentos (Etapa D).**
  Conectados (configurados con sync exitosa) sobre configurados; sin ellos,
  SIN_DATOS. El `round-94/` lo ocupa la Ronda 94 del colega; esta evidencia vive
  en `round-94b/`. Evidencia: `docs/evidence/round-94b/verificacion.md`.
- [x] **Ronda 75 - La confirmación ofrece Mis reservas también al panel.** `conCuenta` con
  ambas sesiones + test del hilo de chat (20/20 vitest). Evidencia:
  `docs/evidence/round-75/verificacion.md`.
- [x] **Ronda 66 - Gestión descubrible.** Enlace Habitaciones en la nav, vacío con acción
  al alta guiada y test del saludo (11/11 vitest con los del colega). Evidencia:
  `docs/evidence/round-66/verificacion.md`.
- [x] **Ronda 67 - Regresión completa de ambos agentes.** Backend 337/337 y frontend
  17/17 en verde, despliegue de todo origin a prod verificado vivo. Evidencia:
  `docs/evidence/round-67/verificacion.md`.
- [x] **Ronda 73 - Integración con el refactor de sesión.** tsc con tests + vitest 17/17
  contra su `sesion.ts` compartido, deploy de todo origin a prod verificado. Evidencia:
  `docs/evidence/round-73/verificacion.md`.
- [ ] **Nota de coordinación.** Las rondas 60-67 son mías; el colega lleva 68-72 y otras sin
  registrar en el plan. Antes de crear evidencia, listar el directorio (ya me pasó una vez).
- [x] **Ronda 77 - Lo del colega a prod, verificado.** Sus suites en verde, deploy de todo
  origin y verificación viva (health, amenidades, lugares, web). Evidencia:
  `docs/evidence/round-77/verificacion.md`.
- [x] **Ronda 78 - Respaldos en modo túnel + higiene.** Servicio diario, shebang que
  faltaba, primer respaldo verificado con integridad + poda de 344 MB. Evidencia:
  `docs/evidence/round-78/verificacion.md`.
- [x] **Ronda 79 - El verificador de despliegue, contra prod.** 18/18 estático + CSP viva
  con frame-src OSM. Evidencia: `docs/evidence/round-79/verificacion.md`.
- [x] **Ronda 81 - La suite completa delató al WIP del colega.** 12 errores en cascada desde
  su `AuditoriaAdminTest` en progreso; sin esa clase, 345/345 verde. Reportado sin tocar sus
  archivos. Evidencia: `docs/evidence/round-81/verificacion.md`.
- [x] **Ronda 82 - Bienvenido con nombre + panel que orienta.** V16, nombre de Google
  guardado, puerta `/admin` con tarjetas y ambos logins llevándote ahí. Evidencia:
  `docs/evidence/round-82/verificacion.md`.
- [x] **Ronda 84 - La puerta del panel, vista en vivo.** `/admin` servida con aviso sin
  sesión, captura en evidencia. Evidencia: `docs/evidence/round-84/verificacion.md`.
- [x] **Ronda 85 - Barrido con el colega en movimiento.** Suites 41/41 (un rojo
  transitorio sin failing), deploy de lo publicado con health `ok`. Evidencia:
  `docs/evidence/round-85/verificacion.md`.
- [x] **Ronda 87 - El perf del colega, en vivo.** Calendario desplegado y respondiendo con
  forma correcta; 400 de búsqueda inválida verificado en prod. Evidencia:
  `docs/evidence/round-87/verificacion.md`.
- [x] **Ronda 88 - Barrido de salud integral.** 0 reinicios, endpoints 200, respaldos al
  día, disco 20%, túnel con 4 conexiones. Evidencia: `docs/evidence/round-88/verificacion.md`.
- [x] **Ronda 89 - La home en celular, verificada.** Captura 390px (todo usable) + typo
  del paso 1. Evidencia: `docs/evidence/round-89/verificacion.md`.
- [x] **Ronda 92 - Auditoría de auth + deploy de lo nuevo.** 0 lockouts, login ok hoy,
  deploy 7c68555 verificado vivo. Evidencia: `docs/evidence/round-92/verificacion.md`.
- [x] **Ronda 93 - Login en celular, verificado.** Captura 390px sin roturas; origin sin
  movimiento. Evidencia: `docs/evidence/round-93/verificacion.md`.
- [x] **Ronda 95 - Contratos sin sesión, todos en orden.** 401 JSON en español, OAuth 302,
  búsqueda mala 400, prod sincronizado. Evidencia: `docs/evidence/round-95/verificacion.md`.
- [x] **Ronda 96 - Sincronizado y sano.** HEAD igual que origin, sanos, health `ok`.
  Evidencia: `docs/evidence/round-96/verificacion.md`.
- [x] **Ronda 97 - El lote lee el rango una vez y no resuelve dos veces (Etapa L).** (del colega)
- [x] **Ronda 98 - El verificador también cuida respaldos.** Sección 4: 24/24 verde y
  FALLA probado en composes rotos. Evidencia: `docs/evidence/round-98/verificacion.md`.
- [x] **Ronda 100 - Aviso de mensajes sin leer en la nav.** Badge con el conteo existente,
  TDD (rojo→verde), 23/23 vitest. Evidencia: `docs/evidence/round-100/verificacion.md`.
- [x] **Ronda 102 - El bloqueo dice en cuántos minutos.** `segundosRestantes` + 429 con
  minutos y campo máquina; test que lo provoca, 12/12 verde. Evidencia:
  `docs/evidence/round-102/verificacion.md`.
- [x] **Ronda 103 - El chat no se deja inundar.** Tope 30/hora por reserva con 429 en
  español; TDD rojo→verde, 11/11 con vecinas. Evidencia: `docs/evidence/round-103/verificacion.md`.
- [x] **Ronda 106 - Topes del colega, en vivo.** 1 MB verificado con 413 real, health `ok`.
  Evidencia: `docs/evidence/round-106/verificacion.md`.
- [x] **Ronda 108 - Pon tu hotel a punto.** Checklist calculada + tarjeta en el panel;
  TDD, 26/26 vitest. Evidencia: `docs/evidence/round-108/verificacion.md`.
- [x] **Ronda 110 - Simulacro de restauración que sí muerde.** `verificar-respaldo.sh`:
  ok/exit 0 en bueno, ERROR/exit 1 en corrupto y vacío; cazó bug real de `set -eu` con
  `$()`. Evidencia: `docs/evidence/round-110/verificacion.md`.
- [x] **Ronda 110 - Simulacro de restauración que sí muerde.** `verificar-respaldo.sh`
  con positivo/negativos en prod + bug real de `set -eu` cazado. Evidencia:
  `docs/evidence/round-110/verificacion.md`.
- [x] **Ronda 111 - WhatsApp con la reserva ya escrita.** Botón wa.me en la confirmación
  (solo con teléfono configurado); TDD, 33/33 vitest. Evidencia:
  `docs/evidence/round-111/verificacion.md`.
- [x] **Ronda 112 - El hilo del chat trae los últimos 50 con su total.** Backend
  383 tests sin fallos (1 omitido preexistente en Windows), frontend 30/30,
  `tsc` limpio y `vite build` OK; rojo-verde verificado.
  Evidencia: `docs/evidence/round-112/verificacion.md`.
- [x] **Ronda 113 - Bloqueos solapados cuentan su unión (Etapa D).**
  `nochesBloqueadas()` sumaba intervalos y la ocupación salía inflada; ahora fusiona
  por (habitación, noche): solapados, global+individual y duplicados restan una vez.
  TDD rojo (57/56, 55/54, 27/24) → verde; `IndicadoresServiceTest` 38/38.
  Evidencia: `docs/evidence/round-113/verificacion.md`.
- [x] **Ronda 114 - Cargar anteriores en el hilo del chat.** `?antes_de=` +
  `hay_mas` en ambos GET, botón que antepone historia en huésped y panel.
  Backend 389 sin fallos, frontend 35/35, `tsc` limpio, `vite build` OK.
  Evidencia: `docs/evidence/round-114/verificacion.md`.
- [x] **Ronda 117 - Comprobante propio por sesión en Mis reservas.**
  `GET /api/mis-reservas/{codigo}/comprobante` (dueño por sesión, ajena 404) +
  panel inline por fila. Backend 395 sin fallos, frontend 40/40, `tsc` limpio.
  (Se numeró 115 por colisión y se movió a 117, siguiente libre.)
  Evidencia: `docs/evidence/round-117/verificacion.md`.
- [x] **Ronda 115 - Reserva y vínculo a la cuenta en una sola unidad (Etapa G).**
  Alta + enganche en la misma transacción (antes dos escrituras: huérfanas con 500);
  la sesión solo reclama su propio correo (H3: huésped y admin con identidad previa);
  `idPorEmail` determinista por antigüedad en vez de 500 con duplicados (H2 parcial).
  TDD rojo-verde; suite 393/393. Evidencia: `docs/evidence/round-115/verificacion.md`.
- [x] **Ronda 116 - Sesiones honestas (Etapa G).** Login manual rota el `JSESSIONID`
  (fijación cerrada, HTTP real 3/3); `salir()` avisa true solo con confirmación y
  conserva la sesión si el servidor falla (vitest 5/5 nuevo, suite 12/42, `tsc` ×2
  y `vite build` OK, capturas desktop+móvil del aviso). Evidencia:
  `docs/evidence/round-116/verificacion.md`.
- [x] **Ronda 118 - El detalle pertenece a la búsqueda (Etapas E + B).** `verDetalle`,
  resumen y caché usan el snapshot buscado (no el formulario editado); clave con
  plan y apertura por separado; sin llaves React duplicadas. TDD 3/3, suite 12/45,
  `tsc` ×2 y build OK, verificado en vivo con ofertas reales + capturas. Evidencia:
  `docs/evidence/round-118/verificacion.md`.
- [x] **Ronda 119 - El panel ignora las lecturas viejas (Etapa E).** Contadores en
  tarifas (plan/mes/tipo) y servicios del panel: la respuesta tardía no pinta ni
  deja baseline equivocado para el lote. TDD 3/3 con orden invertido, suite 14/48,
  `tsc` ×2 OK, grilla real verificada en vivo + capturas. Evidencia:
  `docs/evidence/round-119/verificacion.md`.
- [x] **Ronda 120 - Informe y carga ignoran el período viejo (Etapa E).** Guarda en
  el informe (más limpieza del error viejo) y en la carga del inventario; mismo
  patrón, TDD 3/3 con orden invertido. Suite 16/52, `tsc` ×2 y build OK, informe
  real verificado en vivo + capturas. Evidencia:
  `docs/evidence/round-120/verificacion.md`.
- [x] **Ronda 121 - Toda pantalla del panel ofrece entrar.** Reporte real en prod:
  `/admin/hotel` pedía sesión sin ningún botón para iniciarla (7 de 9 pantallas
  eran callejones). Nuevo `PuertaAdmin` compartido con enlace a `/admin/entrar`,
  usado en las 9. `tsc` ×2 limpio, captura post-deploy del URL reportado.
  Evidencia: `docs/evidence/round-121/verificacion.md`.
- [x] **Ronda 122 - La consulta trae el correo de la sesión (Misión 4).** Prefill
  editable (huésped, si no panel) que no pisa lo escrito a mano + pista visible.
  TDD 3/1 → 4/4, suite 17/56, `tsc` ×2 OK, verificado en vivo con sesión real +
  capturas. (La 121 es del colega: colisión resuelta moviendo la mía a la 122.)
  Evidencia: `docs/evidence/round-122/verificacion.md`.
- [x] **Ronda 123 - Parámetros malformados con contrato (Etapa F).** `huespedes` y
  `roomId` no numéricos + parámetros ausentes devuelven 400 con `$.error` en
  español (antes el 400 crudo de Spring). TDD 3 fallos → verde; suite backend
  399/399. Evidencia: `docs/evidence/round-123/verificacion.md`.
- [x] **Ronda 126 - Alta manual desde el panel, rebanada API (Etapa H).**
  `POST /api/admin/reservas` con origen OTRO y precio vigente congelado (400/409
  con contrato); V18 devuelve OTRO al CHECK perdido en V6/V8. TDD + ruta de
  actualización probada con sqlite3 real; suite 409/409. (124-125 del colega.)
  Evidencia: `docs/evidence/round-126/verificacion.md`.
- [x] **Ronda 127 - Nueva reserva de mostrador en el panel (Etapa H).**
  Formulario sobre el endpoint de la R126 con aviso del código y refresh; de paso,
  `Aviso` faltante en el import (ReferenceError al primer error). TDD 2/2, suite
  19/60, `tsc` ×2 OK, ciclo crear→cancelar verificado en vivo + capturas.
  Evidencia: `docs/evidence/round-127/verificacion.md`.
- [x] **Ronda 129 - Recibo imprimible en consulta y panel (Misión 1).** Botón
  Imprimir en el detalle del panel + `no-imprimir` en formularios, lista, chat y
  acciones (huésped ya lo tenía). TDD 2/2, suite 18/19 (el rojo es TDD en curso
  del colega), `tsc` ×2 OK, recibos verificados con `@media print` emulado +
  capturas. Evidencia: `docs/evidence/round-129/verificacion.md`.
- [x] **Ronda 124 - El hotel vacío lo dice (backend).** `GET /api/hotel/venta`
  (público): verdadero solo con habitación ACTIVA + tarifa; la prod vacía
  (`rooms=rates=0`) lo confirma. `HotelVentaTest` 3/3 con limpieza por test.
  (Se numeró 123 por colisión.) Evidencia: `docs/evidence/round-124/verificacion.md`.
- [x] **Ronda 128 - La vuelta fallida de Google se ve y el saludo dice el rol.**
  Reporte en prod (`?error=oauth2` mudo): `FalloOauth2` lo registra por registro
  y la entrada muestra el aviso; saludo con etiqueta `Administrador`/`Huésped`.
  Backend 409/409, frontend 58/58, captura post-deploy. (Se numeró 125 por
  colisión.) Evidencia: `docs/evidence/round-128/verificacion.md`.
- [x] **Ronda 130 - El huésped mueve sus fechas (Etapa H).**
  `POST /api/mis-reservas/:codigo/fechas` con ownership (ajena 404 sin oráculo,
  anónima 401), delegando en la misma transacción del panel. TDD 5/5; suite
  414/414. Evidencia: `docs/evidence/round-130/verificacion.md`.
- [x] **Ronda 131 - El calendario dice vacío en vez de fingir lleno (frontend).**
  `PaginaInicio` lee `/api/hotel/venta` (R124): sin inventario muestra "Este
  hotel aún no publica habitaciones" en vez de un mes de Llenos. TDD con mock e
  interruptor, 5/5; `tsc` ×2, captura post-deploy. (Se numeró 129 y 130 por
  colisión.) Evidencia: `docs/evidence/round-131/verificacion.md`.
- [x] **Ronda 132 - La CSP deja pasar el beacon de Cloudflare.** Reporte en prod:
  `beacon.min.js` bloqueado por `script-src 'self'` en cada página. Se abre solo
  ese host en nginx y vercel.json; resto igual de cerrado. Verificado en vivo.
  Evidencia: `docs/evidence/round-132/verificacion.md`.
- [x] **Ronda 134 - Cambiar fechas en Mis reservas (Etapa H).** Formulario por
  fila sobre el endpoint de la R130 con refresh de lo guardado y aviso del
  servidor. TDD 2/2, suite 19/20 (el rojo es TDD en curso del colega), `tsc` ×2
  OK, verificado en vivo desktop+móvil + capturas. (132-133 del colega.)
  Evidencia: `docs/evidence/round-134/verificacion.md`.
- [x] **Ronda 135 - Sin 500 con correos duplicados (Etapa G, H2).** Blinda la
  mitigación de la R115 en `/api/yo`, `/api/mis-reservas` y el alta (3 tests;
  rojo probado por reversión). Suite backend en verde. Evidencia:
  `docs/evidence/round-135/verificacion.md`.
- [x] **Ronda 137 - El plan elegido se respeta al reservar (Etapa B).**
  `precioDe` por plan + el alta valida el elegido (precio movido → 409 con sus
  datos; retirado → 409 con lo vigente). TDD rojo→verde; suite 427 (5 rojos del
  colega). Evidencia: `docs/evidence/round-137/verificacion.md`.
- [x] **Ronda 138 - Límite de paginación con contrato (Etapa F).**
  `?limite=-1` ya no vuelca la tabla (400) y lo no-numérico trae `$.error`.
  TDD 4/4; suite 432 (5 rojos del colega). Evidencia:
  `docs/evidence/round-138/verificacion.md`.
- [x] **Ronda 139 - El correo se guarda recortado (Etapa F).** `CrearReserva`
  recorta el email (como ya hacía con el nombre): la reserva aparece con el
  correo limpio y el reintento idempotente no duplica. TDD 2/2; suite 434
  (5 rojos del colega). Evidencia: `docs/evidence/round-139/verificacion.md`.
- [x] **Ronda 140 - Hola corto con rol, nombre ajustable y salir visible.**
  Reporte en prod (saludo cortado, sin salir): `nombreCorto` (pre-@ si no hay
  nombre), `POST /api/admin/perfil` + tarjeta "Mi cuenta", recarga tras entrar,
  salir junto al saludo. Frontend 74/74; backend 429/434 (5 rojos del colega).
  Evidencia: `docs/evidence/round-140/verificacion.md`.
- [x] **Ronda 141 - Sin reservas hacia el pasado (Etapa F).** Llegada anterior a
  hoy rechazada en alta y en mover fechas (hoy sí vale); test de rendimiento a
  fechas futuras. TDD rojo→verde; suite 437 (5 rojos del colega). Evidencia:
  `docs/evidence/round-141/verificacion.md`.
- [x] **Ronda 143 - La consulta tolera espacios (Etapa F).** Comparación
  recortada bilateral (cubre consulta, cancelar y comprobante; sana filas viejas
  sin backfill). TDD 2/2; suite 439 (5 rojos del colega). (142 del colega.)
  Evidencia: `docs/evidence/round-143/verificacion.md`.
- [x] **Ronda 142 - El vacío trae cómo contactar (cierre R133).** `PaginaInicio`
  suma email/teléfono públicos al mensaje honesto; sin contacto, el base igual
  vale. 7/7, `tsc` limpio, captura post-deploy. Evidencia:
  `docs/evidence/round-142/verificacion.md`.
- [x] **Ronda 144 - Base SEO: robots, sitemap y previa para compartir.**
  `/robots.txt` devolvía el SPA; ahora reglas + sitemap con 5 rutas y OG en el
  HTML. Sección 5 en `verificar-despliegue.mjs`: 6 FALLA sin archivos, exit 0
  con ellos; verificado en vivo. Evidencia:
  `docs/evidence/round-144/verificacion.md`.
- [x] **Ronda 145 - Sin venta, la búsqueda se deshabilita con motivo.** Con
  `a_la_venta: false`, Buscar se apaga y avisa que no traerá nada; si la lectura
  falla, sigue abierta (fail-open). 9/9, `tsc` limpio, captura post-deploy.
  Evidencia: `docs/evidence/round-145/verificacion.md`.
- [x] **Ronda 146 - El vacío contacta de verdad (mailto/tel).** El contacto del
  vacío es clicable (tel: sin espacios); sin contacto no hay párrafo. 9/9,
  `tsc` limpio. Evidencia: `docs/evidence/round-146/verificacion.md`.
- [x] **Ronda 147 - Ruta falsa con salidas.** Las URL sin ruta mostraban un hueco
  mudo (verificado en prod). Comodín `*` con `Vacio` + botones a inicio y
  consulta. 1/1 a nivel App, captura post-deploy. Evidencia:
  `docs/evidence/round-147/verificacion.md`.
- [x] **Ronda 148 - El panel avisa si el respaldo se enfría.** Punto `respaldo`
  en la checklist (copia < 36 h; sin url porque no hay pantalla que lo arregle)
  y `ListaPreparacion` que pinta texto si no hay enlace. Backend 2/2, frontend
  4/4; suite 435/440 (5 rojos del colega). En prod los respaldos están frescos.
  Evidencia: `docs/evidence/round-148/verificacion.md`.
- [x] **Ronda 149 - Sondeo OAuth2 sin credenciales.** `probar-oauth2.mjs` recorre
  ida, vuelta con código falso y rebote con motivo; `--self-test` prueba que
  detecta. 7 ok en prod + traza `[invalid_grant]` en el log (cadena probada).
  Sin cambio en la app. Evidencia: `docs/evidence/round-149/verificacion.md`.
- [x] **Ronda 150 - Al navegar se vuelve arriba.** Medido en prod (1500 → 492 al
  ir a /consulta): `VolverArribaAlNavegar` con cada ruta. 1/1 a nivel App, suite
  81/81, captura post-deploy. Evidencia: `docs/evidence/round-150/verificacion.md`.
- [x] **Ronda 151 - OAuth: SameSite=Lax + fin del silencio.** El rebote era
  `authorization_request_not_found`: Strict retenía la cookie en la vuelta de
  Google (en dev regía Lax y nunca se vio). Lax en composes + test que lo exige,
  `FalloOauth2` con causa, handlers que no lanzan, aviso y rol. Suite 437/442
  (5 rojos del colega). Evidencia: `docs/evidence/round-151/verificacion.md`.
- [x] **Ronda 152 - Vigía OAuth diario en prod.** Bucle + servicio `vigia` que
  corre el sondeo a diario y lo deja en el log; el verificador lo exige para que
  no se pierda. Loop probado en contenedor, logs verdes en prod. Evidencia:
  `docs/evidence/round-152/verificacion.md`.
- [x] **Ronda 154 - El sondeo cubre las dos puertas OAuth.** `--registro` +
  ambas por defecto (el vigía las hereda sin cambios); redirect contra el host
  de la base; base inválida falla limpio. 7 ok por puerta en prod. (Se numeró
  153 por colisión.) Evidencia: `docs/evidence/round-154/verificacion.md`.
- [x] **Ronda 155 - El sondeo se marca en el log.** `FalloOauth2` antepone
  `[sondeo]` cuando la vuelta trae `sondeo=1`; el sondeo lo manda. 3/3 + 2/2;
  suite 438/443 (5 rojos del colega). Evidencia:
  `docs/evidence/round-155/verificacion.md`.
- [x] **Ronda 156 - Simulacro semanal automático.** Bucle + servicio `simulacro`
  que corre `verificar-respaldo.sh` cada semana (`/backups` en solo lectura);
  el verificador lo exige. Probado en contenedor contra copia real, logs verdes
  en prod. Evidencia: `docs/evidence/round-156/verificacion.md`.
- [x] **Ronda 157 - Cero vulnerabilidades en dependencias web.**
  `react-router-dom` 7.8.2 → 7.18.4 y `vite` 7.1.7 → 7.3.7: audit total 3 high
  → 0. `tsc` ×2, 90/90, build ok, desplegado. Evidencia:
  `docs/evidence/round-157/verificacion.md`.
- [x] **Ronda 158 - Muro antipánico en toda la app.** Un render roto dejaba el
  hueco mudo; `MuroAntipanico` con key por ruta muestra salida y se resetea al
  navegar. 2/2 + suite 92/92, captura post-deploy. Evidencia:
  `docs/evidence/round-158/verificacion.md`.
- [x] **Ronda 159 - El pie esconde el panel sin sesión.** Los 7 accesos salían
  para todo el mundo; ahora solo con sesión de personal (el visitante ve marca,
  contacto y legal). 5/5 a nivel App, suite 94/94, captura post-deploy.
  Evidencia: `docs/evidence/round-159/verificacion.md`.
- [x] **Ronda 160 - Guardar nombre recarga (cazado en E2E real).** Stack local
  throwaway con admin propio: guardar no actualizaba el saludo (el refresco no
  repinta la cabecera montada). Ahora recarga como entrar/salir; probado en
  vivo hasta el logout. 94/94, captura. Evidencia:
  `docs/evidence/round-160/verificacion.md`.
- [x] **Ronda 161 - Origen y movimientos con su nombre.** El comprobante rotulaba
  "Estado" a origen+historial (el estado ya sale en la etiqueta). Split en dos
  filas; E2E del dinero completo en stack local (reserva H-F469608A) + captura.
  95/95. Evidencia: `docs/evidence/round-161/verificacion.md`.
- [x] **Ronda 162 - Caché HTTP con criterio.** Assets con hash a un año
  inmutable; HTML siempre revalidado (los deploys tardaban horas en verse).
  Cabeceras repetidas por la herencia de nginx + `nginx -t` real. Verificado en
  origen tras el deploy. Evidencia: `docs/evidence/round-162/verificacion.md`.
- [x] **Ronda 163 - Hotel en datos estructurados.** `application/ld+json` con
  `@type Hotel` (nombre, URL, imagen y Sáchica/Boyacá/CO, nada inventado).
  Verificador lo exige; JSON validado + presente en prod. Evidencia:
  `docs/evidence/round-163/verificacion.md`.
- [x] **Ronda 164 - Imprimir y WhatsApp en el comprobante propio.** Botón
  Imprimir + enlace wa.me con código y fechas (reusa `enlaceWhatsApp`); sin
  teléfono no hay enlace. 4/4, suite 97/97. Evidencia:
  `docs/evidence/round-164/verificacion.md`.
- [x] **Ronda 165 - Rotación de logs en prod.** Los 5 servicios con `max-size
  10m × max-file 3` (el json-file crece sin fin; disco al 22% pero prevenido) +
  prune de imágenes huérfanas. El verificador lo exige. Evidencia:
  `docs/evidence/round-165/verificacion.md`.

- [x] **Ronda 166 - Cerrada la ronda a medias: Implementación de los tests de
  huéspedes.** Los tests estaban escritos y en rojo (`404`, el endpoint no existía:
  5 fallos). `ReservaService.cambiarHuespedes` como dominio único para panel y
  huésped: solo vigentes, capacidad vía `precioDe` (subir de capacidad sale 409 sin
  tocar nada), precio recalculado, todo en transacción con rastro. El del huésped
  devuelve 404 para la ajena. Además, `cliente.ts` gana un plazo de 30 s: la
  cancelación deliberada manda sobre el plazo. Backend 444, frontend 106. Evidencia:
  `docs/evidence/round-166/verificacion.md`.
- [x] **Ronda 168 - El panel cambia los huéspedes.** El endpoint existía y estaba
  probado, pero recepción no tenía forma de corregir el grupo sin cancelar y recrear
  (perdiendo historial y abono). Formulario junto al de reasignar, solo en reserva
  vigente, reutilizando `admiteDinero`. Verificado en Chrome contra contenedores:
  9 huéspedes en una habitación de 3 → el mensaje del servidor y nada cambia; 1 →
  aplicado y anotado en el historial. Escritorio y móvil, `erroresJS=0`. Evidencia:
  `docs/evidence/round-168/verificacion.md`.
- [x] **Ronda 169 - El huésped cambia su grupo.** La otra mitad del endpoint: el
  huésped que llega con más gente ya no tiene que llamar. Botón y formulario por
  reserva vigente, lista releída tras guardar para mostrar lo guardado de verdad.
  111/111 frontend. **Sin verificación visual**: entrar como huésped pide OAuth2 de
  Google (`GOOGLE_CLIENT_ID`/`SECRET`), dependencia externa no disponible aquí.
  Evidencia: `docs/evidence/round-169/verificacion.md`.

- [ ] **Ronda 153 - Cinco mejoras de usabilidad en la reserva.** Navegación
  contextual para huésped y panel con menú móvil desplegable; «Cambiar fechas»
  conserva llegada, salida y huéspedes y vuelve a consultar; la selección se
  presenta antes del formulario; correo y nombre se validan en el campo y el
  primer error recibe foco; resultados y estado vacío se anuncian. Pruebas
  frontend, chequeo de tipos y capturas móvil/escritorio aprobados. La compilación
  de producción sigue pendiente: Vite se atascó en transforming… también en la
  copia limpia de develop. Evidencia: docs/evidence/round-153/verificacion.md.
- [x] **Ronda 170 - La portada aclara cuándo la venta está pausada.** Cinco mejoras:
  estado visible desde el hero, acceso a reservas existentes, formulario/calendario
  ocultos cuando no se puede comprar, navegación unificada en `/mis-reservas` y
  sección activa también en `/consulta`. Pausa y venta activa verificadas en
  escritorio y móvil con Playwright local. Evidencia:
  `docs/evidence/round-170/verificacion.md`.
- [x] **Ronda 171 - OAuth aterriza siempre en la app.** Tras "Entrar con Google" el
  navegador mostraba el JSON crudo de `/api/yo?continue`: el login SÍ funcionaba
  (probado en el log de prod) pero la petición guardada era una URL de datos y el
  continuador la honraba. Ahora las rutas de API/OAuth guardadas se descartan y
  vale el `/admin`; las páginas del panel se siguen respetando. Evidencia:
  `docs/evidence/round-171/verificacion.md`.

## Misiones candidatas (impacto/esfuerzo, se toma la de mayor impacto cerrable)
1. ~~Recibo descargable~~ — hecho en R129 (imprimir + no-imprimir).
2. Recordatorio de llegada por correo el día antes (requiere refresh token Gmail) —
   alto/bloqueado por credencial externa.
3. ~~Aviso de reserva al huésped~~ — **hecho en R209**: el huésped recibe código, fechas y
   cómo consultar. No dependía de la credencial: el código ya existía y nadie lo llamaba.
   Si el envío falla, la reserva se guarda igual y el motivo queda en el log. 473 backend.
4. ~~Calendario del panel con arrastre para bloquear noches~~ — hecho en R199: selección accesible y bloqueo con conflictos validados.
5. ~~`/consulta` sin email para logueados~~ — hecho en R122 (prefill) + R117.
5. Modo claro/oscuro en la web pública — bajo/medio (requiere pasar tokens SCSS
   a custom properties; medido: no cabe en una ronda chica).
6. ~~Abono en reserva cerrada~~ — hecho en R166 (400 en el servidor + el panel
   no ofrece el formulario; verificado en el stack real, no en mocks).
  7. ~~Usabilidad del flujo de reserva~~ — hecho en R167 (foco de resultados,
     validación de fechas y huéspedes, calendario accesible, resultados anteriores
     bloqueados al cambiar criterios y datos de sesión precargados). Evidencia:
     docs/evidence/round-167/verificacion.md.
  8. ~~Venta pausada sin callejones sin salida~~ — hecho en R170 (estado explicado,
     navegación a Mis reservas unificada, sin búsqueda ni calendario mientras está pausada).
     Evidencia: docs/evidence/round-170/verificacion.md.
- [x] **Ronda 172 - El auditor de accesibilidad arrancaba y cubría el panel.**
  `tools/accesibilidad/auditar.mjs` no llegaba a medir ni una página: `networkidle` nunca
  se cumple (la web mantiene conexiones) y `browser.newPage()` está prohibido por axe
  (exige `newContext()`). Además solo miraba las 6 rutas públicas, nunca el panel. Ahora
  cubre 12 rutas (6 públicas + 6 del panel) con login demo y su propia base: **0
  violaciones WCAG A/AA, exit 0**. La app era accesible; lo roto era lo que lo medía.
  Evidencia: `docs/evidence/round-172/verificacion.md`.
- [x] **Ronda 173 - Recepción puede cerrar una no presentación.** Reserva confirmada con
  llegada anterior al día local del hotel: la acción queda en el panel, conserva el actor
  en el historial y libera el inventario. El servidor la oculta y rechaza si la llegada es
  hoy o futura. V19 conserva también asignaciones, pagos, mensajes, historial, índices y
  secuencias al ampliar los CHECK de SQLite con claves foráneas activas. Evidencia:
  `docs/evidence/round-173/verificacion.md`.
- [x] **Ronda 175 - El huésped ve las condiciones antes de confirmar.** El panel ya
   guardaba hora de entrada/salida y política de cancelación y `/api/hotel` las publica,
   pero **el frontend no llamaba a `/api/hotel` en ningún sitio**: el huésped solo las
   encontraba en `/terminos`, en el pie, ya decidida. Ahora salen en el resumen del paso
   de confirmación y, si el hotel no ha configurado nada, no se inventa ninguna. Sin tocar
   recepción ni estilos compartidos. 115/115 frontend. Evidencia:
   `docs/evidence/round-175/verificacion.md`.
- [x] **Ronda 180 — La reserva congela las condiciones acordadas (V20 detrás de V19).**
   El panel guardaba hora de entrada/salida y política de cancelación, y `/api/hotel` las
   publica, pero la reserva no las guardaba: si el hotel cambiaba la política tras vender,
   quien ya reservó perdía el acuerdo (el precio sí se congela desde V4). Ahora las tres
   condiciones se congelan en el alta, dentro de la misma transacción, y el comprobante
   muestra lo acordado ese día aunque la configuración cambie. TDD rojo→verde; suite
   backend 456/456 post-merge. Evidencia: `docs/evidence/round-180/verificacion.md`.
- [x] **Propuesta R177 revisada e integrada.** La distancia en `/api/lugares` más el
   formato de la UI quedaría sin consumo si la portada sigue calculando por su cuenta.
   Ya está integrada (el commit `5ad9b0b` y el formato de la UI).
- [x] **Ronda 178 - Las distancias se leen de un vistazo.** Sitios a menos de 1 km se
   muestran en metros, los más lejanos en km con coma decimal; la nota aclara que es una
   distancia en línea recta, solo aparece con el hotel ubicado y el enlace queda entero
   en móvil. Evidencia:
   `docs/evidence/round-178/verificacion.md`.
- [x] **Ronda 179 - El menú móvil se cierra con Escape.** Al cerrar desde el teclado,
   devuelve el foco al botón del menú para no dejarlo dentro de una navegación oculta.
   Evidencia: `docs/evidence/round-179/verificacion.md`.
- [x] **Ronda 182 - Cinco mejoras de usabilidad en la reserva.** Pausa visible y
   una sola acción para reservas existentes; fechas actuales al volver a cambiar;
   llegada y salida validadas; aviso de recálculo de tarifa; foco inicial en llegada.
   Evidencia: `docs/evidence/round-182/verificacion.md`.
- [x] **Ronda 183 - La búsqueda aparece inmediatamente después de la portada.**
   El formulario, calendario y resultados quedan antes de la galería, los pasos y
   el mapa; no cambia la lógica de disponibilidad ni de reserva. Verificado con
   131 pruebas frontend, typecheck de pruebas, build Docker y capturas de escritorio
   y móvil. Evidencia: `docs/evidence/round-183/verificacion.md`.
- [x] **Ronda 184 - La entrada al panel distingue quién tiene sesión.** Sin sesión,
   el encabezado ofrece reservar o iniciar sesión y oculta las rutas administrativas;
   con sesión recupera la navegación completa. El saludo admite saltos de línea sin
   recortar el nombre, y la inicialización del perfil conserva una edición temprana.
   Suite de 132 pruebas, chequeos de tipos, compilación Docker y capturas en tres
   estados de escritorio y móvil. Evidencia: `docs/evidence/round-184/verificacion.md`.
- [x] **Ronda 185 - El alta guiada del inventario aparece cuando hace falta.** Si faltan
  tipos, habitaciones o planes, los pasos iniciales pasan antes del calendario; con
  inventario configurado, el calendario conserva prioridad. 134 pruebas frontend,
  chequeos de tipos, build Docker y tres estados revisados en Chrome. Evidencia:
  `docs/evidence/round-185/verificacion.md`.
- [x] **Ronda 186 - Datos del hotel no se puede guardar a medio cargar.** El formulario
  anuncia la lectura y desactiva sus controles hasta recibir la configuración; si
  falla, deja disponible la recuperación manual. 136 pruebas frontend, chequeos de
  tipos, build Docker y revisión de escritorio/móvil. Evidencia:
  `docs/evidence/round-186/verificacion.md`.
- [x] **Ronda 187 - La moneda del hotel y del inventario usa un catálogo compartido.**
  Datos del hotel presenta las nueve monedas existentes en un selector accesible y
  conserva cualquier código previo fuera de la lista hasta que se reemplace; si la
  carga falla, mantiene la edición manual de códigos ISO para poder recuperar. 139
  pruebas frontend, typechecks y build Vite aprobados; Chrome local sin errores ni
  desbordamiento en escritorio y móvil. La API fue simulada. Evidencia:
  `docs/evidence/round-187/verificacion.md`.

- [x] **Ronda 188 - Filtro de amenidades en disponibilidad.** El huésped filtra las ofertas
  disponibles por una o varias amenidades (todas deben coincidir), ve el conteo y puede limpiar
  el filtro; el panel aclara que cada selección aplica al tipo completo. 140 pruebas frontend,
  typechecks, build Vite y revisión visual de escritorio/móvil aprobados. APIs simuladas durante
  la revisión visual. Evidencia: `docs/evidence/round-188/verificacion.md`.

- [x] **Ronda 189 - Edición de lugares turísticos.** Administración puede abrir un lugar en el
  formulario existente, cambiar sus datos, guardar mediante la API actual o cancelar; el foco
  pasa al nombre y se conserva el estado visible/oculto. La tabla bloquea acciones incompatibles
  durante la edición y sus botones se acomodan en móvil. Verificación y capturas:
  `docs/evidence/round-189/verificacion.md`.
- [x] **Ronda 190 - Elegir cómo consultar las reservas.** Sin sesión, Google y la consulta por
  código/correo aparecen como dos opciones separadas, con CTA del mismo ancho, dos columnas en
  escritorio y apiladas en móvil. Se conservan las rutas y la autenticación existentes. Suite de
  143 pruebas, chequeo de tipos, build Vite y revisión visual en Chrome aprobados. Evidencia:
  `docs/evidence/round-190/verificacion.md`.
- [x] **Ronda 191 - Validar el correo antes de consultar una reserva.** Un correo con formato
  inválido ya no genera una petición ni se confunde con una reserva inexistente: el campo queda
  marcado, enfocado y asociado al mensaje; al corregirlo el aviso desaparece. Suite de 144
  pruebas, chequeo de tipos y build Vite aprobados. Capturas de escritorio y móvil en
  `docs/evidence/round-191/verificacion.md`.
- [x] **Ronda 192 - Separar estadías vigentes del historial.** En «Mis reservas», las estadías
  en curso y próximas aparecen primero y en orden de llegada; las anteriores y las reservas no
  vigentes (incluidas las rechazadas) quedan en un grupo de historial. La API y las acciones
  existentes se conservan. Suite de frontend, typecheck, build Vite y revisión visual de escritorio
  y móvil aprobados. Evidencia: `docs/evidence/round-192/verificacion.md`.

### Próximas mejoras de usabilidad

| Misión | Impacto | Esfuerzo | Estado |
|---|---:|---:|---|
| Categorías y filtro para lugares turísticos | Alto | Medio | Completado en R198: categorías editables y filtro coordinado con el mapa |
| Calendario del panel con arrastre para bloquear noches | Alto | Alto | Completado en R199: selección por dos toques/teclado o arrastre y rechazo de conflictos |
| Recordatorio de llegada por correo el día antes | Alto | Medio | Bloqueado por el refresh token de Gmail del hotel |
| Tema claro/oscuro público | Medio | Alto | Pendiente; requiere pasar los tokens de SCSS a propiedades de tema |
| Conversación huésped-hotel con estados y notificaciones | Alto | Alto | R200: recibos de lectura y continuidad del hilo; quedan avisos proactivos y actualización automática |

- [x] **Ronda 193 — El mapa marca los sitios, no solo el hotel.** La sección "Encuéntranos
   y explora" pintaba un único marcador (el del hotel) y los sitios solo aparecían en la
   lista contigua: para ver dónde caía un sitio había que leer el texto. Ahora el `iframe`
   lleva `marker=hotel;sitio1;sitio2` y un sitio que cae en el punto del hotel no se
   duplica. 147/147 frontend, 3 marcadores verificados en Chrome, 0 errores JS.
   Limitación medida: el encuadre sigue fijo (±0.03°) y a 300 m los pines se solapan;
   ajustar el bbox al conjunto de puntos es lo siguiente. Evidencia:
   `docs/evidence/round-193/verificacion.md`.
- [x] **Ronda 194 — El encuadre del mapa se ajusta a los sitios.** La R193 dejó los pines
   solapados: el encuadre seguía fijo (±0.03°, ~6 km) y un sitio a más de ~3 km quedaba
   además fuera del mapa sin aviso. Ahora el recuadro se calcula sobre hotel + sitios con
   aire del 35% de la dispersión, acotado entre 0.0025° y 0.06°. Verificado: el mapa bajó
   de ~6 km a ~1 km de lado y se ven las calles del pueblo. 150/150 frontend. Evidencia:
   `docs/evidence/round-194/verificacion.md`.
- [x] **Ronda 181 — el panel devuelve al sitio público para quien también reserva.** Medido en
  el navegador: con sesión de admin la navegación del panel tenía nueve enlaces, todos
  administrativos, sin forma de volver a la web pública ni de consultar sus reservas sin
  adivinar la URL. Ahora repite "Reservar" y "Mis reservas" antes del bloque del panel, y una
  línea divisoria (`.nav__corte`) los separa porque "Reservar" y "Reservas" suenan casi igual.
  156/156 frontend, sin errores de tipos. Evidencia: `docs/evidence/round-181/evidencia.md`.
- [x] **Ronda 180 — el hotelero ubica el hotel en el mapa sin teclear coordenadas.** El hotel
  no tenía coordenadas en producción (`/api/lugares` → `{"hotel":{"ubicado":false}}`), así que
  el mapa público y las distancias de R177-R179 nunca se veían. En `PaginaAdminHotel` el botón
  "Ubicar en el mapa" abre un visor de OpenStreetMap: el punto verde es la fuente de verdad y
  los botones Sur/Norte/Oeste/Este/Acercar lo mueven, actualizando los campos. La primera versión
  movía el encuadre y guardaba su centro; la verificación visual mostró que el visor trae
  arrastre propio y se desincronizaba de lo que se guardaba, así que se invirtió el modelo.
  148/148 frontend, sin errores de tipos. Evidencia: `docs/evidence/round-180/evidencia.md`.
- [x] **Ronda 195 — Saldo pendiente visible y estados claros.** «Mis reservas» resume los saldos
  de estadías próximas y en curso por separado para cada moneda, excluye canceladas, rechazadas y
  anteriores, y avisa si faltan datos para calcular alguna. Los estados rechazados y no presentados
  usan el tono de cierre junto con las cancelaciones. Sin cambios en la API. 156/156 pruebas,
  chequeo de tipos de pruebas, build y revisión visual con datos sintéticos en escritorio (1440 px)
  y móvil (390 px) aprobados; sin desbordamiento horizontal. Evidencia:
  `docs/evidence/round-195/verificacion.md`.
- [x] **Ronda 196 — Los sitios se listan de más cerca a más lejos.** La portada los ordenaba
  alfabéticamente (`ORDER BY nombre`), así que un sitio a 2 km salía antes que uno a 300 m
  solo por llamarse "A". Además ignoraba la distancia que la API ya le mandaba y la
  recalculaba en el navegador con una copia del haversine. Ahora el servidor ordena por
  cercanía y la pantalla muestra ese mismo número; sin hotel ubicado se conserva el orden
  alfabético porque no hay con qué ordenar. 458 backend / 158 frontend, y verificado contra
  los contenedores: 33 m, 912 m y 2 km, en ese orden, con nombres puestos al revés a
  propósito. Evidencia: `docs/evidence/round-196/verificacion.md`.
- [x] **Ronda 197 — Recepción ve los mensajes pendientes en cada reserva.** La cifra global
  del panel no indicaba a qué huésped responder. La API ahora entrega el total y el conteo
  agrupado por código; la lista pone una insignia accesible junto a cada reserva y la quita
  después de abrir su conversación. Suite API 459 pruebas (0 fallidas, 1 omitida), frontend
  159/159, typecheck y build Vite aprobados. Chrome con datos sintéticos en escritorio y móvil
  (390 px): sin desbordamiento ni errores de consola. Capturas y detalle:
  `docs/evidence/round-197/verificacion.md`.
- [x] **Ronda 198 — Categorías para los lugares turísticos.** El panel asigna «Qué comer»,
  «Qué visitar» o «Dónde alojarse» y la portada filtra la lista y los marcadores del mapa por
  categoría. Los lugares existentes reciben «Qué visitar» con la migración V21; la API rechaza
  categorías desconocidas. Suite API 460 pruebas (0 fallidas, 0 errores, 1 omitida), frontend
  161/161, typecheck y build Vite aprobados. Chrome con datos sintéticos en escritorio y móvil
  (390 px): sin desbordamiento ni errores de consola. Capturas y detalle:
  `docs/evidence/round-198/verificacion.md`.
- [x] **Ronda 199 — Bloqueos desde el calendario de ocupación.** El administrador elige
  noches libres con dos pulsaciones/teclas o arrastra con el ratón; el formulario muestra el
  intervalo y la salida sin guardar automáticamente. El API revisa solapes de reservas y
  bloqueos dentro de la transacción y responde 409 si el inventario cambió. Suite API 465
  pruebas (0 fallos, 0 errores, 1 omitida), 58 específicas de inventario/controlador,
  frontend 169/169, typecheck y build aprobados. Chrome local con datos sintéticos en escritorio
  y móvil (390 px), cero desbordamiento del documento y cero errores de consola. Evidencia:
  `docs/evidence/round-199/verificacion.md`.

- [x] **Ronda 200 — El chat indica quién ya leyó cada mensaje.** Los mensajes propios muestran «Visto por el hotel/huésped» o «Enviado · pendiente de lectura», según el `visto` ya devuelto por la API. El hilo abre y baja hasta lo recién enviado; actualizar conserva la posición si se consulta el historial y cargar mensajes anteriores mantiene el ancla visual. Sin cambios de API ni base de datos. Frontend 173/173, chequeo de tipos, build Vite y `git diff --check` aprobados. Chrome local con datos sintéticos: escritorio 1440 px y móvil 390 px sin desbordamiento horizontal; envío de prueba visible y cero errores/advertencias de consola. Evidencia: `docs/evidence/round-200/verificacion.md`.

- [x] **Ronda 201 — Los mensajes nuevos se actualizan solos.** El contador del huésped y los conteos del panel/recepción se actualizan cada 30 segundos mientras la pestaña está visible; vuelven a consultarse al regresar a la pestaña. Una falla temporal conserva el último conteo válido y abrir el chat no espera una segunda consulta. El temporizador compartido se desactiva sin sesión y se limpia al desmontar. Los contadores anuncian cambios a lectores de pantalla. Sin cambios de API ni base de datos. Frontend 179/179, chequeo de tipos de pruebas, build Vite y `git diff --check` aprobados. Chrome local con datos sintéticos: componente e insignia reales con SCSS de producción, escritorio 1440 × 1000 y móvil 390 × 844; menú móvil abierto, sin desbordamiento y sin errores/advertencias de consola. Evidencia: `docs/evidence/round-201/verificacion.md`.

- [x] **Ronda 202 — El comprobante se recupera después de recargar.** Tras crear la reserva, la pestaña conserva solo el código y el correo necesarios para consultar el comprobante. Al volver sin una selección activa, consulta el endpoint existente y muestra la habitación y el estado vigentes; una selección nueva tiene prioridad sobre un comprobante anterior. Un 404 limpia el localizador y un fallo temporal conserva el acceso con opción para reintentar. Sin cambios en la API ni la base de datos. Frontend 199/199, chequeo de tipos de pruebas, build Vite y `git diff --check` aprobados. Chrome local con respuestas sintéticas: escritorio 1440 × 1000 y móvil 390 × 844, sin desbordamiento horizontal ni errores/advertencias de consola. Evidencia: `docs/evidence/round-202/verificacion.md`.

- [x] **Ronda 216 — El acceso devuelve al administrador a la pantalla que pidió.** La puerta de sesión guarda ruta, parámetros y fragmento; al terminar el login con contraseña (incluido el cambio obligatorio de clave inicial) se vuelve a esa vista. Los destinos externos, fuera de `/admin` o de acceso a `/admin/entrar` se descartan; el login directo continúa en `/admin`. Sin cambios de API ni base de datos. Frontend 203/203, chequeo de tipos de pruebas y build Vite aprobados. Chrome: destino verificado como `/admin/integraciones?tab=correo#gmail`; escritorio 1440 × 1000 y móvil 390 × 844, ambos sin desbordamiento. La consola muestra dos 401 esperables en las consultas de sesión cuando se visita sin autenticar, sin excepciones de JavaScript. Evidencia: `docs/evidence/round-216/verificacion.md`.

- [x] **Ronda 219 — La guía local explica el fallo y permite recuperarse.** Si falla la consulta de `/api/lugares`, la portada conserva visible la sección «Encuéntranos y explora», muestra un aviso accesible y permite reintentar. La petición ignora respuestas tardías después de desmontar o repetir la carga. El estado exitoso sin ubicación/lugares conserva su comportamiento anterior. Prueba primero escrita y observada fallar; frontend 204/204, chequeo de tipos de pruebas, chequeo de tipos de producción y build Vite aprobados. Chrome con falla y recuperación simuladas: escritorio 1440 × 1000 y móvil 390 × 844, sin desbordamiento horizontal ni errores/advertencias de consola. Evidencia: `docs/evidence/round-219/verificacion.md`.

- [x] **Ronda 220 — La consulta distingue una reserva no encontrada de un fallo temporal.** Un 404 conserva el mensaje que no revela si el código o el correo coinciden; errores de conexión muestran la causa recuperable y otros fallos muestran un aviso temporal seguro, sin exponer detalles internos. Código y correo permanecen en el formulario para reintentar. Prueba primero escrita y observada fallar; frontend 207/207, chequeo de tipos de pruebas, typecheck de producción y build Vite aprobados. Chrome: fallo de red simulado en escritorio (1440 × 1000) y móvil (390 × 844); aviso accesible, datos conservados, ancho del documento igual al viewport y sin excepciones de JavaScript. Evidencia: `docs/evidence/round-220/verificacion.md`.

- [x] **Ronda 221 — El panel no confunde un fallo del checklist con un hotel listo.** Si `/api/admin/preparacion` falla, el panel conserva una alerta accesible con explicación y botón «Reintentar»; una respuesta exitosa vuelve a mostrar los pasos pendientes, y un resultado exitoso sin pendientes continúa ocultando la lista. Prueba de regresión escrita y observada fallar antes del cambio; frontend 208/208, chequeo de tipos de pruebas, typecheck de producción, build Vite y `git diff --check` aprobados. Chrome con respuestas sintéticas 503 y luego 200: aviso y recuperación comprobados; escritorio 1440 × 1000 y móvil 390 × 844, sin desbordamiento horizontal ni errores/advertencias de consola. Evidencia: `docs/evidence/round-221/verificacion.md`.

- [x] **Ronda 222 — La reserva informa si no pudo leer las condiciones del hotel.** Si falla `/api/hotel` al consultar horarios o política de cancelación, el resumen muestra un aviso accesible y permite volver a intentar sin deshabilitar la solicitud. Una respuesta exitosa sin condiciones sigue sin mostrar un bloque inventado. Prueba escrita y observada fallar antes del cambio; frontend 209/209, chequeo de tipos de pruebas, typecheck de producción, build Vite y `git diff --check` aprobados. Chrome con 503 y luego 200 sintéticos: error y recuperación comprobados; escritorio 1440 × 1000 y móvil 390 × 844, sin desbordamiento horizontal ni errores/advertencias de consola. Evidencia: `docs/evidence/round-222/verificacion.md`.

- [x] **Ronda 225 — Lo que le falta a la web, en una lista en vez de tres tarjetas.** Medidos en producción los tres bloqueos reales: `/api/lugares` → `ubicado:false` (mapa y distancias invisibles), `/api/admin/tipos` → `[]` (nada que vender) y `/api/hotel` sin correo ni teléfono (el cliente no tiene a quién escribir). Las rondas 218 y 224 avisaban de dos de ellos en tarjetas separadas; la verificación visual midió que tres tarjetas apiladas dejaban "Mi cuenta" fuera de la pantalla a 1280×800 (~750px). Consolidado en una sola lista con un enlace por pendiente, en orden de gravedad, con singular/plural correcto. Medido después: aviso hasta 437px y "Mi cuenta" desde 437px, ambos dentro del viewport. Frente 217/217 y typecheck limpio. Prod `9f5a866`, texto verificado dentro del bundle servido. Evidencia: `docs/evidence/round-225/`.
- [x] **Ronda 224 — El panel avisa que la web no puede vender.** `/api/admin/tipos` devuelve lista vacía en producción: la portada no muestra disponibilidad y nadie puede reservar, pero el panel no lo decía. La puerta del panel lee los tipos y avisa con enlace a crear el primero; `Array.isArray` antes de `.length` y una lectura fallida no afirma nada. Frente 214/214. Prod `4e88d6f`. Evidencia: `docs/evidence/round-224/`.
- [x] **Ronda 218 — El panel avisa que los huéspedes no pueden contactar al hotel.** Sin correo ni teléfono la línea de contacto de la portada desaparecía entera: quien quería reservar y no podía se quedaba sin a quién escribir. La puerta del panel lee `/api/hotel` y avisa sólo cuando faltan los dos, con enlace directo; si la lectura falla no inventa el aviso. Prod `4a1c983`. Evidencia: `docs/evidence/round-218/`.
- [x] **Ronda 230 — Código y correo ya no aparecen en la URL de consulta.** El comprobante usa POST con cuerpo JSON; mantiene 404 indistinguible, límite por IP y CSRF, y añade no-store a 200/404/429. La consulta y la recuperación tras recargar usan el nuevo contrato. Se actualizaron pruebas internas de reservas vinculadas, permisos y rate limit. Backend completo: 481 pruebas, 0 fallas/errores y 1 omitida; frontend 227/227, typecheck y build Vite aprobados. Chrome en escritorio 1440 × 1000 y móvil 390 × 844 sin desbordamiento horizontal ni excepciones JavaScript; 2 respuestas 401 de sesión son esperables sin autenticar. El límite actual (código de 32 bits y 30 consultas/minuto/IP) sigue siendo básico, por lo que esta ronda no habilita acceso anónimo duradero al chat. Evidencia: docs/evidence/round-230/verificacion.md.
- [x] **Ronda 230B — «Mis reservas» ofrece salidas claras cuando la cuenta está vacía.** La cuenta sin reservas tiene «Ver disponibilidad» y «Consultar otra reserva» con jerarquía primaria/secundaria; `Vacio` admite una acción opcional y el enlace genérico para reservar no se repite en el estado vacío. En móvil, el botón «Cerrar sesión» conserva su ancho y el texto queda en una línea. Frontend 230/230; prueba dirigida 19/19; typecheck, test:typecheck y build aprobados. Playwright en escritorio 1440 × 1000 y móvil 390 × 844: sin desbordamiento horizontal, acciones visibles dentro del primer viewport y salida en una línea. Se usaron respuestas sintéticas para la sesión de huésped. Evidencia: docs/evidence/round-230b/verificacion.md.
- [x] **Ronda 232 — El fallo al cargar el contacto ya no deja al huésped sin salida.** Un `GET /api/hotel` fallido se diferenciaba de la configuración sin contacto y dejaba vacíos correo, teléfono y dirección sin explicar el motivo. La portada ahora muestra un aviso accesible con reintento manual en «Reserva en línea no disponible» y cuando la búsqueda no encuentra habitaciones; una respuesta válida sin canales sigue sin inventar contacto y las respuestas tardías se ignoran al desmontar. Frontend 232/232; typecheck, test:typecheck y build aprobados. Playwright contra build local con API sintética: recuperación comprobada tras 503; ancho de documento 390 px en móvil; capturas en escritorio 1440 × 1000 y móvil 390 × 844. No se conectó a producción. Evidencia: docs/evidence/round-232/verificacion.md.
- [x] **Ronda 233 — La consulta explica cómo recuperarse de un 404.** Conserva el mensaje que no revela si el código o el correo coinciden, añade una indicación asociada accesiblemente a ambos campos para revisarlos y permite volver a consultar; si el huésped ya inició sesión, ofrece un enlace secundario a «Mis reservas» de esa cuenta. La prueba cubre el estado anónimo y autenticado y que ambos valores sigan editables. Sin cambios de API ni base de datos. Frontend 233/233 (32 archivos), typecheck de producción y pruebas, build Vite y `git diff --check` aprobados. Chromium aislado con APIs sintéticas: 1440 × 1000 y 390 × 844; el formulario conserva los datos, el enlace apunta a `/mis-reservas`, el documento no se desborda y no hay excepciones JavaScript. Consola: solo el 401 esperado al consultar sesión administrativa y el 404 simulado. Evidencia: `docs/evidence/round-233/`.
- [x] **Ronda 234 — La reserva explica cómo recuperarse si se pierde la respuesta.** Ante timeout, caída de red o error 5xx, `/reserva` explica que el resultado puede ser incierto y permite reintentar con el mismo correo y la misma clave de idempotencia. La clave aleatoria se conserva en `sessionStorage` (sin correo ni nombre) para recuperar la guía tras recargar; un éxito o rechazo definitivo limpia la marca. Sin cambios de API, base de datos, disponibilidad ni precios. Frontend 236/236; ReservaService 35/35, incluidos reintentos idempotentes; typechecks, build Vite y `git diff --check` aprobados. Chromium con un 503 sintético en escritorio 1440 × 1000 y móvil 390 × 844, sin desbordamiento ni errores JavaScript. Evidencia: `docs/evidence/round-234/verificacion.md`.
- [x] **Ronda 235 — Ubicar lugares sin copiar coordenadas.** `/admin/lugares` reutiliza el selector de punto del hotel; centra el mapa con las coordenadas configuradas, permite ajustar el marcador con Norte/Sur/Este/Oeste y acercar, y refleja el punto elegido en los campos que se guardan. El `iframe` anuncia accesiblemente que sirve para elegir el punto. Sin cambios de API ni base de datos. Frontend 237/237 (32 archivos), pruebas específicas de lugares y configuración del hotel 12/12, typechecks y build Vite aprobados. Chromium con datos sintéticos: 1440 × 1000 y 390 × 844, sin desbordamiento horizontal; mover al norte actualiza la latitud y el marcador. Evidencia: `docs/evidence/round-235/verificacion.md`.
- [x] **Ronda 231 — «Mis reservas» permite reintentar una carga fallida.** Tras un fallo de `/api/mis-reservas`, un botón accesible reintenta sin recargar la página ni perder el estado del huésped; el error permanece mientras la petición está pendiente y si vuelve a fallar. No cambia la API ni la base de datos. Prueba dirigida 22/22; suite frontend 251/251 en 32 archivos; typechecks y build Vite aprobados. Chromium con falla y recuperación sintéticas, escritorio 1440 × 1000 y móvil 390 × 844, sin desbordamiento horizontal. Evidencia: `docs/evidence/round-231/verificacion.md`.
- [x] **Ronda 236 — El chat responde solo.** El hilo solo se releía al abrir y con el botón «Actualizar»: el huésped escribía, el hotel contestaba un minuto después y la respuesta no se veía hasta pulsar. Reutiliza `useActualizacionPeriodicaVisible`, que el panel ya usaba: cada 30 s, solo con la pestaña a la vista, y relee al volver a verla. Cuando la respuesta entra mientras se lee arriba aparece un aviso que baja al final. El contador acumula hasta que el usuario baje — la primera versión lo recalculaba sobre lo que había en pantalla y el segundo sondeo borraba un aviso que nadie había leído. Sin cambios de API. Frontend 251/251 (32 archivos) y typecheck limpio. Chromium con 40 mensajes sintéticos: la respuesta llega sin tocar nada, el aviso aparece leyendo arriba y baja al final al pulsarlo. Evidencia: `docs/evidence/round-236/`.
- [x] **Ronda 239 — La moneda se identifica igual en el recibo y en «Mis reservas».** El recibo privado mostraba solo `$` y mantenía una función de formato duplicada; ahora usa el formateador compartido, que devuelve el código explícito de moneda y no fabrica un cero cuando faltan el importe o la moneda. «Mis reservas» también elimina sus dos envolturas locales redundantes. TDD: los dos casos nuevos fallaron antes del cambio por `COP 0` y `$ 200`, y pasan después. Suite frontend 253/253 en 32 archivos, chequeo de tipos de pruebas y build Vite aprobados (76 módulos). Chrome con datos sintéticos: recibo verificado en escritorio 1440×1000 y móvil 390×844, sin desbordamiento horizontal ni errores/advertencias de consola. Evidencia: `docs/evidence/round-239/verificacion.md`.
- [x] **Ronda 229 — El hotel da de alta sus habitaciones en lote.** Nuevo `POST /api/admin/habitaciones/lote`: el hotelero pega la lista separada por saltos, comas o puntos y coma y queda registrada de una vez. Éxito parcial a propósito —si el 103 está repetido, los otros once se crean igual y ve cuál quedó fuera y por qué—, cada habitación en su propia transacción. En el paso 2 del asistente va plegado; los códigos repetidos se marcan al momento con la lista ya cargada, y tras el alta se mantiene el tipo elegido. InventarioServiceTest 58/58. Evidencia: `docs/evidence/round-229/`.
- [x] **Ronda 228 — Lo que bloquea la web, arriba del formulario.** El panel avisaba del GPS y mandaba a `/admin/hotel`, pero allí los campos de latitud y longitud eran los últimos del formulario: el botón caía en y=934 de una página de 1382 px, al borde del pliegue, y en móvil había que recorrer casi diez campos. Ahora el aviso aparece sobre el formulario y un clic abre el selector; el botón del final se oculta cuando el aviso está, para que no haya dos botones con el mismo nombre. `sinPunto` mira el texto y no `Number('')` —que es 0, un número válido— y queda en `null` si la lectura falla. Frente 225/225 y typecheck limpio en su momento. Evidencia: `docs/evidence/round-228/`.
- [x] **Ronda 237 — Una sola lista de preparación, calculada desde los datos del hotel.** El panel ya no presenta avisos paralelos que podían contradecirse: consume la lista de preparación de la API y esta incorpora el contacto como paso (correo o teléfono bastan). Siete pasos en una fuente única; se muestran seis pendientes en el fixture con un paso completo. RED/GREEN de la regresión de contacto; suite API 485/0 fallos/1 omitida, suite frontend 249/249 (32 archivos), chequeos de tipos y build (76 módulos) aprobados. Chrome con datos sintéticos: escritorio 1440×1000, móvil 390×844, documento sin desbordamiento horizontal, cero mensajes de consola. Evidencia: docs/evidence/round-237/verificacion.md.
- [x] **Ronda 238 — El saldo aparece junto a cada movimiento de recepción.** El parte de llegadas, salidas y huéspedes en casa incluye abonos, pendiente y moneda desde la misma regla que el comprobante. La interfaz reutiliza el formateador monetario compartido; muestra **PAGADA** o el saldo pendiente, y no inventa cifras cuando falta precio acordado. API completa en `develop`: 487 pruebas, 0 fallos, 0 errores y 1 omitida (`BUILD SUCCESS`); `OcupacionDiaTest` 7/7. Frontend: 253/253, chequeo de tipos y tipos de pruebas limpios, build con 76 módulos. Chrome con datos sintéticos en 1440 × 1000 y 390 × 844: sin desbordamiento horizontal ni errores de consola. Evidencia: `docs/evidence/round-238/verificacion.md`.
- [x] **Ronda 240 — El parte de recepción se actualiza y comunica cuando los datos pueden quedar antiguos.** `/admin/hoy` consulta de nuevo cada 30 s mientras la pestaña está visible; pausa al ocultarla, evita solicitudes solapadas y descarta respuestas de una fecha anterior. Durante una actualización normal no aparece la carga de primer plano. Si falla el sondeo, conserva la información previa y presenta un aviso accesible y no bloqueante; el aviso se limpia al recuperarse. Sin cambios de API, base de datos ni estilos. Suite web 266/266 en 35 archivos; typecheck de pruebas y build aprobados (77 módulos). Chromium con datos sintéticos en 1440×900 y 390×844: las tres listas caben con el aviso, sin overflow horizontal ni excepciones JavaScript. El smoke simula un 503 para probar el aviso y recibe dos 401 esperados de `/api/yo` por ausencia de sesión de huésped. Capturas y verificación: `docs/evidence/round-240/`.
- [x] **Ronda 241 — La lista de reservas ya no vive dentro de la página monolítica.** La tabla y las acciones por fila están en `TablaReservas`, con entradas controladas y transiciones autorizadas por el servidor. `PaginaAdminReservas.tsx` bajó de 756 a 644 líneas físicas (112 menos; 14,8 %); el componente extraído ocupa 134 líneas, una separación que reduce el tamaño de la página, no el total de código. Frontend: 258/258 pruebas en 34 archivos, chequeos de tipos y build (77 módulos) aprobados. Chrome local con datos sintéticos: escritorio 1440×1000 y móvil 390×844, ancho del documento sin desbordamiento horizontal y sin errores/advertencias de consola. `f382f79` está verificado en `origin/develop` con autoría `jdsalasca`; el worktree y la rama R241 quedaron eliminados. Evidencia: `docs/evidence/round-241/verificacion.md`.
- [x] **Ronda 242 — Los colores literales del SCSS quedaron centralizados.** `_base.scss`, `_chat.scss`, `_componentes.scss` y `_mixins.scss` usan tokens semánticos de `_tokens.scss`; una prueba recursiva impide reintroducir colores literales en otros archivos SCSS. El CSS compilado antes y después mide 33.967 bytes y conserva exactamente la huella SHA-256. Frontend 259/259 en 35 archivos, `test:typecheck` y build de 77 módulos aprobados. Chromium local: portada a 1440×1000 y 390×844, sin desbordamiento horizontal; las únicas respuestas de error son los 401 esperables de sesión anónima. Código publicado como `6912425` por `jdsalasca`; no introduce tema oscuro/claro. Evidencia: `docs/evidence/round-242/verificacion.md`.
- [x] **Ronda 243 — La portada espera la confirmación de venta antes de ofrecer una reserva.** Mientras consulta el estado de venta, el encabezado, título de pestaña y navegación se mantienen neutrales; no aparecen búsqueda, fechas ni calendario. Si el hotel vende, vuelven el formulario y el calendario; si no vende, se muestra un único aviso y se conserva el acceso a reservas existentes. Un error conserva el fallback de búsqueda con mensaje recuperable. La revisión independiente no encontró problemas críticos ni importantes; difirió dos detalles menores de texto en el título inicial del HTML y el pie de página. Tras integrar, la suite web pasó 262/262 en 35 archivos, `typecheck`, `test:typecheck` y `build` terminaron con código 0; Vite transformó 77 módulos. Integración en `develop`: `8cbe950` y `608f4e9`, bajo la autoría canónica `jdsalasca`. El tema oscuro/claro sigue aplazado. Evidencia: `docs/evidence/round-243/verificacion.md`.
- [x] **Ronda 244 — El HTML inicial y el pie describen con neutralidad el servicio.** En apps/web/index.html, el título, la descripción y Open Graph ya no prometen reservas directas antes de que React conozca la disponibilidad. El pie informa «Información y gestión de reservas», independiente del estado de venta. Regresión RED/GREEN; suite web 263/263 en 35 archivos, `test:typecheck` y build aprobados (77 módulos), también después de integrar. Capturas sintéticas en escritorio y móvil, sin desbordamiento horizontal; dos 401 de sesión anónima esperables, cero advertencias y excepciones JavaScript. Revisión independiente sin hallazgos pendientes. Publicado en `origin/develop` como `c9cfac8`, con `author.login` y `committer.login` verificados como `jdsalasca`. El código quedó integrado; la limpieza física del worktree y de su rama está pendiente porque `git worktree remove` falló (`Invalid argument`) y Codex indicó que el artefacto pertenece a otra tarea. No se forzó su eliminación. Evidencia: `docs/evidence/round-244/verificacion.md`.
- [ ] **Siguiente:** R213 — outbox durable de correo. El diseño recomendado está aprobado; revisar la [especificación](superpowers/specs/2026-10-11-correo-outbox-design.md) y el [plan detallado](superpowers/plans/2026-10-12-r213-correo-outbox.md). No iniciar código hasta que el usuario apruebe el plan. Mantener la Etapa 9 (tema claro/oscuro) para cuando el resto del producto esté terminado. Antes de habilitar chat anónimo duradero, cerrar la verificación por correo y el token aleatorio, temporal y revocable. Bloqueo de datos en producción: siguen sin punto GPS, sin tipos de habitación y sin contacto, así que la web pública no vende ni muestra el mapa.
### Plan de la ronda 240 — Refrescar el parte sin perder datos ni ocultar su antigüedad

1. **Caracterizar los estados del turno — completado:** se cubrieron la llegada nueva sin recargar, la continuidad visual durante el sondeo, la conservación de datos ante fallo y la pausa de solicitudes con la pestaña oculta. La regresión de error se verificó en rojo antes del aviso. Los temporizadores de React se avanzan dentro de `act` para que Vitest espere el render de las promesas.
2. **Actualizar en segundo plano — completado:** `useActualizacionPeriodicaVisible` se integra a la consulta del parte cada 30 segundos. La carga inicial mantiene su estado existente; el sondeo no la enciende, no se solapa y una petición vieja no reemplaza la fecha actualmente seleccionada.
3. **Hacer transparente la falta de frescura — completado:** si falla una consulta de fondo se mantienen las filas ya visibles y aparece un aviso `role=status` con lenguaje comprensible; no se muestra el detalle técnico de red ni se presenta el parte como actualizado. Una lectura posterior correcta limpia el aviso.
4. **Verificar código y navegador — completado:** prueba dirigida 7/7; suite web 266/266 en 35 archivos; `npm run test:typecheck`, `npm run typecheck` y `npm run build` terminaron con código 0; Vite transformó 77 módulos. Playwright confirmó que los datos cambian sin recargar en escritorio y móvil, y que un 503 simulado conserva el parte y muestra el aviso. En 1440×900 y 390×844 el ancho del documento coincide con el viewport; las regiones principales caben en la vista inicial. No hubo excepciones JavaScript; el smoke registra dos 401 esperados de la sesión de huésped no iniciada y el 503 intencional de la prueba de resiliencia. Capturas: `integracion-escritorio.png`, `integracion-movil.png`, `refresco-escritorio-integrado.png` y `refresco-movil-integrado.png`.
5. **Integrar a `develop` — completado:** la implementación parte de la revisión vigente, conserva el saldo incorporado después de la rama de origen y se integra con identidad Git canónica del usuario. La verificación posterior a integrar y la publicación normal en `origin/develop` se registran en el cierre de esta ronda. El worktree temporal propio se elimina al finalizar; los worktrees ajenos permanecen intactos.

### Plan de la ronda 241 — Separar tabla y acciones de reservas

1. **Caracterizar antes del componente — completado:** se añadió `TablaReservas.test.tsx`; al principio falló porque aún no existía el componente que importaba, y tras su implementación quedaron cubiertas acciones autorizadas, ausencia de transiciones, mensajes, respaldo de correo, semántica de tabla y clase de impresión.
2. **Extraer la vista — completado:** `TablaReservas.tsx` (134 líneas físicas) recibe filas, conteos y callbacks; la página mantiene las consultas, filtros, mutaciones y detalle. `siguientes` sigue siendo la única fuente de transiciones visibles.
3. **Verificar — completado:** frente dirigido 18/18; suite frontend 258/258 en 34 archivos; `typecheck`, `test:typecheck` y build aprobados (77 módulos). Vitest imprimió un mensaje de jsdom sobre `window.scrollTo()` y una nota de tiempo de entorno; el proceso terminó con código de salida 0.
4. **Revisar en navegador — completado:** fixture sintética en Chrome a 1440×1000 y 390×844; tres filas legibles en ambos tamaños, ancho de documento 1425/1440 en escritorio y 390/390 en móvil; cero errores o advertencias de consola. Capturas y detalle en `docs/evidence/round-241/verificacion.md`.
5. **Integrar — completado:** `9f51a7c` se cherry-pickeó a `develop` como `f382f79`; la suite 258/258, ambos chequeos de tipos y build de 77 módulos pasaron después de integrar. `origin/develop` apunta a `f382f79`; GitHub devuelve `author.login=jdsalasca` y autor/committer canónicos. Worktree y rama R241 eliminados, `git worktree prune` ejecutado; los worktrees de otros agentes siguen presentes.

### Plan de la ronda 242 — Centralizar los colores SCSS sin cambiar la apariencia

1. **Caracterizar — completado:** se añadió primero `tokens-colores.test.ts`; en rojo encontró 47 colores literales fuera de `_tokens.scss`.
2. **Migrar — completado:** se añadieron tokens semánticos para texto claro, superficies, transparencias y capas de portada; los usos se reemplazaron en cuatro parciales SCSS. El tema claro/oscuro sigue aplazado.
3. **Comprobar equivalencia visual — completado:** compilar `indice.scss` desde `4d10fe1` y desde el cambio produce CSS byte por byte idéntico (33.967 bytes, SHA-256 `d4b15b292c63ffa0b5a878132443ef914ce82d9bd0451e72f2499e5379816204`). Playwright en 1440×1000 y 390×844 dio anchos de documento 1440/1440 y 390/390; capturas incluidas en la evidencia.
4. **Verificar e integrar — completado:** después del cherry-pick en `develop`, frontend 259/259 (35 archivos), `npm run test:typecheck` y `npm run build` terminaron con código 0; Vite transformó 77 módulos. El commit `6912425` se publicó en `origin/develop` con autoría de `jdsalasca`. La consola registró dos 401 esperables al consultar sesiones sin autenticar, sin excepciones de JavaScript.

### Plan de la ronda 244 — Neutralizar metadatos y pie de página

1. **Reproducir el texto incoherente — completado:** se añadieron primero regresiones al test del HTML inicial y a la portada sin venta. Antes del arreglo fallaban por el título estático «Reserva directa» y por el pie «Reservas directas».
2. **Corregir lo visible — completado:** `index.html` usa título y metadatos Open Graph neutrales con descripción informativa; `App.tsx` presenta «Información y gestión de reservas» en el pie. No cambia la lógica de disponibilidad, los estilos ni el área administrativa.
3. **Verificar código — completado:** frente dirigido 18/18; suite web 263/263 en 35 archivos; `npm run test:typecheck` y `npm run build` terminaron con código 0. Vite transformó 77 módulos.
4. **Inspeccionar en navegador — completado:** respuestas sintéticas sin cambios de datos; vista de no venta en 1440×1000 y 390×844. El ancho de documento fue 1440/1440 y 390/390. El título de pestaña y el pie muestran las etiquetas neutrales esperadas; formularios de compra ausentes y enlace a reservas existentes visible. La consola contiene dos 401 de las consultas anónimas de sesión, sin advertencias. Capturas en `docs/evidence/round-244/`.
5. **Revisión e integración — en curso:** diff, revisión independiente, verificación sobre `develop`, autoría GitHub `jdsalasca`, push normal y limpieza del worktree propio pendientes.

### Plan de la ronda 223 — Rechazar respaldos ajenos antes de reemplazar la base

1. **Reproducir sin datos reales — completado:** la regresión primero falló contra `1b31d92`: una base vacía pasó integridad y el restaurador anunció éxito con `0` tablas y reservas `?`.
2. **Fijar las regresiones — completado:** cobertura para propietario/permisos de la API, directorio sin escritura, esquema parcial, checksum incorrecto o ausente, metadatos e historial Flyway inválidos (tipos y versiones SQL, `installed_rank`, migraciones ausentes/duplicadas), baseline v1, CHECK omitidos o demasiado restrictivos, PK/UNIQUE, `NOT NULL` con reglas versionadas, índices/FK, `AUTOINCREMENT`, columnas omitidas, compatibilidad con respaldos v1/v15/v20, base vacía con WAL/SHM intactos, fallas posteriores, limpieza parcial, señal `TERM` y ciclo sintético.
3. **Corregir el restaurador — completado:** exige historial SQL contiguo; compara CRC32 y nombres de script con las migraciones empaquetadas; acepta el marcador baseline que permite `baseline-on-migrate`; valida tablas, columnas, `NOT NULL`, PK/UNIQUE, CHECK, claves foráneas, índices y `AUTOINCREMENT` según la versión aplicada, y conserva la compatibilidad de versiones antiguas válidas. La imagen de operaciones se construye desde la raíz para incluir y calcular checksums de las migraciones API. Ambas imágenes fijan `hotel` en UID/GID `100:101`; el restaurador comprueba antes el acceso al directorio, instala con modo `660`, conserva una copia previa en ruta exclusiva, comprueba escritura real en WAL y recupera ante fallas de instalación, limpieza o señal.
4. **Verificar e integrar — completado en `develop`:** después de integrar los siete commits de R223, la suite completa pasó **480/480** (0 fallas, errores u omisiones), el arnés `probar-restaurar-segura.sh` terminó con `PRUEBA DE RESTAURACION SEGURA VERIFICADA` (código 0), y las imágenes de API/operaciones se reconstruyeron desde `develop` con `hotel` en UID/GID `100:101`. Los tres archivos Compose validaron; una API sobre volumen temporal respondió `/api/health` con `estado: ok`, y la base detenida quedó en Flyway v21 con cero violaciones FK. Evidencia detallada: `docs/evidence/round-223/verificacion.md`.

**Aceptación:** un respaldo parcial o inválido termina con código distinto de cero sin alterar el destino; un directorio inaccesible para `hotel` se rechaza antes de sustituir la base; el caso válido deja esquema íntegro, UID/GID `100:101` y escritura comprobada en WAL; ninguna falla posterior o parcial puede anunciar éxito y, cuando existe una copia previa, se intenta recuperarla. Todas las verificaciones usan bases sintéticas o un volumen Docker temporal recién creado; no se tocan respaldos reales ni volúmenes de producción.


## Criterios de aceptación, verificados con ejecución real

| # | Criterio | Verificación | Resultado |
|---|---|---|---|
| 1 | `docker compose up --build` levanta la app | `docker compose config` + arranque real | ✅ |
| 2 | Una reserva creada en la web aparece en el panel | `H-C7F625DA` → panel: `WEB PENDIENTE` | ✅ |
| 3 | La reserva sobrevive al reinicio | `docker compose down && up` → sigue en la base | ✅ |
| 4 | Doble reserva rechazada | Solape → `{"error":"no hay disponibilidad para esas fechas"}` | ✅ |
| 5 | Las OTAs muestran su estado real | `BOOKING/DESPEGAR/AIRBNB = NO_CONFIGURADO` | ✅ |
| 6 | Los indicadores no inventan datos | `tieneResultado=False` con motivo concreto | ✅ |
| 7 | `compose up --build` funciona en dev y prod | Ambos `config` validan con exit 0 | ✅ |
| 8 | Pruebas verdes | `Tests run: 299, Failures: 0, Errors: 0` | ✅ |
| 9 | Sin secretos en el repositorio | Escaneo del diff antes de cada commit | ✅ |

### Escalado medido de SQLite
- 20 hilos compitiendo por la misma habitación → 1 aceptada, 19 con 409, **0 sobreventas**.
- 48 reservas/segundo de escritura.
- 50 000 reservas → 7,5 MB de archivo.

El límite no es el motor: es que el archivo necesita un disco que sobreviva al redespliegue. Por eso
el backend no puede ir en Vercel (filesystem efímero) y sí en una VM o servicio con volumen.

## Estado real 2026-10-06
- Java 25.0.4.1, Docker 29.8.2, Maven Wrapper 3.9.11 funcionando. `distributionUrl` corregido.
- **299 pruebas backend** y **`tsc --noEmit` limpio** en el frontend (los `@types` de React
  faltaban: todo React era `any` implícito y nadie lo notaba porque `vite build` no comprueba tipos).
- 34 capturas verificadas por guion, sin errores de consola ni de API.
- **Node.js sigue ausente en el host**: el frontend se construye y verifica en `node:22-alpine`,
  que es el mismo toolchain que usa Vercel.
- **Sin credenciales OTA** (esperado): ver `docs/integrations/ota-estado.md` con el bloqueo exacto
  de cada canal. Ninguna integración aparece como conectada.
- Gmail implementado con OAuth 2.0; falta el `GOOGLE_REFRESH_TOKEN` del hotel para probarlo contra
  una cuenta real. Procedimiento en `docs/operations/correo.md`.
- Demostración de extremo a extremo verificada por HTTP contra los contenedores reales, no solo por
  pruebas unitarias: tipo, habitación, plan y precios fijados desde la API aparecen en la web
  pública, y desaparecen cuando falta una noche de precio.

## Pendiente antes de declarar el sistema en operación
1. Dominio y correo del hotel, y `ADMIN_INIT_TOKEN` generado en el servidor.
2. Credenciales y aprobación de partner de Booking.com, Despegar y Airbnb.
3. Conciliación y recepción real de reservas y cancelaciones de las OTAs. Los mapeos locales ya
   pueden declararse, pero la importación sigue bloqueada por credenciales y aprobación de socio.
4. Copia de respaldos externa configurada y probada una vez.
