# R213 — Outbox durable para avisos de reserva

- **Fecha:** 2026-10-11
- **Estado:** dirección general aprobada; esta especificación está **pendiente de revisión del usuario**. No implementar ni redactar el plan de implementación hasta que se apruebe este documento.
- **Producto:** HOTEL-OS, reservas directas y panel administrativo.

## 1. Resultado buscado

Que cada reserva nueva conserve de forma durable la intención de enviar su aviso, aunque Gmail falle o la aplicación se reinicie. La reserva y la tarea de correo se guardan juntas en SQLite; un trabajador las procesa fuera del camino síncrono de la petición; administración puede consultar el resultado sin acceder al contenido personal del mensaje.

La respuesta HTTP de reserva no espera a Gmail. El trabajador puede comenzar después del commit y antes de que la conexión HTTP haya terminado de responder: la garantía es que el envío ocurre fuera de la transacción y del camino síncrono, no que ocurra después de observar la respuesta en el navegador.

## 2. Qué aprobó el usuario y qué sigue propuesto

La aprobación recibida cubre solo esta dirección:

1. Crear la tarea de correo en SQLite junto con la reserva.
2. Enviar en segundo plano con reintentos automáticos acotados.
3. Hacer visible el estado solo para administración.

Las decisiones de detalle de este documento son propuestas para revisión, no aprobaciones previas. En particular, siguen abiertas la interfaz de solo lectura, el tratamiento de datos del mensaje, los límites de espera, la recuperación tras restaurar un respaldo y la resolución operativa de un resultado incierto.

**No empezar cambios de código** hasta que el usuario apruebe esta especificación y luego revise el plan detallado por separado.

## 3. Evidencia del estado actual

- ReservaService completa la transacción de alta y después llama a avisarPorCorreo. Si el proveedor falla, la reserva queda, pero no queda una tarea persistente que permita recuperar el aviso.
- El reintento de una reserva con la misma clave idempotente no vuelve a enviar el correo. Evita duplicados por reintento HTTP, pero no recupera un envío perdido.
- CorreoService usa HttpClienteOta para OAuth y Gmail. Ese transporte puede repetir POST ante fallos de red, 429 y 5xx, y sigue redirecciones; al ocultar las cabeceras de respuesta, no sirve como frontera de reintento auditable para messages.send.
- CorreoService acepta hoy una respuesta Gmail 2xx sin id como éxito con el marcador sin-id.
- docs/operations/correo.md afirma que las reservas no envían correo automático, aunque el servicio actual sí lo intenta.
- El mensaje actual indica que la reserva está pendiente. Una tarea asíncrona podría llegar después de un cambio o cancelación y mostrar información ya desactualizada.
- La configuración de correo se liga al proceso al arrancar. Corregir variables de entorno no se detecta solo porque el trabajador vuelva a sondear; hace falta reiniciar la aplicación.
- Reserva y líneas de reserva se almacenan en SQLite. La solución propuesta no necesita un broker ni otra base de datos.

