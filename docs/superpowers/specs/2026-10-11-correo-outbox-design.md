# R213 — Outbox durable para avisos de reserva

- **Fecha inicial:** 2026-10-11
- **Última revisión:** 2026-10-12 (revisión independiente: CAS de fases, señal persistente de restauración y puerta de arranque)
- **Estado:** el usuario aprobó el alcance funcional de R213 el 2026-10-12. Esta revisión incorpora controles técnicos recomendados que se presentan junto con el plan detallado; no implementar hasta que el usuario apruebe el plan revisado.
- **Producto:** HOTEL-OS, reservas directas y panel administrativo.

## 1. Resultado buscado

Que cada reserva nueva conserve de forma durable la intención de enviar su aviso, aunque Gmail falle o la aplicación se reinicie. La reserva y la tarea de correo se guardan juntas en SQLite; un trabajador las procesa fuera del camino síncrono de la petición; administración puede consultar el resultado sin acceder al contenido personal del mensaje.

La respuesta HTTP de reserva no espera a Gmail. El trabajador puede comenzar después del commit y antes de que la conexión HTTP haya terminado de responder: la garantía es que el envío ocurre fuera de la transacción y del camino síncrono, no que ocurra después de observar la respuesta en el navegador.

## 2. Qué aprobó el usuario y siguiente puerta

La dirección funcional fue aprobada por el usuario el 2026-10-12:

1. Crear la tarea de correo en SQLite junto con la reserva.
2. Enviar en segundo plano con reintentos automáticos acotados.
3. Hacer visible el estado solo para administración.

Los detalles de implementación de este documento —incluidos el límite de respuesta, el CAS previo a HTTP y la señal lateral de restauración— se presentan para revisión junto con el plan. La aprobación del alcance funcional no autoriza cambios de código.

**No empezar cambios de código** hasta que el usuario revise y apruebe el plan detallado por separado.

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
- Un solo POST a Gmail por intento de envío; los reintentos ocurren únicamente mediante una nueva ejecución programada del outbox.
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
- estado, fase, número máximo y número actual de intentos;
- creación, último intento, próximo intento, lease y versión de reclamación;
- clasificación segura del último resultado y, si existe, el id de mensaje de Gmail.
- estado y resultado más reciente únicamente; esta versión no agrega una bitácora append-only de cada cambio.

Las marcas de tiempo de creación, intento, siguiente intento y lease representan instantes UTC en un formato uniforme. La comparación de vencimientos no depende de la zona horaria del hotel ni de cambios de horario estacional.

**Propuesta de privacidad:** no duplicar en outbox el email, nombre, asunto ni cuerpo. El trabajador recupera la reserva vigente al enviar y usa su email actual. El mensaje se simplifica a una constancia genérica de recepción con el código y un enlace para consultar el estado vigente; no copia fechas, nombre ni un estado temporal. Si la reserva se canceló antes del despacho, el texto sigue siendo solo una constancia de que se recibió una solicitud y el enlace presenta el estado vigente. Si falta la reserva o el email no es utilizable, no se envía: pasa a `AGOTADO` con la clasificación allowlist `RESERVA_NO_DISPONIBLE` o `DESTINATARIO_INVALIDO`, respectivamente, sin llamar a OAuth ni a Gmail ni consumir un intento adicional. La migración propone una FK con `ON DELETE RESTRICT`; la clasificación de reserva ausente cubre corrupción o datos legados inconsistentes.

Esta elección evita crear una segunda copia de datos personales, pero implica que un cambio de email antes del despacho dirige el aviso a la dirección actualmente guardada. Si el usuario prefiere congelar destinatario y contenido al crear la reserva, esa alternativa exige definir retención del payload y presencia en respaldos antes de escribir código; no está implícita en esta propuesta.

La tabla no guarda cuerpos HTTP, cabeceras crudas, tokens OAuth, autorización, email completo ni mensajes de error libres. La API administrativa tampoco los expone. La fila conserva `reservation_id` como relación, con borrado restringido mientras exista el aviso. Si una reserva aun así no está disponible al procesar el evento, se aplica `AGOTADO/RESERVA_NO_DISPONIBLE`. La retención de la fila sigue a la reserva y la retención existente de la base; esta ronda no promete borrado físico ni vencimiento distinto en respaldos.

