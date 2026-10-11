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

Referencias técnicas: ReservaService, CorreoService, ResultadoEnvio, HttpClienteOta, SqliteTransactionExecutor, SqliteDataSources, CorreoAdminController y PaginaAdminIntegraciones. Gmail documenta el envío como un POST a users.messages.send y devuelve un Message cuando tiene éxito: https://developers.google.com/workspace/gmail/api/reference/rest/v1/users.messages/send. La [guía oficial de errores de Gmail](https://developers.google.com/workspace/gmail/api/guides/handle-errors) identifica `rateLimitExceeded` y `userRateLimitExceeded` como errores 403 que requieren backoff; la clasificación no puede basarse únicamente en la clase HTTP. La guía de errores también recomienda backoff para fallos de servidor. Como el envío es un POST sin idempotencia garantizada, R213 tratará una respuesta de envío ambigua de forma conservadora para no duplicar silenciosamente.

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
5. El número de intentos automáticos es finito. Cada fila termina en aceptación del proveedor, bloqueo de configuración, revisión manual o agotamiento, con razón y hora observables.
6. ACEPTADO significa que Gmail respondió éxito; no significa que el destinatario lo recibió, abrió o no sufrió un rebote.
7. No se promete entrega exactamente una vez. Si Gmail acepta el mensaje y la respuesta se pierde, un nuevo envío podría duplicarlo. Esa situación debe aparecer como RESULTADO_INCIERTO y requerir decisión administrativa antes de reenviar.
8. Cada intento y acción administrativa tiene un registro persistente append-only, sin cuerpo ni dirección de correo completa.
9. La administración puede ver estado y tomar una acción explícita, pero nunca recibe cuerpo, destinatario completo, secretos OAuth ni tokens en la respuesta de listado del API.
10. Las pruebas usan correos `.test` y no llaman a Gmail real.

## 5. Componentes propuestos

### Persistencia

- Añadir una migración Flyway V22 para una tabla de outbox, con referencia a la reserva y al evento anterior cuando sea una corrección/reenvío, clave de evento única, tipo de aviso, origen, generación, estado, payload necesario para enviar, contador de intentos, fechas de creación/próximo intento, lease/token de fencing, resultado del proveedor y último error saneado.
- Añadir una bitácora append-only relacionada con cada evento para guardar inicio y resultado de cada intento automático/manual, actor cuando corresponda, token de reclamación, fecha, clasificación, status HTTP, `reason` saneado de Gmail, id de mensaje recibido y motivo seguro. No guardar cabeceras crudas, cuerpo, destinatario completo, OAuth ni tokens. Un fallo de proceso deja el intento abierto; la expiración del lease lo cierra como incierto con un evento de recuperación.
- Índice para recuperar eventos por estado y vencimiento. Restricciones de CHECK para estados e intentos no negativos. La tabla no sustituye ni modifica las migraciones anteriores.
- El evento inicial es único por reserva, tipo y generación inicial. Una corrección o reenvío administrativo crea una generación hija inmutable y enlazada; nunca edita ni reemplaza el payload ni borra su historial.
- Preservar el comportamiento actual: crear exactamente un evento en toda alta nueva que hoy invoca el aviso, tanto de origen `WEB` como `OTRO` del panel; un reintento idempotente no crea otro. Una futura decisión de producto puede cambiar destinatarios/orígenes, pero R213 no cambia esa regla de audiencia.
- Guardar una instantánea mínima del destinatario, asunto y contenido que se necesita para enviar el aviso generado al crear la reserva. Un reintento de esa generación conserva su contenido original. El payload se oculta en todas las respuestas de listado administrativo.

### Alta de reserva

- Sustituir el envío sincrónico de ReservaService por la creación del evento outbox dentro de la transacción actual, después de insertar reserva, habitación e historial.
- Si el insert del outbox falla, toda la transacción de alta se revierte. No queda una reserva sin una intención durable de aviso.
- Si la reserva se encontró por idempotencia, devolverla como hoy y no insertar ni enviar otro aviso.
- Una vez persistida, la petición termina sin esperar a OAuth ni a Gmail.

### Worker

