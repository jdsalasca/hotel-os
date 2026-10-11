# R213 — Outbox durable para avisos de reserva

- **Fecha:** 2026-10-11
- **Estado:** revisión técnica interna completada; versión revisada pendiente de aprobación del usuario. La dirección de usar SQLite y un trabajador con reintentos limitados fue aprobada. No hay plan de implementación ni cambios de código todavía.
- **Producto:** HOTEL-OS, reserva directa y panel administrativo.

## 1. Propósito

Evitar que una reserva directa quede sin una notificación recuperable cuando Gmail no está configurado, responde con error o la aplicación se reinicia durante el envío. La reserva y la intención de avisar deben persistir juntas; el envío externo debe poder reintentarse y su resultado debe ser visible al personal autorizado.

La reserva sigue siendo la operación principal: una caída de Gmail nunca libera inventario ni revierte una reserva ya confirmada por el servidor. El estado del correo no se presenta al huésped como prueba de entrega.

## 2. Evidencia del estado actual

- ReservaService ejecuta alta, inventario, historial e idempotencia dentro de SqliteTransactionExecutor. Después de cerrar esa transacción llama a avisarPorCorreo.
- avisarPorCorreo invoca Gmail en la petición web. Si falla, escribe un log y deja intacta la reserva; no guarda una tarea que pueda sobrevivir un reinicio.
- El reintento HTTP idempotente no llama de nuevo a avisarPorCorreo. Esto evita un correo repetido en el reintento de reserva, pero tampoco recupera el aviso fallido.
- CorreoService envía mediante Gmail API y devuelve ResultadoEnvio. El worker debe reutilizar esa integración y no afirmar éxito si no recibe aceptación del proveedor.
- HttpClienteOta reintenta fallos de red y respuestas 5xx para sus POST. El POST de Gmail que envía el mensaje no tiene garantía de idempotencia documentada por la API; una respuesta perdida puede ser ambigua. R213 debe evitar ocultar reintentos de envío dentro del cliente HTTP y mostrar esa ambigüedad al administrador.
- La base es SQLite, WAL, transacción IMMEDIATE y una instancia de aplicación con un escritor. La solución debe funcionar con esa operación sin añadir un broker ni cambiar de base de datos.
- CorreoAdminController ya es exclusivo de administración por la regla de seguridad /api/admin/**. PaginaAdminIntegraciones es el panel existente más próximo para mostrar el estado de correo e integraciones.

Referencias técnicas: ReservaService, CorreoService, ResultadoEnvio, HttpClienteOta, SqliteTransactionExecutor, SqliteDataSources, CorreoAdminController y PaginaAdminIntegraciones. Gmail documenta el envío como un POST a `users.messages.send` y devuelve un `Message` cuando tiene éxito: [método de envío](https://developers.google.com/workspace/gmail/api/reference/rest/v1/users.messages/send). La [guía oficial de errores](https://developers.google.com/workspace/gmail/api/guides/handle-errors) identifica `rateLimitExceeded` y `userRateLimitExceeded` como errores HTTP 403 que requieren backoff; la clasificación no puede basarse únicamente en la clase HTTP. Google también recomienda reintentar errores 5xx. R213 se aparta de ese consejo general únicamente para la operación `messages.send`: el POST no tiene garantía documentada de idempotencia y un 5xx o timeout puede dejar incierto si el mensaje fue aceptado; esos resultados requieren revisión manual antes de repetir el envío. Para `Retry-After`, seguir la sintaxis de segundos o fecha HTTP de [RFC 9110 §10.2.3](https://www.rfc-editor.org/rfc/rfc9110.html#section-10.2.3).

### Hallazgos de precisión que el plan debe resolver

- La implementación actual sí manda el aviso al huésped después de confirmar la transacción de reserva (`ReservaService.avisarPorCorreo`), para toda alta nueva que alcanza ese método; una reserva repetida por idempotencia no lo vuelve a mandar. La sección «Qué NO hace este sistema» de `docs/operations/correo.md` hoy contradice ese código al afirmar que el correo solo se envía desde el panel. Corregirla dentro de R213 y describir el outbox como el mecanismo durable del aviso automático.
- `HttpClienteOta.post` repite en secreto los POST ante errores de red, 429 y 5xx; `CorreoService` usa ese mismo camino para OAuth y Gmail. La respuesta actual de Gmail 2xx sin `id` termina como éxito con el texto `sin-id`. R213 debe dar a `CorreoService` un transporte de un solo intento para OAuth y Gmail, dejar que el outbox sea la única capa de reintentos, exponer fase/status/cuerpo JSON estructurado y cabeceras allowlist, y exigir un `id` no vacío antes de afirmar aceptación.
- El validador de reserva existente rechaza espacios y saltos de línea, pero no tiene límite explícito de longitud; el endpoint administrativo de correo de prueba solo comprueba que haya un `@`. Añadir un validador compartido y usarlo en `ReservaService`, `CorreoService`, `CorreoAdminController /prueba` y los reenvíos con dirección corregida. Acepta una sola dirección, máximo 254 octetos ASCII para el mailbox, sin controles/CRLF ni lista de destinatarios. El límite de 254 octetos usa el forward-path SMTP máximo de 256 octetos, incluidos los delimitadores angulares (RFC 5321 §4.5.3.1.3: https://www.rfc-editor.org/rfc/rfc5321.html#section-4.5.3.1.3). Aplicarlo antes de persistir o redactar encabezados; probar direcciones múltiples y entradas con CRLF. No registrar la dirección completa.
- Gmail devuelve los 403 con causas distintas que se distinguen por `error.errors[].reason`; la documentación confirma backoff para `rateLimitExceeded` y `userRateLimitExceeded`. HTTP 429 también puede corresponder al límite diario de envío y durar varias horas. Un 403 desconocido no se reintenta a ciegas; un 429 explícito sí puede reintentarse, respetando el plazo que indique la respuesta. El manual de Gmail no autoriza a inferir entrega al destinatario desde el 2xx.
- El email actual incluye «Estado: Pendiente de confirmación», pero un outbox puede entregar el mensaje horas o días después. Para que una recuperación no mande una afirmación obsoleta, el texto debe decir que la solicitud se recibió y que el estado vigente se consulta en la web; no presentar «Pendiente» como un estado actual. Este ajuste limitado del texto sí queda dentro de R213.
- El respaldo local documentado tiene retención predeterminada de 30 días; la sincronización externa mostrada usa `rclone sync` y también se menciona una rotación mensual en otro destino, pero no hay un TTL externo definido en el repositorio. Los 30 días de payload en la base activa no son una promesa de borrado total. La guía debe decir que los respaldos conservan la copia hasta vencer su propia rotación, y el despliegue real del worker queda condicionado a identificar y comprobar ese plazo externo.

## 3. Opciones evaluadas

### A. Reintentar dentro de la petición de reserva

Es el cambio más pequeño, pero mantiene al huésped esperando a Gmail y pierde el aviso si el proceso cae después de guardar la reserva. No cumple la necesidad de recuperación durable.

### B. Outbox transaccional en SQLite con despachador programado — recomendada

Guardar el mensaje pendiente en la misma transacción que la reserva. Un worker procesa filas en segundo plano, con reintentos limitados, espera creciente, recuperación por lease y una vista administrativa de estado. No añade infraestructura y aprovecha la base y la política de concurrencia actuales.

### C. Cola externa

Un broker permitiría desacoplar el envío y escalar varios workers, pero añade operación, credenciales, despliegue y otra frontera de consistencia. No corrige por sí sola la incertidumbre de un POST aceptado por Gmail cuya respuesta se perdió. No se justifica para la instancia SQLite prevista.

## 4. Invariantes del diseño

1. Una reserva nueva y su evento de correo se confirman o revierten en la misma transacción SQLite.
2. Un reintento de la misma clave de reserva no agrega un segundo evento ni envía el correo desde el hilo HTTP.
3. El trabajador nunca mantiene una transacción abierta mientras espera OAuth o Gmail.
4. Cada reclamación incrementa un token de fencing. Solo la reclamación vigente puede cambiar el estado principal; una respuesta tardía se anota en el historial, pero no sobrescribe la decisión más nueva.
5. El número de intentos automáticos es finito. Cada fila termina en aceptación del proveedor, bloqueo de configuración, revisión manual, cierre explícito o expiración, con razón y hora observables.
6. ACEPTADO significa que Gmail respondió éxito; no significa que el destinatario lo recibió, abrió o no sufrió un rebote.
7. No se promete entrega exactamente una vez. Si Gmail acepta el mensaje y la respuesta se pierde, un nuevo envío podría duplicarlo. Esa situación debe aparecer como RESULTADO_INCIERTO y requerir decisión administrativa antes de reenviar.
8. Cada intento y acción administrativa tiene un registro persistente append-only, sin cuerpo ni dirección de correo completa.
9. La administración puede ver estado y tomar una acción explícita, pero nunca recibe cuerpo, destinatario completo, secretos OAuth ni tokens en la respuesta de listado del API.
10. Las pruebas usan correos `.test` y no llaman a Gmail real.

## 5. Componentes propuestos

### Persistencia

- Añadir una migración Flyway V22 para una tabla de outbox, con referencia a la reserva y al evento anterior cuando sea una corrección/reenvío, clave de evento única, tipo de aviso, origen, generación, estado, payload necesario para enviar, contador de intentos, fechas de creación/próximo intento, lease/token de fencing, resultado del proveedor y último error saneado.
- Añadir una bitácora append-only relacionada con cada evento. Cada fila es inmutable y registra un hecho (`INTENTO_INICIADO`, resultado, vencimiento de lease, reenvío, corrección o cierre manual); no se actualiza una fila de inicio para ponerle resultado. Un intento abierto se cierra lógicamente con un nuevo hecho de vencimiento/resultado incierto. Guardar actor cuando corresponda, token de reclamación, hora UTC, fase (`TOKEN_OAUTH` o `GMAIL_SEND`), clasificación, status HTTP, `reason` allowlist de Gmail, id de mensaje recibido y código de motivo seguro. No guardar cabeceras crudas, cuerpo, dirección completa, texto libre de administración, OAuth ni tokens. El estado actual del outbox sí es una proyección mutable y se actualiza con compare-and-set.
- La migración crea triggers SQLite que rechazan `UPDATE` y `DELETE` en la bitácora; su limpieza nunca forma parte de la retención del payload. El repository solo ofrece inserción y consultas.
- Índice para recuperar eventos por estado y vencimiento. Restricciones de CHECK para estados e intentos no negativos. La tabla no sustituye ni modifica las migraciones anteriores.
- El evento inicial es único por reserva, tipo y generación inicial. Una corrección o reenvío administrativo crea una generación hija inmutable y enlazada; nunca edita ni reemplaza el payload ni borra su historial.
- Preservar el comportamiento actual: crear exactamente un evento en toda alta nueva que hoy invoca el aviso, tanto de origen `WEB` como `OTRO` del panel; un reintento idempotente no crea otro. Una futura decisión de producto puede cambiar destinatarios/orígenes, pero R213 no cambia esa regla de audiencia.
- Guardar una instantánea mínima del destinatario, asunto y contenido que se necesita para enviar el aviso generado al crear la reserva. Validar tamaños antes de persistir. Un reintento de esa generación conserva su contenido original. El payload se oculta en todas las respuestas administrativas; al vencer su retención, se borra sin borrar el historial no personal.
- La fila de estado debe incluir `version_reclamacion` monotónica, `fase_actual` (`TOKEN_OAUTH` / `GMAIL_SEND` / null), `lease_until`, `proximo_intento`, `intentos_automaticos`, `generacion`, `evento_padre_id`, `vence_payload_en` y la clave de idempotencia de acción manual (o su hash). La pareja `(reserva_id, tipo, generación=0)` hace único el aviso inicial; una clave manual repetida devuelve el mismo hijo, no crea otro.
- `vence_payload_en` se fija al crear el aviso inicial (`creado_en + 30 días`) y todas las generaciones hijas heredan exactamente ese plazo. Un reenvío/corrección no reinicia la retención ni puede prolongar la presencia del payload de esa reserva en la base activa.

### Alta de reserva

- Sustituir el envío sincrónico de ReservaService por la creación del evento outbox dentro de la transacción actual, después de insertar reserva, habitación e historial.
- Si el insert del outbox falla, toda la transacción de alta se revierte. No queda una reserva sin una intención durable de aviso.
- Usar la dirección ya normalizada y validada por `ReservaService`; no crear una cabecera `To` desde texto que admita controles o una lista de destinatarios. Al generar el mensaje, validar también remitente y asunto para impedir CRLF en encabezados.
- El contenido persistido confirma que la solicitud fue recibida y enlaza a la consulta del estado vigente; no incluye una afirmación temporal como «Estado: Pendiente de confirmación». Este cambio es necesario porque la entrega asíncrona puede ocurrir después de que el hotel actualice la reserva.
- Si la reserva se encontró por idempotencia, devolverla como hoy y no insertar ni enviar otro aviso.
- Una vez persistida, la petición termina sin esperar a OAuth ni a Gmail.

### Worker

- Añadir un servicio de despacho con reloj inyectable para pruebas y una tarea Spring programada con intervalo y tamaño de lote acotados.
- No reclamar ni iniciar una fase externa si el tiempo restante hasta `vence_payload_en` es menor al peor camino HTTP acotado (40 s con la configuración actual). Marcar EXPIRADO y borrar payload de toda la cadena antes de ese límite; una acción manual que no pueda completar antes del vencimiento responde 410 y no crea generación hija.
- Reclamar una fila y registrar el inicio del intento en una transacción breve; asignar lease de dos minutos y token de fencing creciente. El transporte del correo hace como máximo una llamada OAuth y, si esta responde con token válido, una llamada Gmail. Con el timeout actual de 20 s por llamada, el camino HTTP acotado dura hasta unos 40 s; el lease deja unos 80 s de margen para transacciones, programación y pausas breves. Si cambia cualquier timeout o se añade una fase, recalcular el lease y probar el peor caso con reloj controlado. No hay sleep/reintento dentro del intento: la fecha persistida del outbox programa la próxima ejecución. Envío y llamadas HTTP ocurren fuera de la transacción.
- `intentos_automaticos` incrementa una vez al reclamar; puede incluir fallo de token OAuth antes del envío. Si falta configuración detectada antes de reclamar, el evento se bloquea sin incrementar. El límite de cinco es por generación; una generación hija inicia su propio conteo, pero conserva el vencimiento de su evento inicial.
- Antes de cada llamada externa, escribir `fase_actual` y el hecho de inicio de esa fase en una transacción corta que compruebe lease/fencing. Si el token OAuth termina y la reclamación sigue vigente, cambiar a `GMAIL_SEND` y registrar ese inicio antes de invocar el POST. Nunca mantener abierta SQLite durante la llamada.
- Guardar resultado e historial en una transacción breve con condición de estado, token de fencing y vencimiento vigentes. Si el worker responde tarde, no sobrescribe la fila reclamada después; su resultado tardío se agrega a la bitácora para orientar revisión.
- El worker no toma otro lote si su ejecución anterior sigue activa. La serialización de SQLite y el soporte de una instancia son parte del contrato actual.
- La tarea programada tiene un solo ejecutor y un guard que impide ejecuciones superpuestas; el lease/fencing sigue siendo la protección persistente ante reinicio. El tamaño del lote y la cadencia deben ser configurables con valores predeterminados acotados, y probarse con el peor tiempo por intento.
- Si el proceso cae durante `TOKEN_OAUTH`, al vencer el lease se registra el hecho y se agenda REINTENTO: aún no se había iniciado `messages.send`. Si cae después de marcar `GMAIL_SEND`, al vencer el lease pasa a RESULTADO_INCIERTO y no se vuelve a enviar a ciegas; la administración revisa el proveedor antes de decidir. Un resultado tardío agrega un hecho, pero el fencing impide que cambie el estado vigente.
- OAuth y `users.messages.send` usan transporte de un solo intento sin reintentos ni seguimiento de redirecciones. Un intento del outbox puede ejecutar como máximo un POST de token y, tras recibir token válido, un POST de Gmail al host esperado. El error OAuth se clasifica por separado porque ocurre antes del envío y no tiene el efecto lateral de mandar el mensaje. Un 401, `invalid_grant`, `invalid_client` o respuesta exitosa sin `access_token` bloquea configuración; un fallo OAuth de red, 429 o 5xx previo al POST agenda el siguiente intento acotado en el outbox. Un OAuth 4xx desconocido no se categoriza como rate limit.
- Clasificar la respuesta Gmail usando status y `error.errors[].reason` del JSON. El cliente permite acceder a status, cuerpo estructurado y solo cabeceras allowlist (por ejemplo `Retry-After`) sin registrar ni exponer cabeceras crudas. No traducir todo 4xx a permanente ni inferir que todo 5xx fue rechazado sin ambigüedad. El `Retry-After` sigue la sintaxis HTTP estándar: segundos decimales o fecha HTTP; se parsea con límite de tamaño/rango y el vencimiento es el mayor entre ese plazo y el backoff propio. Si el valor es inválido se conserva el backoff y se registra solo un código de clasificación.

### Panel y API administrativa

- Contrato de lectura: `GET /api/admin/correo/outbox?estado=&limite=&cursor=` devuelve estado de configuración, conteos por estado y una página de eventos. `limite` predeterminado 25 y máximo 100; cursor estable ordenado por `creado_en DESC, id DESC`. Cada resumen incluye id opaco, código de reserva, origen, tipo, generación, estado, hora de creación/último intento/próximo intento/vencimiento de payload, conteo automático/manual, código de fallo allowlist y destinatario enmascarado. No incluye asunto, cuerpo, email completo, nombre del huésped, respuesta cruda del proveedor, token ni cabeceras.
- Mostrar el estado como una sección de correo en PaginaAdminIntegraciones, aprovechando navegación existente: configuración real, pendientes, en espera, bloqueados, resultados inciertos, aceptados por proveedor y agotados. Indicar hora del último intento y próximo reintento cuando existan.
- `POST /api/admin/correo/outbox/{id}/reenvios` crea una generación hija, nunca edita la original; exige `Idempotency-Key`, código de motivo y confirmación explícita cuando el origen sea incierto. El actor se deriva en el servidor del principal autenticado y nunca se acepta desde el cuerpo de la solicitud. En `RESULTADO_INCIERTO` el panel advierte que Gmail pudo haber aceptado el mensaje y exige marcar que el operador revisó la carpeta Enviados antes de continuar. Un destinatario corregido solo se admite en una generación hija con una dirección única validada. Permitir reintento sin cambio solo desde `AGOTADO`/`RESULTADO_INCIERTO`; bloquear si ya existe un descendiente activo.
- `POST /api/admin/correo/outbox/{id}/cierre` permite cerrar sin reenvío solo estados de revisión manual (`RESULTADO_INCIERTO`, `AGOTADO`, `REQUIERE_CORRECCION`), con motivo categórico obligatorio y registro append-only. El nuevo estado `CERRADO_MANUALMENTE` significa únicamente que el hotel decidió no reintentar; no implica que el correo se haya enviado o entregado.
- Las dos acciones requieren autenticación `ROLE_ADMIN`, sesión/CSRF e idempotencia. El listado no revela el email completo; el destino nuevo se acepta solo en la solicitud autenticada de corrección y nunca se copia a logs/bitácora. Una transacción IMMEDIATE valida estado de origen, límite de generaciones y ausencia de hijo activo antes de crear una única acción.
- Límite operativo propuesto para aprobación: no más de tres reenvíos manuales nuevos por reserva en 24 horas, contando todas sus generaciones; los clicks repetidos con la misma clave idempotente no consumen el límite. Este límite protege contra bucles del operador y errores de doble pulsación.
- El panel debe explicar que el sistema no prueba entrega en la bandeja del destinatario. Sin configuración autorizada, muestra pendiente de configuración y no una prueba de envío.

## 6. Estados y política de reintentos

Estados propuestos: PENDIENTE, ENVIANDO, REINTENTO, BLOQUEADO_CONFIGURACION, REQUIERE_CORRECCION, RESULTADO_INCIERTO, ACEPTADO, AGOTADO, CERRADO_MANUALMENTE y EXPIRADO.

- PENDIENTE: aún no se ha reclamado.
- ENVIANDO: reclamado con lease vigente.
- REINTENTO: el proveedor rechazó explícitamente con un error temporal; queda fecha de próximo intento.
- BLOQUEADO_CONFIGURACION: faltan datos, hay credenciales inválidas o un permiso/configuración conocida impide enviar. Si la falta se detecta antes de reclamar, no consume intento; si OAuth descubre la credencial inválida después de reclamar, ese intento queda registrado y contado. Cuando se corrige la configuración, el mismo evento vuelve a PENDIENTE con su contador existente.
- REQUIERE_CORRECCION: el destinatario guardado no supera el validador de una sola dirección antes del POST, o el proveedor entrega un código de error que permite atribuir el rechazo a formato de dirección. Solo una generación hija con la dirección corregida puede iniciar otro envío. No significa que Gmail haya comprobado que un buzón existe.
- RESULTADO_INCIERTO: no se obtuvo respuesta concluyente luego de enviar el POST. Se detiene el reenvío automático para evitar duplicado silencioso; el panel pide revisar Gmail antes de tomar una decisión.
- ACEPTADO: Gmail respondió éxito y entregó un `id` válido; guardar el id y `aceptado_en` UTC local, además de `internalDate` de Gmail solo cuando venga en la respuesta. No decir entregado. Un HTTP 2xx sin `id` válido no usa un marcador inventado ni cuenta como ACEPTADO: queda RESULTADO_INCIERTO y no se reenvía automáticamente.
- AGOTADO: se alcanzó el máximo automático o hubo un rechazo permanente/desconocido que no debe repetirse automáticamente. Se conserva el motivo seguro y se permite una decisión administrativa explícita.
- EXPIRADO: venció el plazo de 30 días heredado de la reserva inicial; se borraron los payloads de todas sus generaciones y el evento ya no se puede reenviar.
- CERRADO_MANUALMENTE: un administrador eligió no volver a enviar; no afirma éxito ni entrega. Es terminal y queda la razón categórica en la bitácora.

Política inicial propuesta: máximo cinco intentos automáticos por generación; esperas de 1, 5, 15 y 60 minutos entre fallos temporales. Son reintentables como rechazo explícito HTTP 429 y HTTP 403 cuyo `reason` Gmail sea `rateLimitExceeded` o `userRateLimitExceeded`; un 403 `dailyLimitExceeded`, `domainPolicy`, de permisos u otro motivo no incluido en la allowlist se bloquea o termina en AGOTADO según su razón segura, nunca se reintenta como rate limit por inferencia. Un 401 OAuth, `invalid_grant`, `invalid_client`, `unauthorized_client` o respuesta 2xx sin token válido bloquea configuración hasta corregir credenciales. Un OAuth 429/5xx o fallo de red antes del POST programa REINTENTO; un OAuth 4xx no reconocido va a AGOTADO con código seguro para revisión, sin reintento automático. Un 4xx de Gmail que pruebe inequívocamente una dirección inválida pasa a REQUIERE_CORRECCION; los demás 4xx permanentes pasan a AGOTADO con razón segura y revisión manual, sin reintento automático. No inferir que un error Gmail describe un buzón inexistente ni que la sintaxis válida garantiza entrega. Un error de transporte/timeout después de marcar `GMAIL_SEND`, HTTP 5xx de `messages.send` o respuesta 2xx sin `id` válido es RESULTADO_INCIERTO: por el riesgo de que Gmail ya haya aceptado el mensaje, no se repite automáticamente. Fallos transitorios del canje OAuth se distinguen del POST porque ocurren antes de mandar correo y pueden reintentarse sin riesgo de enviar el mensaje dos veces.

Si Gmail entrega `Retry-After`, interpretar segundos o fecha HTTP (RFC 9110 §10.2.3) y respetarlo como espera mínima; si falta o es inválido, usar el backoff anterior. Si pide esperar más allá del vencimiento del payload, pasa a AGOTADO para revisión en vez de enviar antes del plazo indicado. Nunca guardar la cabecera cruda. La clasificación retiene solo status y razones allowlist saneadas suficientes para explicar la decisión.

### Transiciones permitidas

| Estado actual | Evento/condición | Estado siguiente | Efecto externo |
|---|---|---|---|
| PENDIENTE | Configuración incompleta al procesar | BLOQUEADO_CONFIGURACION | Ningún POST a Gmail; no consume intento. |
| PENDIENTE / REINTENTO vencido | Reclamación compare-and-set exitosa | ENVIANDO | Registra inicio y lease antes de salir de SQLite. |
| BLOQUEADO_CONFIGURACION | La configuración ya está completa, queda intento y no venció el payload | PENDIENTE | El worker lo reevalúa; conserva generación y contador. Si no queda intento, AGOTADO; si venció, EXPIRADO. |
| ENVIANDO | Gmail 2xx con `id` válido | ACEPTADO | Elimina el payload activo en la misma actualización. |
| ENVIANDO | Rechazo temporal explícito y seguro de repetir | REINTENTO | Guarda vencimiento persistente y resultado del intento. |
| ENVIANDO | Credencial/configuración corregible | BLOQUEADO_CONFIGURACION | Detiene el intento automático hasta corregir entorno. |
| ENVIANDO / TOKEN_OAUTH | Lease vencido | REINTENTO | El envío Gmail no había empezado; aplica backoff y límite normal. |
| ENVIANDO / GMAIL_SEND | Resultado ambiguo del POST o lease vencido | RESULTADO_INCIERTO | No vuelve a enviar automáticamente. |
| ENVIANDO | Rechazo permanente o error desconocido | AGOTADO / REQUIERE_CORRECCION | Espera decisión administrativa; no repite el POST. |
| Estado de revisión manual | Cierre explícito del administrador | CERRADO_MANUALMENTE | No envía; no declara aceptación ni entrega. |
| Evento recuperable | Se alcanza `vence_payload_en` heredado de la reserva inicial | EXPIRADO | Borra payload de todas las generaciones activas; no se puede reenviar. |

Toda transición de estado principal comprueba estado y `version_reclamacion` anterior dentro de una transacción breve. Un resultado de una reclamación antigua solo agrega un hecho tardío a la bitácora y no modifica la proyección vigente.

El reenvío manual debe mostrar advertencia, quedar auditado y conservar la cantidad de acciones manuales por separado de los intentos automáticos. No se reinicia ni borra la historia anterior. Una generación nueva enlaza al evento anterior y conserva su propia instantánea; así es posible corregir email sin alterar el mensaje ni los intentos anteriores. Cerrar sin reenvío es otra decisión explícita y no pone el evento en ACEPTADO.

## 7. Privacidad y retención

El correo y el contenido de confirmación son datos personales. Al llegar a ACEPTADO se eliminan destinatario, asunto y cuerpo de la base activa; permanecen metadatos mínimos de resultado vinculados a la reserva. Para estados recuperables, el payload de cada reserva tiene un plazo lógico máximo de 30 días en la base activa, contado desde la creación de su evento inicial. Las generaciones hijas heredan la misma fecha de vencimiento; corregir o reintentar no la reinicia. Al vencer se marca EXPIRADO en toda la cadena y se borra el payload de todos sus eventos, manteniendo código de reserva, estado y timestamps mínimos. Tras restaurar una copia, la purga por `vence_payload_en` debe finalizar antes de habilitar el worker; si no finaliza, el scheduler sigue deshabilitado.

La eliminación lógica del payload no borra inmediatamente páginas SQLite/WAL ni snapshots ya creados. Los respaldos `.backup` pueden conservarlo hasta vencer su rotación; la copia diaria local documentada usa 30 días, mientras que la rotación mensual externa no define un TTL. La UI y la documentación describen 30 días como disponibilidad máxima del payload en la base activa, no como borrado físico total. Antes de habilitar el worker en producción, el hotel debe identificar y comprobar la retención externa; si no hay un TTL verificable, la documentación debe declararlo sin prometer borrado total. Al restaurar, la purga lógica por `vence_payload_en` se completa antes de activar el scheduler; si falla, el worker queda deshabilitado y la salud informa degradación. Las pruebas no borran archivos de backup existentes.

Errores persistidos y logs se sanean con las utilidades existentes y se limitan en longitud. No se guardan cuerpos HTTP del proveedor, valores Authorization, secretos, refresh tokens ni access tokens.

## 8. Casos de fallo y respuesta esperada

| Caso | Resultado esperado |
|---|---|
| Migración o insert outbox falla al crear | Rollback completo de la nueva reserva; error normal de la operación, sin inventario reservado a medias. |
| Correo sin configurar | Reserva y evento quedan persistidos; evento bloqueado sin consumir intentos; pantalla administrativa lo señala. |
| Configuración arreglada después de un bloqueo | El mismo evento vuelve a PENDIENTE y se puede reclamar; conserva generación y payload. |
| Gmail responde éxito con id | Evento ACEPTADO, id y fecha almacenados; el huésped no espera dentro del POST de reserva. |
| Gmail responde 429 o error temporal reconocido | Evento en REINTENTO, backoff persistido; la reserva no cambia. |
| Gmail responde 403 `rateLimitExceeded` / `userRateLimitExceeded` | Evento en REINTENTO con backoff; un 403 de otra razón sigue la clasificación específica y no se trata automáticamente como temporal. |
| Validador detecta dirección inválida o el proveedor identifica inequívocamente su formato | REQUIERE_CORRECCION; no se manda POST y solo una nueva generación con dirección validada puede volver a enviar. La sintaxis válida no prueba que el buzón exista. |
| Gmail rechaza otro contenido de forma permanente | AGOTADO con razón segura; no hay reintento automático ni se modifica el payload original. |
| Gmail responde error permanente de configuración/permisos | BLOQUEADO_CONFIGURACION con razón segura; no consume intentos ciegos y espera corrección. |
| Se pierde la respuesta del POST, responde 5xx o el 2xx no incluye `id` | RESULTADO_INCIERTO, sin reenvío automático; administración revisa proveedor antes de decidir. |
| Proceso cae durante TOKEN_OAUTH | Tras vencer lease se agenda REINTENTO porque no comenzó `messages.send`. |
| Proceso cae después de marcar GMAIL_SEND | Tras vencer el lease el evento pasa a RESULTADO_INCIERTO; no se reenvía automáticamente porque Gmail pudo haberlo aceptado. |
| Se intenta cerrar un resultado incierto | CERRADO_MANUALMENTE con razón categórica y actor; no se inventa un estado de entrega. |
| Clic repetido en reenvío/corrección | La misma `Idempotency-Key` devuelve el mismo hijo; no crea otro envío. |
| Reserva repetida por idempotencia mientras el correo sigue pendiente | Devuelve el código original y no crea otro evento ni adelanta su intento. |
| Respuesta tardía del worker tras vencer/reasignarse el lease | Se agrega a la bitácora con el token viejo; fencing impide que sobrescriba el estado vigente o una generación hija. |
| Alta manual `OTRO` del panel | Se crea un evento igual que para `WEB`, porque el comportamiento actual avisa después de toda alta nueva; la prueba fija esa paridad. |
| Reenvío manual/corrección | Solo administrador autenticado, generación hija, confirmación y auditoría; aviso de posible duplicado para estado incierto. |
| Restauración de respaldo antiguo | La purga por edad corre antes de iniciar el worker; un payload vencido no se puede reenviar por restauración. |

## 9. Verificación de aceptación

### Backend

- Prueba de integración con SQLite que demuestre atomicidad: reserva y outbox aparecen juntas; provocar un fallo del outbox revierte ambas.
- Reintento idempotente produce un solo evento y cero envío directo desde la petición HTTP.
- El texto del aviso describe recepción de la solicitud y enlace al estado vigente; no afirma que la solicitud siga pendiente cuando Gmail la entregue.
- Worker reclama, persiste lease y estado final; al simular caída, el lease vencido mueve la fila a RESULTADO_INCIERTO y bloquea un segundo envío automático.
- Probar caída en ambas fases: `TOKEN_OAUTH` se puede reintentar; `GMAIL_SEND` queda incierto. Cubrir caída entre recibir token y marcar la fase de Gmail.
- Probar correo sin configurar, HTTP 429, 403 temporal y 403 permanente según `reason`, HTTP 5xx/timeout ambiguo, rechazo permanente, éxito con id, 2xx sin id, límite de intentos y `Retry-After` válido/ausente/malformado.
- Probar OAuth `invalid_grant`/`invalid_client`, error OAuth desconocido, 429/5xx previo al envío y respuesta 2xx sin access token; distinguirlos de resultados del POST Gmail.
- Probar `Retry-After` como segundos y HTTP-date, rango excesivo, valor malformado y backoff resultante; no reducir el plazo del proveedor.
- Probar dirección única, longitud máxima, CR/LF, controles y múltiples destinatarios en alta, correo de prueba y corrección manual; los datos inválidos no llegan a crear cabeceras ni payload. Probar que remitente/asunto rechazan CR/LF y que el cuerpo generado queda dentro del límite documentado.
- Probar que el POST Gmail send ejecuta como máximo una llamada por intento visible y no tiene reintentos invisibles dentro de HttpClienteOta.
- Probar que un intento completo ejecuta como máximo una llamada OAuth y un POST Gmail, y que un fallo OAuth programa retry sin haber llamado a Gmail.
- Probar que una respuesta de redirección en `users.messages.send` no dispara otro request ni reenvía la autorización a otro host.
- Probar lease y fencing con resultado tardío, caída durante ENVIANDO, reenvío concurrente y duración superior al peor tiempo HTTP calculado.
- Probar historial append-only para intento automático, acción manual, fallo abierto recuperado y resultado tardío; sin cuerpo ni email en sus respuestas.
- Probar la ausencia de UPDATE/DELETE en la bitácora, la creación idempotente de hijo, límite propuesto de tres reenvíos manuales nuevos por reserva cada 24 horas y cierre manual sin afirmación de envío.
- Probar `WEB` y `OTRO`, idempotencia y corrección de destinatario con una generación hija sin alterar payload/historia original.
- Probar expiración desde la hora de alta original, heredada por hijos, en base activa y restauración de copia antigua antes de que el worker reclame eventos; rechazar reclamaciones cuando no quede margen para completar el peor tiempo HTTP antes de vencer.
- Probar que un fallo de la tarea de purga tras restaurar mantiene deshabilitado el worker y degrada el indicador operativo hasta completar la purga.
- Prueba de seguridad: acceso anónimo/rol no administrador rechazado y ausencia de destinatario/cuerpo/token en respuestas.
- Ejecutar suite API completa, empaquetado y arranque con SQLite temporal y Gmail desactivado. Una prueba simulada no cuenta como envío real.

### Interfaz

- Pruebas de PaginaAdminIntegraciones para estados de carga, error, ausencia de correo configurado, conteos, estados de entrega y confirmación de reenvío.
- Incluir los estados RESULTADO_INCIERTO, AGOTADO, REQUIERE_CORRECCION, CERRADO_MANUALMENTE y EXPIRADO; la interfaz explica que «aceptado por Gmail» no equivale a «entregado».
- Estilos solo en SCSS central; controles táctiles y semántica accesible coherentes con componentes del panel.
- Verificación visual con capturas en escritorio y móvil, sin datos reales, además de cero errores de consola.

### Operación

- Documentar nuevos valores opcionales de intervalo/límite si se exponen como configuración. Los valores predeterminados deben permitir arrancar sin configuración de correo.
- Actualizar la guía operativa para explicar que los 30 días son retención lógica del outbox activo y que las copias pueden preservar payload hasta su rotación; documentar y verificar TTL local/externo y purga previa al worker tras restaurar.
- Corregir la sección actual «Qué NO hace este sistema»: el código ya avisa por correo al crear una reserva. Documentar que el envío pasa al worker y que el estado operativo se consulta solo en administración.
- Antes de habilitar el worker real, registrar evidencia del TTL de respaldos externos y de la política de las rotaciones mensuales; una configuración externa sin TTL deja bloqueada la habilitación de producción.
- Arranque real en perfil de pruebas con migraciones, worker inactivo para Gmail no configurado y estado visible en API/panel.
- La entrega real de Gmail queda pendiente de una configuración y autorización del hotel; no forma parte de las pruebas locales ni del criterio de este código.

## 10. Fuera de alcance

- Tema oscuro/claro: se mantiene en Etapa 9, según el plan general.
- Incorporar nuevos proveedores, mensajería instantánea, colas externas o múltiples instancias.
- Alterar reservas existentes, editar silenciosamente un evento original, o cambiar el origen que genera el correo en relación con el comportamiento actual.
- Confirmar lectura, llegada a bandeja de entrada, rebotes o reputación del dominio; requieren capacidades del proveedor y configuración posterior.
- Garantía de entrega exactamente una vez. El protocolo HTTP de Gmail documenta un POST de envío y no se presupone deduplicación del proveedor. Se hace explícito el riesgo de duplicado tras una respuesta ambigua.
- Cambiar el contenido del aviso más allá de sustituir el estado posiblemente obsoleto por una constancia de recepción con enlace al estado vigente; no afirmar que la reserva está confirmada.

## 11. Archivos candidatos

La implementación debe confirmar nombres y límites al escribir el plan, pero se prevé tocar únicamente:

- API: migración V22, paquete correo (repositorio, worker, servicio de outbox, historial de intentos y controlador), ReservaService, CorreoService/HttpClienteOta/Respuesta, validador compartido de direcciones/cabeceras, AppConfig y sus pruebas.
- Web: PaginaAdminIntegraciones, su prueba, App si la ruta actual exige cambios y los SCSS centralizados del panel.
- Documentación: plan general, evidencia R213 y manual operativo de correo.

Cualquier cambio a esquemas previos, envío en tiempo real desde la petición, exposición de payload personal, retención física prometida o excepción a la regla de origen requiere justificarlo antes del código.