## 7. Flujo transaccional y trabajador

### Al crear la reserva

1. Dentro de la transacción actual, guardar reserva, línea, historial y tarea AVISO_RESERVA.
2. Si falla la inserción del outbox, revertir la creación completa.
3. Si se encuentra la reserva por idempotencia, responder como hoy sin insertar otra tarea.
4. Finalizar la operación HTTP sin canjear OAuth ni llamar a Gmail.

### Al procesar la tarea

1. Reclamar una sola fila mediante una transacción corta, incrementando versión de reclamación y lease, pero no el contador de intentos. No se reclaman lotes que puedan esperar en memoria más tiempo que el lease.
2. Leer la reserva y la configuración; ninguna llamada de red ocurre mientras SQLite está en transacción.
3. Antes de cada llamada externa, persistir la fase con una operación CAS que compruebe estado ENVIANDO, lease vigente y versión de reclamación actual. La transición RECLAMADA→TOKEN_OAUTH o RECLAMADA→GMAIL_SEND incrementa una vez el contador; TOKEN_OAUTH→GMAIL_SEND conserva ese mismo número de intento. La operación devuelve el claim y la versión actualizados. Si el CAS modifica cero filas, no se hace la llamada HTTP. Una caída después de confirmar el CAS y antes de que la petición alcance la red consume conservadoramente el intento, porque no se puede demostrar que no salió.
4. Usar transporte específico de Google sin reintentos ocultos ni seguimiento de redirecciones; no alterar la política HTTP de los conectores OTA.
5. Guardar la clasificación del resultado con una actualización condicional por estado, lease y la última versión devuelta por el CAS de fase. Una respuesta tardía de una reclamación antigua no cambia el estado vigente.
6. Ejecutar un solo worker dentro de una instancia de la aplicación, con prevención de solapamiento. El intervalo de sondeo propuesto es cinco segundos y se procesa como máximo una fila por sondeo; esta versión no habilita réplicas activas ni workers externos sobre la misma base SQLite.

La fase persistida es `RECLAMADA` al obtener una fila; cambia a `TOKEN_OAUTH` o `GMAIL_SEND` únicamente con el CAS inmediatamente anterior a la llamada. Así, el reinicio distingue una llamada OAuth iniciada de un envío Gmail iniciado. El CAS también incrementa la versión de reclamación y devuelve un claim renovado que el worker debe usar en toda escritura posterior. La reclamación de un intento nuevo reinicia la fase a `RECLAMADA`; el intento anterior ya está reflejado en el contador.

### Presupuesto de tiempos propuesto

- El transporte específico de Google permite como máximo una petición OAuth y un POST a Gmail por intento de envío, con timeout de 20 segundos para cada petición y sin reintentos de transporte ni redirecciones.
- Cada respuesta HTTP de OAuth y Gmail se lee con un límite de 64 KiB. Una respuesta OAuth sobredimensionada se clasifica como respuesta inválida de configuración; después de iniciar Gmail, una respuesta sobredimensionada o que no permita confirmar un id válido queda `RESULTADO_INCIERTO`. No se conserva ni registra el cuerpo crudo.
- El deadline total del intento es 50 segundos desde la reclamación, incluidos trabajo local y persistencia. Si vence antes de iniciar `GMAIL_SEND`, se registra `REINTENTO`; si ya inició ese POST, se registra `RESULTADO_INCIERTO`.
- El lease propuesto es de 120 segundos: supera el deadline total en 70 segundos para dar margen a persistir y recuperar el estado. Los valores deben ser configurables para las pruebas, sin cambiar los límites de producción.
- Un lease vencido en `TOKEN_OAUTH` vuelve a ser elegible para reintento; como el intento y la fase se persisten antes de la llamada OAuth, ese intento cuenta aunque el proceso caiga antes de recibir respuesta. Un lease vencido en `GMAIL_SEND` se convierte en `RESULTADO_INCIERTO`, limpia la reclamación e incrementa la versión para que la respuesta tardía no pueda sobrescribir el estado.
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
| AGOTADO | Estado terminal sin reintento automático: se agotaron los intentos, hubo un rechazo permanente o una espera superior al límite, o una validación previa impide enviar (por ejemplo, reserva ausente o destinatario inválido). Puede tener desde cero intentos; la clasificación segura explica el motivo. Requiere revisión. |