- Añadir un servicio de despacho con reloj inyectable para pruebas y una tarea Spring programada con intervalo y tamaño de lote acotados.
- Reclamar una fila y registrar el inicio del intento en una transacción breve; asignar lease de dos minutos y token de fencing creciente. Con los valores actuales verificados (timeout de 20 s, tres intentos OAuth con espera de 500 ms y un único POST de Gmail), el camino acotado dura hasta unos 81 s; los dos minutos agregan margen. Si cambia cualquier timeout o número de intentos, el lease debe recalcularse y conservar margen verificable. Envío y llamadas HTTP ocurren fuera de la transacción.
- Guardar resultado e historial en una transacción breve con condición de estado, token de fencing y vencimiento vigentes. Si el worker responde tarde, no sobrescribe la fila reclamada después; su resultado tardío se agrega a la bitácora para orientar revisión.
- El worker no toma otro lote si su ejecución anterior sigue activa. La serialización de SQLite y el soporte de una instancia son parte del contrato actual.
- Si el proceso cae después de reclamar una fila, el vencimiento del lease la mueve a RESULTADO_INCIERTO y cierra el intento abierto; no se vuelve a enviar a ciegas. La administración revisa el proveedor antes de decidir.
- El POST `users.messages.send` no usa reintentos internos de `HttpClienteOta`: cada intento visible del outbox produce como máximo un POST. El intercambio previo de token OAuth se clasifica por separado porque ocurre antes del envío y no tiene el efecto lateral de mandar el mensaje.
- Clasificar la respuesta Gmail usando status y el `reason` estructurado de su JSON. El cliente debe permitir acceder a status, cuerpo estructurado y solo cabeceras allowlist necesarias (por ejemplo `Retry-After`) sin registrar ni exponer cabeceras crudas. No traducir todo 4xx a permanente ni inferir que todo 5xx fue rechazado sin ambigüedad.

### Panel y API administrativa

- Extender CorreoAdminController con una lectura autenticada que devuelva configuración, conteos por estado y los eventos recientes necesarios para operar. No incluir asunto, cuerpo, correo completo, token ni cabeceras.
- Mostrar el estado como una sección de correo en PaginaAdminIntegraciones, aprovechando navegación existente: configuración real, pendientes, en espera, bloqueados, resultados inciertos, aceptados por proveedor y agotados. Indicar hora del último intento y próximo reintento cuando existan.
- Para RESULTADO_INCIERTO o AGOTADO por agotamiento de fallos temporales, el administrador podrá ordenar una generación hija de reenvío con confirmación, aviso de posible duplicado y motivo. Un destinatario corregido solo se admite en una generación hija nueva; el original queda inmutable. Para `REQUIERE_CORRECCION`, el reenvío exige una dirección corregida validada y explícita.
- La acción requiere ROLE_ADMIN, CSRF y registro append-only con identificadores de generaciones y actor. La respuesta de listado no revela email completo; la dirección nueva se acepta solo en la solicitud autenticada de corrección y nunca se copia a logs/bitácora.
- El panel debe explicar que el sistema no prueba entrega en la bandeja del destinatario. Sin configuración autorizada, muestra pendiente de configuración y no una prueba de envío.

## 6. Estados y política de reintentos

Estados propuestos: PENDIENTE, ENVIANDO, REINTENTO, BLOQUEADO_CONFIGURACION, REQUIERE_CORRECCION, RESULTADO_INCIERTO, ACEPTADO, AGOTADO y EXPIRADO.

- PENDIENTE: aún no se ha reclamado.
- ENVIANDO: reclamado con lease vigente.
- REINTENTO: el proveedor rechazó explícitamente con un error temporal; queda fecha de próximo intento.
- BLOQUEADO_CONFIGURACION: el envío no puede empezar porque faltan datos de correo. No consume intento y vuelve a ser elegible cuando el correo queda configurado.
- REQUIERE_CORRECCION: rechazo permanente que no puede solucionarse reintentando el mismo payload, como destinatario inválido. Solo una generación hija con el dato corregido puede iniciar otro envío.
- RESULTADO_INCIERTO: no se obtuvo respuesta concluyente luego de enviar el POST. Se detiene el reenvío automático para evitar duplicado silencioso; el panel pide revisar Gmail antes de tomar una decisión.
- ACEPTADO: Gmail respondió éxito y entregó un `id` válido; conservar id y fecha reportados por Gmail, sin decir entregado. Un HTTP 2xx sin `id` válido no usa un marcador inventado ni cuenta como ACEPTADO: queda RESULTADO_INCIERTO y no se reenvía automáticamente.
- AGOTADO: se agotó el límite de reintentos para fallos temporales. Se conserva el motivo seguro y se permite crear una generación hija mediante decisión administrativa.
- EXPIRADO: venció la retención de 30 días; el payload se borró y el evento ya no se puede reenviar.

