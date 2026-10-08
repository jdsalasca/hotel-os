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
- [ ] **Siguiente: qué queda abierto.** Los ítems externos (dominio, credenciales OTA, copia externa)
  siguen bloqueados. Sin ellos: revisar el plan de indicadores y el borde del frontend.


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