### Reintentos

Propuesta para revisión del plan: máximo de cinco intentos por tarea que llegan a iniciar una llamada externa, tanto si la primera llamada es OAuth como si se usa un token vigente y se llama directamente a Gmail. El contador se incrementa inmediatamente antes de iniciar esa llamada. Esperas base entre intentos: 1, 5, 15 y 60 minutos. Un fallo OAuth temporal consume el intento y puede reintentarse; un agotamiento del lease durante Gmail no se reintenta automáticamente porque el resultado es incierto. Falta de configuración, reserva ausente o destinatario inválido se resuelven antes de la primera llamada y no consumen intento. Si un fallo temporal ocurre en el quinto intento, la tarea pasa a `AGOTADO` en vez de programar otra espera; se conservan la clasificación segura del último resultado y el contador 5/5, y nunca se permite superar cinco intentos.

- Gmail 429 y Gmail 403 con reason explícito rateLimitExceeded o userRateLimitExceeded: REINTENTO.
- Gmail 401 o 403 con reason de credencial/permisos, como authError o domainPolicy: BLOQUEADO_CONFIGURACION.
- Gmail 403 con otra razón, otros 4xx permanentes o reason desconocido: AGOTADO; nunca tratar cualquier 403 como límite de cuota ni como error de configuración sin evidencia.
- Fallo de red, 429 o 5xx de OAuth antes del envío: reintento acotado. OAuth 401, invalid_grant, invalid_client o respuesta 2xx sin access_token: BLOQUEADO_CONFIGURACION. Un 4xx OAuth desconocido queda AGOTADO con clasificación segura.
- Timeout o pérdida de conexión después de iniciar GMAIL_SEND, respuesta Gmail 5xx, redirección o Gmail 2xx sin id no vacío: RESULTADO_INCIERTO; no hacer otro POST automáticamente. Aunque la guía de Gmail recomienda backoff para errores 5xx en general, aquí prima evitar un correo duplicado: este POST tiene efecto externo y la respuesta no demuestra si Gmail alcanzó a aceptarlo. No existe en este diseño una clave de idempotencia del proveedor que permita repetirlo sin riesgo.
- Falta de configuración detectada antes de llamar a OAuth: BLOQUEADO_CONFIGURACION sin consumir intento.
- Para volver a intentar tras corregir variables de entorno se debe reiniciar la aplicación. Una sola vez por arranque, después de migrar la base y cargar la configuración pero antes de iniciar el worker, una transacción cambia a `PENDIENTE` las filas `BLOQUEADO_CONFIGURACION` cuyo contador sea menor que cinco; conserva el contador y no crea filas. Si una fila ya consumió cinco intentos, pasa a `AGOTADO`, conserva el último resultado seguro y no se llama a OAuth ni a Gmail. El worker procesa las filas reactivadas; si la configuración aún falla, vuelven a `BLOQUEADO_CONFIGURACION`. No se intenta demostrar que el entorno cambió: cualquier reinicio puede habilitar una fila una vez, por lo que un reinicio sin corregir credenciales también puede hacer un intento mientras quede presupuesto. El sondeo durante el mismo proceso no vuelve a habilitarlas ni consume intentos. Toda selección del worker comprueba el máximo dentro de la reclamación y no inicia llamadas para una fila con contador igual o mayor al límite.

### Retry-After

RFC 9110 admite segundos enteros no negativos o una fecha HTTP. Propuesta:

1. Parsear ambos formatos sin guardar ni exponer la cabecera cruda.
2. Si el valor es válido y no excede 24 horas, programar el mayor entre ese plazo y la espera base.
3. Si falta o está malformado, usar la espera base.
4. Si pide más de 24 horas, no enviar antes del plazo del proveedor: pasar a AGOTADO con un código seguro de espera excesiva para revisión.
5. El máximo de cinco intentos se conserva incluso cuando uno de ellos queda programado a muchas horas.