Política inicial propuesta: máximo cinco intentos automáticos; esperas de 1, 5, 15 y 60 minutos entre fallos temporales. Son reintentables como rechazo explícito HTTP 429 y HTTP 403 cuyo `reason` Gmail sea `rateLimitExceeded` o `userRateLimitExceeded`; los demás 403 se clasifican por su razón y no se reintentan automáticamente. Un 401 de OAuth/configuración queda bloqueado hasta corrección. Un 4xx por destinatario inválido pasa a REQUIERE_CORRECCION; otros 4xx permanentes pasan a AGOTADO con razón segura, sin reintento automático. Un error de transporte/timeout después de iniciar el POST, HTTP 5xx de `messages.send` o respuesta 2xx sin id válido es RESULTADO_INCIERTO: por el riesgo de que Gmail ya haya aceptado el mensaje, no se repite automáticamente. Fallos transitorios del canje OAuth se distinguen del POST porque ocurren antes de mandar correo y pueden reintentarse sin riesgo de enviar el mensaje dos veces.

Si Gmail entrega `Retry-After`, interpretarlo de forma validada y respetarlo como espera mínima; si falta o es inválido, usar el backoff anterior. Si el proveedor pide esperar más allá del horizonte de retención del payload, pasa a AGOTADO para revisión en vez de enviar antes del plazo indicado. Nunca guardar la cabecera cruda. La clasificación debe retener solo status y razones allowlist saneadas suficientes para explicar la decisión.

El reenvío manual debe mostrar advertencia, quedar auditado y conservar la cantidad de acciones manuales por separado de los intentos automáticos. No se reinicia ni borra la historia anterior. Una generación nueva enlaza al evento anterior y conserva su propia instantánea; así es posible corregir email sin alterar el mensaje ni los intentos anteriores.

## 7. Privacidad y retención

El correo y el contenido de confirmación son datos personales. Al llegar a ACEPTADO se eliminan destinatario, asunto y cuerpo de la base activa; permanecen metadatos mínimos de resultado vinculados a la reserva. Para estados recuperables, el plazo de 30 días es retención lógica máxima en la base activa; después se marca EXPIRADO y se borra el payload, manteniendo código de reserva, estado y timestamps. La tarea de retención debe ejecutarse tras una restauración y antes de habilitar el worker, para que una copia antigua no reactive envíos vencidos.

La eliminación lógica de la fila no borra inmediatamente páginas SQLite/WAL ni snapshots ya creados. Los respaldos `.backup` pueden conservar el payload hasta vencer su propia política de rotación, y las copias externas pueden durar más. Por eso la UI y la documentación describen 30 días como disponibilidad de reenvío en la base activa, no como borrado físico total. La guía operativa de implementación debe identificar y verificar la duración efectiva de los respaldos locales y externos y el orden de purga al restaurar; cualquier promesa de borrado total exige una limpieza coordinada de esas copias.

Errores persistidos y logs se sanean con las utilidades existentes y se limitan en longitud. No se guardan cuerpos HTTP del proveedor, valores Authorization, secretos, refresh tokens ni access tokens.

## 8. Casos de fallo y respuesta esperada

| Caso | Resultado esperado |
|---|---|
| Migración o insert outbox falla al crear | Rollback completo de la nueva reserva; error normal de la operación, sin inventario reservado a medias. |
| Correo sin configurar | Reserva y evento quedan persistidos; evento bloqueado sin consumir intentos; pantalla administrativa lo señala. |
| Gmail responde éxito con id | Evento ACEPTADO, id y fecha almacenados; el huésped no espera dentro del POST de reserva. |
| Gmail responde 429 o error temporal reconocido | Evento en REINTENTO, backoff persistido; la reserva no cambia. |
| Gmail responde 403 `rateLimitExceeded` / `userRateLimitExceeded` | Evento en REINTENTO con backoff; un 403 de otra razón sigue la clasificación específica y no se trata automáticamente como temporal. |
| Gmail rechaza un destinatario inválido | REQUIERE_CORRECCION; se conserva payload original y solo una nueva generación con dirección corregida explícita puede volver a enviar. |
| Gmail rechaza otro contenido de forma permanente | AGOTADO con razón segura; no hay reintento automático ni se modifica el payload original. |
| Gmail responde error permanente de configuración/permisos | BLOQUEADO_CONFIGURACION con razón segura; no consume intentos ciegos y espera corrección. |
| Se pierde la respuesta del POST, responde 5xx o el 2xx no incluye `id` | RESULTADO_INCIERTO, sin reenvío automático; administración revisa proveedor antes de decidir. |
| Proceso cae durante ENVIANDO | Tras vencer el lease, el evento pasa a RESULTADO_INCIERTO; no se reenvía automáticamente porque Gmail pudo haberlo aceptado. |
| Reserva repetida con la misma clave | Mismo código y un solo evento outbox. |
| Respuesta tardía del worker tras vencer/reasignarse el lease | Se agrega a la bitácora con el token viejo; fencing impide que sobrescriba el estado vigente o una generación hija. |
| Alta manual `OTRO` del panel | Se crea un evento igual que para `WEB`, porque el comportamiento actual avisa después de toda alta nueva; la prueba fija esa paridad. |
| Reenvío manual/corrección | Solo administrador autenticado, generación hija, confirmación y auditoría; aviso de posible duplicado para estado incierto. |
| Restauración de respaldo antiguo | La purga por edad corre antes de iniciar el worker; un payload vencido no se puede reenviar por restauración. |