Fuentes primarias para el comportamiento externo: [método Gmail messages.send](https://developers.google.com/workspace/gmail/api/reference/rest/v1/users.messages/send), [errores y límites de Gmail](https://developers.google.com/workspace/gmail/api/guides/handle-errors) y [sintaxis Retry-After, RFC 9110 §10.2.3](https://www.rfc-editor.org/rfc/rfc9110.html#section-10.2.3). Gmail documenta que los 403 deben clasificarse por su reason estructurado, que algunos 429 de envío pueden durar varias horas y que se puede aplicar backoff. La respuesta de aceptación de la API no acredita entrega en la bandeja del huésped.

## 4. Opciones consideradas

| Opción | Ventaja | Límite | Decisión propuesta |
|---|---|---|---|
| Mantener el envío en la petición | Menor cambio | La petición espera al proveedor y no hay recuperación durable tras una caída | No cumple el objetivo |
| Outbox en SQLite | Reserva e intención se guardan juntas; aprovecha la base e infraestructura existentes | Un resultado de Gmail puede quedar incierto; requiere operación de recuperación | Recomendada |
| Cola externa | Permite separar y escalar workers | Añade servicio, operación y otra frontera de consistencia; no elimina la incertidumbre de Gmail | Fuera de esta ronda |

## 5. Alcance propuesto

### Incluye

- Una fila de outbox por reserva nueva que hoy llama a avisarPorCorreo, tanto si el origen es WEB como OTRO.
- Inserción transaccional junto con reserva, línea e historial; una repetición idempotente no crea otra fila.
- Un trabajador único y programado, con reclamación persistente, lease y protección contra ejecuciones superpuestas.
- Un solo POST a Gmail por ciclo visible del trabajador; los reintentos ocurren únicamente mediante una nueva tarea programada del outbox.
- Clasificación persistente de aceptación, reintento, bloqueo de configuración, incertidumbre o agotamiento.
- Consulta paginada, protegida por rol administrativo, y una sección de solo lectura en Integraciones.
- Actualización del mensaje al huésped, guía operativa, restauración y pruebas automatizadas con Gmail simulado.

### No incluye en la propuesta inicial

- Reenvío, corrección de correo o cierre manual desde la interfaz.
- Cola externa, varios workers, cambios de reserva o canales OTA.
- Mensajes de chat, tema oscuro/claro o rediseño general de navegación.
- Confirmaciones de entrega, lectura, rebotes o procesamiento exactamente una vez.
- Acceso de lectura a Gmail para intentar conciliar automáticamente el contenido de Enviados.

La interfaz se propone como solo lectura para mantener acotado el primer cambio. Un evento incierto quedaría visible y requeriría una decisión operativa fuera de esa vista. El usuario puede pedir que se incluya una acción administrativa de resolución antes de aprobar la especificación.

## 6. Persistencia y datos personales

Añadir una migración Flyway posterior a la versión vigente —se espera V22, por confirmar al planear— con una tabla de outbox que contenga solo los metadatos necesarios:

- id, reservation_id, tipo de aviso y unicidad por reserva/tipo;
- estado, fase, número máximo y número actual de ciclos;
- creación, último intento, próximo intento, lease y versión de reclamación;
- clasificación segura del último resultado y, si existe, el id de mensaje de Gmail.
- estado y resultado más reciente únicamente; esta versión no agrega una bitácora append-only de cada cambio.

**Propuesta de privacidad:** no duplicar en outbox el email, nombre, asunto ni cuerpo. El trabajador recupera la reserva vigente al enviar y usa su email actual. El mensaje se simplifica a una constancia genérica de recepción con el código y un enlace para consultar el estado vigente; no copia fechas, nombre ni un estado temporal. Si la reserva se canceló antes del despacho, el texto sigue siendo solo una constancia de que se recibió una solicitud y el enlace presenta el estado vigente. Si falta la reserva o el email no es utilizable, no se envía: pasa a `AGOTADO` con la clasificación allowlist `RESERVA_NO_DISPONIBLE` o `DESTINATARIO_INVALIDO`, respectivamente, sin llamar a OAuth ni a Gmail ni consumir un ciclo adicional. La migración propone una FK con `ON DELETE RESTRICT`; la clasificación de reserva ausente cubre corrupción o datos legados inconsistentes.

Esta elección evita crear una segunda copia de datos personales, pero implica que un cambio de email antes del despacho dirige el aviso a la dirección actualmente guardada. Si el usuario prefiere congelar destinatario y contenido al crear la reserva, esa alternativa exige definir retención del payload y presencia en respaldos antes de escribir código; no está implícita en esta propuesta.

La tabla no guarda cuerpos HTTP, cabeceras crudas, tokens OAuth, autorización, email completo ni mensajes de error libres. La API administrativa tampoco los expone. La fila conserva `reservation_id` como relación, con borrado restringido mientras exista el aviso. Si una reserva aun así no está disponible al procesar el evento, se aplica `AGOTADO/RESERVA_NO_DISPONIBLE`. La retención de la fila sigue a la reserva y la retención existente de la base; esta ronda no promete borrado físico ni vencimiento distinto en respaldos.

## 7. Flujo transaccional y trabajador

### Al crear la reserva

1. Dentro de la transacción actual, guardar reserva, línea, historial y tarea AVISO_RESERVA.
2. Si falla la inserción del outbox, revertir la creación completa.
3. Si se encuentra la reserva por idempotencia, responder como hoy sin insertar otra tarea.
4. Finalizar la operación HTTP sin canjear OAuth ni llamar a Gmail.

### Al procesar la tarea

1. Reclamar una sola fila mediante una transacción corta, incrementando versión de reclamación y lease, pero no el contador de ciclos. No se reclaman lotes que puedan esperar en memoria más tiempo que el lease.
2. Leer la reserva y la configuración; ninguna llamada de red ocurre mientras SQLite está en transacción.
3. Registrar la fase TOKEN_OAUTH y luego GMAIL_SEND antes de sus respectivas llamadas externas. Incrementar el contador de ciclo una sola vez, justo antes de la primera llamada externa y después de validar configuración, reserva y destinatario; si no se hace una llamada externa, no se consume ciclo.
4. Usar transporte específico de Google sin reintentos ocultos ni seguimiento de redirecciones; no alterar la política HTTP de los conectores OTA.
5. Guardar la clasificación del resultado con una actualización condicional por estado, lease y versión. Una respuesta tardía de una reclamación antigua no cambia el estado vigente.
6. Ejecutar un solo worker dentro de una instancia de la aplicación, con prevención de solapamiento. El intervalo de sondeo propuesto es cinco segundos y el lote máximo es una fila por ciclo; esta versión no habilita réplicas activas ni workers externos sobre la misma base SQLite.

### Presupuesto de tiempos propuesto

- El transporte específico de Google permite como máximo una petición OAuth y un POST a Gmail por ciclo, con timeout de 20 segundos para cada petición y sin reintentos de transporte ni redirecciones.
- El deadline total de procesamiento es 50 segundos desde la reclamación, incluidos trabajo local y persistencia. Si vence antes de iniciar `GMAIL_SEND`, se registra `REINTENTO`; si ya inició ese POST, se registra `RESULTADO_INCIERTO`.
- El lease propuesto es de 120 segundos: supera el deadline total en 70 segundos para dar margen a persistir y recuperar el estado. Los valores deben ser configurables para las pruebas, sin cambiar los límites de producción.
- Un lease vencido en `TOKEN_OAUTH` vuelve a ser elegible para reintento; como el ciclo y la fase se persisten antes de la llamada OAuth, ese ciclo cuenta aunque el proceso caiga antes de recibir respuesta. Un lease vencido en `GMAIL_SEND` se convierte en `RESULTADO_INCIERTO`, limpia la reclamación e incrementa la versión para que la respuesta tardía no pueda sobrescribir el estado.
- Si la respuesta Gmail sigue pendiente al expirar el lease, no se reclama otro envío: la recuperación terminal a incierto y la actualización condicional por versión impiden un segundo POST automático.

Tras caída normal con la misma base: lease vencido durante TOKEN_OAUTH puede reintentarse porque todavía no inició el envío; lease vencido durante GMAIL_SEND pasa a RESULTADO_INCIERTO para no duplicar a ciegas.

## 8. Estados y política de reintentos propuesta

| Estado | Significado |
|---|---|
| PENDIENTE | Tarea guardada, aún no reclamada |
| ENVIANDO | Reclamada con lease vigente; la fase queda registrada |
| REINTENTO | Rechazo temporal conocido; espera hasta una fecha persistida |
| BLOQUEADO_CONFIGURACION | Configuración o credenciales requieren corrección; no hay nuevo envío mientras siga el bloqueo |
| ACEPTADO | Gmail respondió 2xx con id no vacío; no demuestra entrega |
| RESULTADO_INCIERTO | Gmail pudo aceptar el POST, pero el resultado local no es concluyente; no se reenvía automáticamente |
| AGOTADO | Se consumieron los ciclos permitidos o hubo rechazo permanente/espera excesiva; requiere revisión |

### Reintentos

Propuesta inicial: máximo de cinco ciclos por tarea que llegan a realizar una llamada externa, tanto si el primer llamado es OAuth como si se usa un token vigente y se llama directamente a Gmail. El contador se incrementa inmediatamente antes de esa llamada. Esperas base entre ciclos: 1, 5, 15 y 60 minutos. La cifra y la secuencia son propuestas, no decisiones aprobadas. Un fallo OAuth temporal consume el ciclo y puede reintentarse; un agotamiento del lease durante Gmail no se reintenta automáticamente porque el resultado es incierto. Falta de configuración, reserva ausente o destinatario inválido se resuelven antes de la primera llamada y no consumen ciclo.

- Gmail 429 y Gmail 403 con reason explícito rateLimitExceeded o userRateLimitExceeded: REINTENTO.
- Gmail 401 o 403 con reason de credencial/permisos, como authError o domainPolicy: BLOQUEADO_CONFIGURACION.
- Gmail 403 con otra razón, otros 4xx permanentes o reason desconocido: AGOTADO; nunca tratar cualquier 403 como límite de cuota ni como error de configuración sin evidencia.
- Fallo de red, 429 o 5xx de OAuth antes del envío: reintento acotado. OAuth 401, invalid_grant, invalid_client o respuesta 2xx sin access_token: BLOQUEADO_CONFIGURACION. Un 4xx OAuth desconocido queda AGOTADO con clasificación segura.
- Timeout o pérdida de conexión después de iniciar GMAIL_SEND, respuesta Gmail 5xx, redirección o Gmail 2xx sin id no vacío: RESULTADO_INCIERTO; no hacer otro POST automáticamente.
- Falta de configuración detectada antes de llamar a OAuth: BLOQUEADO_CONFIGURACION sin consumir ciclo.
- Tras corregir variables de entorno se debe reiniciar la aplicación. En el siguiente arranque, una vez cargada la configuración, el trabajador cambia las filas `BLOQUEADO_CONFIGURACION` a `PENDIENTE` sin crear otra fila ni alterar el contador de ciclos; luego las procesa normalmente. Si las credenciales todavía fallan en OAuth, vuelve a `BLOQUEADO_CONFIGURACION`. El sondeo por sí solo no recarga HotelProperties; las filas bloqueadas no consumen más ciclos mientras esperan el reinicio.

### Retry-After

RFC 9110 admite segundos enteros no negativos o una fecha HTTP. Propuesta:

1. Parsear ambos formatos sin guardar ni exponer la cabecera cruda.
2. Si el valor es válido y no excede 24 horas, programar el mayor entre ese plazo y la espera base.
3. Si falta o está malformado, usar la espera base.
4. Si pide más de 24 horas, no enviar antes del plazo del proveedor: pasar a AGOTADO con un código seguro de espera excesiva para revisión.
5. El máximo de cinco ciclos se conserva incluso cuando uno de ellos queda programado a muchas horas.

Gmail indica que ciertos límites diarios pueden producir 429 durante varias horas; por eso el sistema no acorta una espera válida del proveedor. El umbral de 24 horas es una propuesta para balancear espera y utilidad del aviso, pendiente de aprobación.

## 9. Restauración de respaldos

Una caída y reinicio con la base actual no equivalen a restaurar una copia antigua: los efectos externos de Gmail no vuelven atrás cuando se restaura SQLite.

Procedimiento propuesto para restaurar una base que ya contiene la tabla del outbox. Durante toda la ventana de migración y cuarentena la aplicación debe estar en modo de mantenimiento: no acepta altas ni otras mutaciones de reservas, no permite envíos de correo de prueba y el worker permanece desactivado. Las lecturas de salud y solo las operaciones estrictamente necesarias para el procedimiento pueden seguir disponibles.

1. Activar el modo de mantenimiento, bloquear escrituras de reservas, detener el worker y mantener CORREO_OUTBOX_ACTIVO=false.
2. Restaurar la copia y arrancar la API en modo de mantenimiento, todavía con el worker inactivo, para aplicar migraciones vigentes. Una solicitud de alta o un envío de correo de prueba durante esta ventana responde temporalmente no disponible (503) y no crea reserva, outbox ni hace llamadas a Google.
3. Ejecutar una operación de cuarentena única dentro de SQLite: todas las filas recuperadas en PENDIENTE, ENVIANDO, REINTENTO o BLOQUEADO_CONFIGURACION pasan a RESULTADO_INCIERTO, reciben la clasificación RESPALDO_RESTAURADO_REVISAR, y limpian lease y próximo intento. Las filas ACEPTADO, RESULTADO_INCIERTO y AGOTADO conservan su estado.
4. Comprobar que la consulta de elegibles devuelve cero filas recuperadas anteriores a la restauración. Guardar en el registro operativo el conteo por estado antes y después de la cuarentena.
5. Retirar el modo de mantenimiento y habilitar las altas de reservas; reactivar el worker solo después de completar y verificar la cuarentena. Desde ese momento las tareas nuevas se procesan normalmente; las filas inciertas recuperadas no se reenvían automáticamente.

Esta decisión es deliberadamente conservadora: una fila pudo no haberse enviado y aun así quedará incierta. El personal puede revisar Enviados por código, destinatario y ventana temporal y contactar al huésped por otro canal cuando no haya certeza. La búsqueda manual no actualiza el estado de la app en la propuesta de solo lectura; no se añade permiso Gmail de lectura ni un botón de resolución. Por tanto, RESULTADO_INCIERTO puede permanecer visible. Una transición administrativa auditada para registrar conciliación es una ampliación que el usuario debe aprobar antes de planear.

Una copia anterior a la creación de la tabla no contiene tareas de outbox; esas reservas históricas no se regeneran automáticamente. No se envían correos retroactivos al aplicar la migración.

## 10. Consulta administrativa

Propuesta de API de solo lectura bajo /api/admin/**:

- GET /api/admin/correo/outbox?estado=&limite=&cursor=
- límite predeterminado 25 y máximo 100; cursor opaco estable ordenado por creado_en DESC e id DESC.
- devolver código de reserva, estado, ciclo actual/máximo, marcas de creación/último/próximo intento y clasificación allowlist.
- no devolver email, nombre, asunto, cuerpo, id de Gmail, token, Authorization, texto libre ni respuesta original de Google.
- una sección independiente en PaginaAdminIntegraciones muestra carga, error, vacío, paginación y todos los estados. ACEPTADO debe decir “Aceptado por Gmail; no confirma entrega”.
- estados se identifican con texto y no solo color. Controles accesibles con teclado, foco visible y avisos anunciados.

Cursor en lugar de offset para ordenar páginas de forma determinista sin permitir offsets enormes. El endpoint requiere rol administrador; huéspedes y anónimos no pueden consultarlo.

## 11. Validación y privacidad al generar el mensaje

R213 cambia el transporte del correo y pasa la dirección de reserva al trabajador. El límite propuesto de 254 octetos se basa en el máximo del forward-path SMTP (RFC 5321 §4.5.3.1.3: https://www.rfc-editor.org/rfc/rfc5321.html#section-4.5.3.1.3). La política operativa completa y sus casos están especificados a continuación.

La regla de dirección se aplica antes de crear encabezados o hacer cualquier petición HTTP: una sola dirección ASCII de hasta 254 octetos; rechazar Unicode, listas de destinatarios, controles y CR/LF; no registrar el valor completo. Aplicar la misma regla al alta de reserva, al envío de prueba administrativo y al despacho desde outbox. El alta inválida se rechaza antes de persistir reserva/outbox; una reserva histórica con dirección inválida termina como `AGOTADO/DESTINATARIO_INVALIDO`, sin llamada OAuth ni POST Gmail.

Remitente y asunto también deben rechazar CR/LF. Los errores de Google se reducen a estados y códigos allowlist; ningún mensaje crudo del proveedor, token o cabecera aparece en logs o API. Las pruebas usan servidores HTTP locales y dominios .test; no se usan credenciales reales ni se manda correo real.

## 12. Criterios de aceptación para el futuro plan

### API y base de datos

- Prueba SQLite demuestra que reserva y outbox se confirman juntas y que un fallo del insert revierte reserva, línea e historial.
- Una reserva repetida por idempotencia conserva una sola tarea y no hace llamadas de red.
- Gmail sin configuración no impide guardar la reserva.
- Gmail 2xx con id, 2xx sin id, 401, 429, 403 por cuota, credencial/permisos o reason desconocido, rechazo permanente, timeout, 5xx, redirección y OAuth fallido producen los estados definidos.
- OAuth 2xx sin token no llama a Gmail; 401/invalid_grant/invalid_client bloquean configuración; un arreglo de variables requiere reiniciar y recupera la misma fila.
- Configuración ausente se bloquea antes de OAuth, sin llamada HTTP ni ciclo. Reserva ausente o dirección inválida en una reserva persistida termina en `AGOTADO` con `RESERVA_NO_DISPONIBLE` o `DESTINATARIO_INVALIDO`, sin consumir ciclo ni hacer petición HTTP.
- Validación de email: 254 octetos ASCII válidos; 255 octetos, Unicode, más de un destinatario, controles y CR/LF inválidos. Los mismos casos cubren alta y `/prueba`; un dato inválido no crea encabezados ni hace HTTP. Una reserva histórica inválida llega a `AGOTADO/DESTINATARIO_INVALIDO` y produce cero POST Gmail. Remitente o asunto con CR/LF se rechazan antes de OAuth/Gmail; logs y excepciones no incluyen la dirección completa.
- El transporte hace como máximo un POST Gmail por ciclo, no sigue redirecciones y no modifica los reintentos usados por OTAs.
- Cada llamada OAuth/Gmail respeta timeout de 20 segundos, el ciclo el deadline de 50 segundos y el lease es de 120 segundos; duraciones cortas inyectables en pruebas. Un servidor local que bloquea OAuth acaba en `REINTENTO` con cero POST Gmail; uno que bloquea Gmail acaba en `RESULTADO_INCIERTO` con exactamente un POST. Respuesta tardía después del lease no sobrescribe el estado ni dispara otro POST. Máximo de ciclos, esperas persistidas, Retry-After como segundos/fecha/ausente/malformado/>24h, lease vencido en ambas fases y ausencia de solapamiento cubiertos.
- Tras reiniciar con configuración corregida, filas bloqueadas pasan a `PENDIENTE` conservando su contador; credenciales aún inválidas las vuelven a bloquear. La restauración rechaza altas durante mantenimiento, pone en cuarentena solo filas recuperadas y permite procesar una nueva reserva tras levantar el modo.
- Un resultado incierto o una fila recuperada de respaldo antiguo no causa reenvío automático.
- Seguridad administrativa de lista, paginación por cursor, límite 100 y ausencia de PII/secretos comprobados.
- Ninguna operación de correo cambia precio, estado, disponibilidad o inventario de reserva.

### Interfaz y operación

- PaginaAdminIntegraciones prueba carga/error/vacío, todos los estados, cursor/paginación y error independiente del estado OTA.
- Estilos SCSS centralizados; teclado, foco visible, lectura por tecnología asistiva y ausencia de desbordamiento móvil verificados.
- Evidencia visual real de escritorio 1440 × 1000 y móvil 390 × 844, con datos sintéticos y consola sin errores atribuibles a la página.
- docs/operations/correo.md describe el envío automático, estados, diferencias entre aceptación/entrega y reinicio requerido al corregir entorno.
- docs/operations/despliegue-respaldos.md contiene pausa, restauración, cuarentena, conteo de filas y reanudación; la prueba sintética confirma que las filas antiguas quedan inciertas y que solo las nuevas son elegibles.
- Suite API, suite web, typechecks y builds pasan; la migración y restauración se verifican sobre SQLite temporal.
- Gmail real no forma parte de la suite ni se declara verificado.

## 13. Decisiones que el usuario debe revisar

La recomendación de esta especificación es aprobar el alcance de solo lectura, sin reenvío/corrección desde el panel, con estas reglas: cinco ciclos y backoff 1/5/15/60 minutos; Retry-After válido hasta 24 horas se respeta; un plazo mayor termina en AGOTADO; no se copia el contenido del correo a outbox; credenciales corregidas requieren reinicio; y tras restaurar una copia antigua todas las filas no terminales recuperadas se ponen en cuarentena como RESULTADO_INCIERTO antes de reactivar el worker.

También requieren revisión explícita: validar una sola dirección ASCII de hasta 254 octetos en reserva, envío de prueba y despacho; mapear una reserva ausente a `AGOTADO/RESERVA_NO_DISPONIBLE`; usar una instancia y una fila por ciclo; sondear cada cinco segundos; aplicar timeout de 20 segundos por petición, deadline de ciclo de 50 segundos y lease de 120 segundos; reactivar filas de configuración como `PENDIENTE` tras reinicio sin reiniciar su contador; y detener altas y envíos de prueba durante la cuarentena de restauración.

La decisión más relevante de privacidad es reconstruir el mensaje desde la reserva vigente en vez de guardar una instantánea personal. Eso significa que se usa el email actual al momento de despacho; el cuerpo propuesto es genérico para no repetir fechas o estado desactualizado. La alternativa de congelar destinatario/contenido da fidelidad al momento de reserva, pero duplica PII y requiere una política explícita de retención y respaldos.

Al aprobar o comentar este documento, el usuario también puede pedir una acción administrativa auditada para resolver RESULTADO_INCIERTO. Sin esa ampliación, la vista seguirá siendo de solo lectura y las filas que no puedan conciliarse permanecerán inciertas, sin reenvío automático.

### Referencias primarias

- Gmail API, envío: https://developers.google.com/workspace/gmail/api/reference/rest/v1/users.messages/send
- Gmail API, errores y límites: https://developers.google.com/workspace/gmail/api/guides/handle-errors
- IETF, HTTP Semantics, Retry-After: https://www.rfc-editor.org/rfc/rfc9110.html#section-10.2.3