Gmail indica que ciertos límites diarios pueden producir 429 durante varias horas; por eso el sistema no acorta una espera válida del proveedor. El umbral de 24 horas es una propuesta para balancear espera y utilidad del aviso, pendiente de aprobación con el plan.

## 9. Restauración de respaldos

Una caída y reinicio con la base actual no equivalen a restaurar una copia antigua: los efectos externos de Gmail no vuelven atrás cuando se restaura SQLite. El modo de mantenimiento por sí solo no prueba que hubo una restauración, porque también puede usarse en una pausa operativa normal.

Procedimiento propuesto para restaurar una base que ya contiene la tabla del outbox. Requiere `HOTEL_MODO_MANTENIMIENTO` (por defecto `false`), `CORREO_OUTBOX_ACTIVO` (por defecto `true`) y una señal lateral persistente en el volumen de datos: `/data/.correo-outbox-restauracion-pendiente`. La herramienta de restauración crea esa señal antes de sustituir la base y la deja para que la API la consuma. Solo la señal identifica una restauración; mantenimiento normal con la señal ausente no pone filas en cuarentena. Con mantenimiento activo, las rutas HTTP que crean o modifican reservas y `/api/admin/correo/prueba` responden 503 con el código `MANTENIMIENTO_ACTIVO`; salud y consulta administrativa de solo lectura pueden seguir disponibles. El worker no inicia ni reclama tareas aunque `CORREO_OUTBOX_ACTIVO` estuviera en `true`.

1. Configurar `HOTEL_MODO_MANTENIMIENTO=true` y `CORREO_OUTBOX_ACTIVO=false`; detener la aplicación de forma ordenada y esperar a que termine cualquier transacción en curso. No restaurar mientras la instancia anterior siga atendiendo solicitudes.
2. La herramienta valida la copia candidata y la copia de seguridad del destino. Justo antes del reemplazo atómico, crea de forma atómica la señal lateral y la sincroniza a disco. Si una restauración falla y la recuperación del estado anterior se valida, retira la señal; si la recuperación no se puede demostrar, la conserva.
3. Arrancar la API con ambas banderas activas. Después de aplicar Flyway y antes de exponer la aplicación como lista, el arranque detecta la señal. Si falta mantenimiento o el worker sigue activo, el proceso falla cerrado: no acepta mutaciones ni inicia llamadas externas. Sin señal, una pausa normal no ejecuta cuarentena.
4. Con señal y ambas banderas correctas, ejecutar una única operación transaccional SQLite (`BEGIN IMMEDIATE`) de cuarentena: todas las filas recuperadas en `PENDIENTE`, `ENVIANDO`, `REINTENTO` o `BLOQUEADO_CONFIGURACION` pasan a `RESULTADO_INCIERTO`, reciben `RESPALDO_RESTAURADO_REVISAR` y limpian lease y próximo intento. Las filas `ACEPTADO`, `RESULTADO_INCIERTO` y `AGOTADO` conservan su estado.
5. Después del commit, consultar de nuevo y verificar cero filas elegibles; registrar conteos por estado. Solo después de esa verificación se elimina y sincroniza la señal. Si la transacción, la lectura de verificación o la eliminación de la señal falla, no se habilita el worker ni las mutaciones; el arranque siguiente repite la verificación de forma segura.
6. Conservar el modo de mantenimiento y el worker apagado hasta que operaciones revise el resultado; después detener la API, poner `HOTEL_MODO_MANTENIMIENTO=false` y `CORREO_OUTBOX_ACTIVO=true`, y reiniciar. Las filas inciertas recuperadas no se reenvían automáticamente; las nuevas tareas sí son elegibles.

Esta decisión es deliberadamente conservadora: una fila pudo no haberse enviado y aun así quedará incierta. El personal puede revisar Enviados por código, destinatario y ventana temporal y contactar al huésped por otro canal cuando no haya certeza. La búsqueda manual no actualiza el estado de la app en la propuesta de solo lectura; no se añade permiso Gmail de lectura ni un botón de resolución. Por tanto, RESULTADO_INCIERTO puede permanecer visible. Una transición administrativa auditada para registrar conciliación es una ampliación que el usuario debe aprobar antes de planear.