## 9. Verificación de aceptación

### Backend

- Prueba de integración con SQLite que demuestre atomicidad: reserva y outbox aparecen juntas; provocar un fallo del outbox revierte ambas.
- Reintento idempotente produce un solo evento y cero envío directo desde la petición HTTP.
- Worker reclama, persiste lease y estado final; al simular caída, el lease vencido mueve la fila a RESULTADO_INCIERTO y bloquea un segundo envío automático.
- Probar correo sin configurar, HTTP 429, 403 temporal y 403 permanente según `reason`, HTTP 5xx/timeout ambiguo, rechazo permanente, éxito con id, 2xx sin id, límite de intentos y `Retry-After` válido/ausente/malformado.
- Probar que el POST Gmail send ejecuta como máximo una llamada por intento visible y no tiene reintentos invisibles dentro de HttpClienteOta.
- Probar lease y fencing con resultado tardío, caída durante ENVIANDO, reenvío concurrente y duración superior al peor tiempo HTTP calculado.
- Probar historial append-only para intento automático, acción manual, fallo abierto recuperado y resultado tardío; sin cuerpo ni email en sus respuestas.
- Probar `WEB` y `OTRO`, idempotencia y corrección de destinatario con una generación hija sin alterar payload/historia original.
- Probar expiración en base activa y restauración de copia antigua antes de que el worker reclame eventos.
- Prueba de seguridad: acceso anónimo/rol no administrador rechazado y ausencia de destinatario/cuerpo/token en respuestas.
- Ejecutar suite API completa, empaquetado y arranque con SQLite temporal y Gmail desactivado. Una prueba simulada no cuenta como envío real.

### Interfaz

- Pruebas de PaginaAdminIntegraciones para estados de carga, error, ausencia de correo configurado, conteos, estados de entrega y confirmación de reenvío.
- Estilos solo en SCSS central; controles táctiles y semántica accesible coherentes con componentes del panel.
- Verificación visual con capturas en escritorio y móvil, sin datos reales, además de cero errores de consola.

### Operación

- Documentar nuevos valores opcionales de intervalo/límite si se exponen como configuración. Los valores predeterminados deben permitir arrancar sin configuración de correo.
- Actualizar la guía operativa para explicar que los 30 días son retención lógica del outbox activo y que las copias pueden preservar payload hasta su rotación; documentar y verificar TTL local/externo y purga previa al worker tras restaurar.
- Arranque real en perfil de pruebas con migraciones, worker inactivo para Gmail no configurado y estado visible en API/panel.
- La entrega real de Gmail queda pendiente de una configuración y autorización del hotel; no forma parte de las pruebas locales ni del criterio de este código.

## 10. Fuera de alcance

- Tema oscuro/claro: se mantiene en Etapa 9, según el plan general.
- Incorporar nuevos proveedores, mensajería instantánea, colas externas o múltiples instancias.
- Alterar reservas existentes, editar silenciosamente un evento original, o cambiar el origen que genera el correo en relación con el comportamiento actual.
- Confirmar lectura, llegada a bandeja de entrada, rebotes o reputación del dominio; requieren capacidades del proveedor y configuración posterior.
- Garantía de entrega exactamente una vez. El protocolo HTTP de Gmail documenta un POST de envío y no se presupone deduplicación del proveedor. Se hace explícito el riesgo de duplicado tras una respuesta ambigua.
- Cambiar el contenido del aviso al huésped o afirmar que la reserva está confirmada: sigue siendo una solicitud pendiente.

## 11. Archivos candidatos

La implementación debe confirmar nombres y límites al escribir el plan, pero se prevé tocar únicamente:

- API: migración V22, paquete correo (repositorio, worker, servicio de outbox, historial de intentos y controlador), ReservaService, CorreoService/HttpClienteOta/Respuesta, AppConfig y sus pruebas.
- Web: PaginaAdminIntegraciones, su prueba, App si la ruta actual exige cambios y los SCSS centralizados del panel.
- Documentación: plan general, evidencia R213 y manual operativo de correo.

Cualquier cambio a esquemas previos, envío en tiempo real desde la petición, exposición de payload personal, retención física prometida o excepción a la regla de origen requiere justificarlo antes del código.