Una copia anterior a la creación de la tabla no contiene tareas de outbox; esas reservas históricas no se regeneran automáticamente. No se envían correos retroactivos al aplicar la migración.

## 10. Consulta administrativa

Propuesta de API de solo lectura bajo /api/admin/**:

- GET /api/admin/correo/outbox?estado=&limite=&cursor=
- límite predeterminado 25 y máximo 100; cursor opaco estable ordenado por creado_en DESC e id DESC.
- devolver código de reserva, estado, intento actual/máximo, marcas de creación/último/próximo intento y clasificación allowlist.
- no devolver email, nombre, asunto, cuerpo, id de Gmail, token, Authorization, texto libre ni respuesta original de Google.
- una sección independiente en PaginaAdminIntegraciones muestra carga, error, vacío, paginación y todos los estados. ACEPTADO debe decir “Aceptado por Gmail; no confirma entrega”; AGOTADO, “No se enviará automáticamente”, acompañado de su clasificación segura. El contador cero es válido para una tarea agotada durante las validaciones previas.
- estados se identifican con texto y no solo color. Controles accesibles con teclado, foco visible y avisos anunciados.

Cursor en lugar de offset para ordenar páginas de forma determinista sin permitir offsets enormes. El endpoint requiere rol administrador; huéspedes y anónimos no pueden consultarlo.

## 11. Validación y privacidad al generar el mensaje

R213 cambia el transporte del correo y pasa la dirección de reserva al trabajador. El límite propuesto de 254 octetos se basa en el máximo del forward-path SMTP (RFC 5321 §4.5.3.1.3: https://www.rfc-editor.org/rfc/rfc5321.html#section-4.5.3.1.3). La política operativa completa y sus casos están especificados a continuación.

La regla de dirección se aplica antes de crear encabezados o hacer cualquier petición HTTP. Primero rechaza todo control ASCII (incluidos CR, LF y tabulador) y todo carácter no ASCII en el valor recibido; luego recorta únicamente espacios ASCII exteriores (`U+0020`). Acepta exactamente un buzón ASCII de forma `local@dominio`: sin nombre visible, comentarios, grupos, parte local entrecomillada, separadores de lista ni dominio literal; exactamente un `@`; parte local dot-atom de máximo 64 octetos, sin punto inicial/final ni puntos consecutivos; dominio con al menos dos etiquetas, cada una de 1 a 63 caracteres ASCII alfanuméricos con guiones solo en posiciones interiores, y sufijo alfabético de al menos dos letras; longitud total máxima de 254 octetos incluido `@`. El valor tras recortar espacios se valida, se persiste y se usa al enviar; no se cambia el uso de mayúsculas/minúsculas. No registrar el valor completo. Aplicar la misma regla al alta de reserva, al envío de prueba administrativo y al despacho desde outbox. El alta inválida se rechaza antes de persistir reserva/outbox; una reserva histórica con dirección inválida termina como `AGOTADO/DESTINATARIO_INVALIDO`, sin llamada OAuth ni POST Gmail.

El remitente pasa la misma validación de buzón simple; nombre visible del remitente y asunto rechazan CR/LF y controles antes de cualquier llamada a OAuth. El cuerpo puede contener saltos de línea porque se escribe después del separador entre cabeceras y cuerpo, nunca interpolado dentro de una cabecera. Los errores de Google se reducen a estados y códigos allowlist; ningún mensaje crudo del proveedor, token o cabecera aparece en logs o API. Las pruebas usan servidores HTTP locales y dominios `.test`; no se usan credenciales reales ni se manda correo real.

## 12. Criterios de aceptación para el futuro plan

### API y base de datos

- Prueba SQLite demuestra que reserva y outbox se confirman juntas y que un fallo del insert revierte reserva, línea e historial.
- Una reserva repetida por idempotencia conserva una sola tarea y no hace llamadas de red.
- Gmail sin configuración no impide guardar la reserva.
- Gmail 2xx con id, 2xx sin id, 401, 429, 403 por cuota, credencial/permisos o reason desconocido, rechazo permanente, timeout, 5xx, redirección y OAuth fallido producen los estados definidos.
- OAuth 2xx sin token no llama a Gmail; 401/invalid_grant/invalid_client bloquean configuración; un arreglo de variables requiere reiniciar y recupera la misma fila.
- Configuración ausente se bloquea antes de OAuth, sin llamada HTTP ni intento. Reserva ausente o dirección inválida en una reserva persistida termina en `AGOTADO` con `RESERVA_NO_DISPONIBLE` o `DESTINATARIO_INVALIDO`, sin consumir intento ni hacer petición HTTP.
- Validación de email: buzón simple válido de 254 octetos aceptado; 255, Unicode, nombre visible, parte local entrecomillada, dominio literal, etiqueta inválida, punto inicial/final/consecutivo en local, local mayor de 64, más de un destinatario, controles y CR/LF (también al inicio/final) rechazados. Los mismos casos cubren alta y `/prueba`; un dato inválido no crea encabezados ni hace HTTP. Una reserva histórica inválida llega a `AGOTADO/DESTINATARIO_INVALIDO` y produce cero POST Gmail. Remitente, nombre visible o asunto con CR/LF se rechazan antes de OAuth/Gmail; logs y excepciones no incluyen la dirección completa.
- El transporte hace como máximo un POST Gmail por intento, no sigue redirecciones y no modifica los reintentos usados por OTAs.
- El límite de lectura para respuestas OAuth/Gmail es 64 KiB; las pruebas incluyen cuerpos por encima del límite y verifican que no se guardan ni registran.
- Antes de cada llamada HTTP, un CAS persiste fase y versión; si no actualiza exactamente la fila reclamada, se comprueba que el servidor local recibió cero llamadas. OAuth→Gmail consume un solo intento y el envío directo Gmail consume uno.
- Cada llamada OAuth/Gmail respeta timeout de 20 segundos, cada intento tiene deadline de 50 segundos y el lease es de 120 segundos; duraciones cortas inyectables en pruebas. Un servidor local que bloquea OAuth acaba en `REINTENTO` con cero POST Gmail; uno que bloquea Gmail acaba en `RESULTADO_INCIERTO` con exactamente un POST. Respuesta tardía después del lease no sobrescribe el estado ni dispara otro POST. Máximo de intentos, esperas persistidas, Retry-After como segundos/fecha/ausente/malformado/>24h, lease vencido en ambas fases y ausencia de solapamiento cubiertos.
- Tras cualquier reinicio, filas bloqueadas con menos de cinco intentos pasan una sola vez a `PENDIENTE` conservando su contador; las que ya llegaron a cinco quedan `AGOTADO`, conservan la clasificación segura previa y no provocan petición. Credenciales aún inválidas vuelven a bloquear las reactivadas. Se prueba que el mismo proceso no repite la transición y que un reinicio sin cambios puede provocar un intento adicional cuando queda presupuesto; ningún camino supera cinco intentos, incluido un fallo transitorio en el intento 5/5, que queda `AGOTADO`. `AGOTADO` es terminal y puede tener cero intentos cuando la validación previa impide enviar. La restauración requiere la señal lateral persistente además de `HOTEL_MODO_MANTENIMIENTO=true` y `CORREO_OUTBOX_ACTIVO=false`; mantenimiento sin señal no pone en cuarentena. La API falla cerrada si la señal llega con configuración incompatible, el worker no se habilita antes de Flyway, recuperación, cuarentena y verificación, y la señal solo se elimina después del commit y la lectura de cero elegibles. Se prueba el ciclo del marcador en restauración correcta, rechazo previo, rollback verificado e interrupción sin rollback verificable.
- Un resultado incierto o una fila recuperada de respaldo antiguo no causa reenvío automático.
- Seguridad administrativa de lista, paginación por cursor, límite 100 y ausencia de PII/secretos comprobados.
- Cursor de la última página es nulo; orden estable con marcas `creado_en` repetidas y cursor malformado cubiertos.
- La operación deja una señal periódica estructurada de actividad del worker y antigüedad de la tarea elegible más antigua, sin código de reserva, identificadores de fila, correo, secretos ni cuerpos del proveedor.
- Ninguna operación de correo cambia precio, estado, disponibilidad o inventario de reserva.

### Interfaz y operación

- PaginaAdminIntegraciones prueba carga/error/vacío, todos los estados, cursor/paginación y error independiente del estado OTA.
- Estilos SCSS centralizados; teclado, foco visible, lectura por tecnología asistiva y ausencia de desbordamiento móvil verificados.
- Evidencia visual real de escritorio 1440 × 1000 y móvil 390 × 844, con datos sintéticos y consola sin errores atribuibles a la página.
- docs/operations/correo.md describe el envío automático, estados, diferencias entre aceptación/entrega y reinicio requerido al corregir entorno.
- docs/operations/despliegue-respaldos.md contiene pausa, restauración, cuarentena, conteo de filas y reanudación; la prueba sintética confirma que las filas antiguas quedan inciertas y que solo las nuevas son elegibles.
- Suite API, suite web, typechecks y builds pasan; la migración y restauración se verifican sobre SQLite temporal.
- Gmail real no forma parte de la suite ni se declara verificado.

## 13. Alcance funcional aprobado y puerta de ejecución

El alcance funcional aprobado es registrar una tarea outbox junto con cada reserva nueva, procesarla en segundo plano con reintentos limitados y mostrar el estado solo a administración. Para concretarlo, esta especificación recomienda una interfaz de solo lectura sin reenvío, un worker único y como máximo una tarea por sondeo de cinco segundos, hasta cinco intentos con esperas base de 1, 5, 15 y 60 minutos, `Retry-After` de hasta 24 horas, timeout de 20 segundos por petición, deadline de 50 segundos y lease de 120 segundos. Estos límites y detalles están incluidos en el plan revisado para aprobación; un POST Gmail de resultado incierto nunca se repite automáticamente en la propuesta.

Los detalles operativos propuestos para aprobación incluyen: una reserva ausente queda `AGOTADO/RESERVA_NO_DISPONIBLE`; una dirección inválida queda `AGOTADO/DESTINATARIO_INVALIDO`; filas bloqueadas por configuración se reactivan una vez en cada reinicio si conservan intentos disponibles; y una restauración detiene la instancia anterior y requiere la señal lateral persistente `/data/.correo-outbox-restauracion-pendiente` junto con `HOTEL_MODO_MANTENIMIENTO=true` y `CORREO_OUTBOX_ACTIVO=false`. El mantenimiento normal sin esa señal no pone tareas en cuarentena. El arranque queda cerrado hasta migrar, aplicar/verificar cuarentena y consumir la señal. La validación de direcciones usa el buzón ASCII simple descrito en la sección 11, sin nombres visibles, comentarios, listas ni dominios literales. Estos detalles solo se implementan después de la aprobación explícita del plan.

La principal decisión de privacidad es reconstruir el mensaje desde la reserva vigente en vez de guardar una instantánea personal. Se usa el correo guardado al momento del despacho y un cuerpo genérico para no repetir fechas o estado desactualizado. Congelar destinatario y contenido daría fidelidad al momento de reserva, pero duplicaría datos personales y exigiría decidir retención y presencia en respaldos.

**Propuesta para revisión:** `RESULTADO_INCIERTO` queda visible en el panel de solo lectura y exige conciliación fuera de la aplicación; nunca se reintenta automáticamente. El alcance funcional aprobado no autoriza cambios de código; estos detalles y el plan se revisan antes de implementar.

### Referencias primarias

- Gmail API, envío: https://developers.google.com/workspace/gmail/api/reference/rest/v1/users.messages/send
- Gmail API, errores y límites: https://developers.google.com/workspace/gmail/api/guides/handle-errors
- IETF, HTTP Semantics, Retry-After: https://www.rfc-editor.org/rfc/rfc9110.html#section-10.2.3
- IETF, SMTP, límite de octetos del forward-path: https://www.rfc-editor.org/rfc/rfc5321.html#section-4.5.3.1.3
