# R213 — Outbox durable para avisos de reserva

- **Fecha:** 2026-10-11
- **Estado:** propuesta de diseño para revisión del usuario. La dirección de usar SQLite y un trabajador con reintentos limitados fue aprobada; este documento aún requiere revisión antes del plan de implementación.
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

Referencias técnicas: ReservaService, CorreoService, ResultadoEnvio, HttpClienteOta, SqliteTransactionExecutor, SqliteDataSources, CorreoAdminController y PaginaAdminIntegraciones. Gmail documenta el envío como un POST a users.messages.send y devuelve un Message cuando tiene éxito: https://developers.google.com/workspace/gmail/api/reference/rest/v1/users.messages/send. La API también recomienda backoff para fallos reintentables: https://developers.google.com/workspace/gmail/api/guides/handle-errors.

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
4. Un lease vencido hace visible la caída durante ENVIANDO y mueve el evento a RESULTADO_INCIERTO; una fila no se procesa concurrentemente dos veces dentro de la instancia soportada.
5. El número de intentos automáticos es finito. Cada fila termina en aceptación del proveedor, bloqueo de configuración, revisión manual o agotamiento, con razón y hora observables.
6. ACEPTADO significa que Gmail respondió éxito; no significa que el destinatario lo recibió, abrió o no sufrió un rebote.
7. No se promete entrega exactamente una vez. Si Gmail acepta el mensaje y la respuesta se pierde, un nuevo envío podría duplicarlo. Esa situación debe aparecer como RESULTADO_INCIERTO y requerir decisión administrativa antes de reenviar.
8. La administración puede ver estado y tomar una acción explícita, pero nunca recibe cuerpo, destinatario completo, secretos OAuth ni tokens en la respuesta del API.
9. Datos y fixtures de pruebas usan correos .test y no llaman a Gmail real.

## 5. Componentes propuestos

### Persistencia

- Añadir una migración Flyway V22 para una tabla de outbox, con referencia a la reserva, clave de evento única, tipo de aviso, estado, payload necesario para enviar, contador de intentos, fechas de creación/próximo intento, lease, resultado del proveedor y último error saneado.
- Índice para recuperar eventos por estado y vencimiento. Restricciones de CHECK para estados e intentos no negativos. La tabla no sustituye ni modifica las migraciones anteriores.
- Clave de idempotencia del evento formada por reserva y tipo, única en la tabla. Para R213 el tipo es solicitud de reserva recibida; otros tipos futuros requieren diseño propio.
- Guardar una instantánea del destinatario, asunto y cuerpo generados al crear la reserva. Así un reintento conserva el mensaje original aunque cambien luego las fechas o la configuración pública del hotel. El payload se limita a lo imprescindible y se oculta en todas las respuestas administrativas.

### Alta de reserva

- Sustituir el envío sincrónico de ReservaService por la creación del evento outbox dentro de la transacción actual, después de insertar reserva, habitación e historial.
- Si el insert del outbox falla, toda la transacción de alta se revierte. No queda una reserva sin una intención durable de aviso.
- Si la reserva se encontró por idempotencia, devolverla como hoy y no insertar ni enviar otro aviso.
- Una vez persistida, la petición termina sin esperar a OAuth ni a Gmail.

### Worker

- Añadir un servicio de despacho con reloj inyectable para pruebas y una tarea Spring programada con intervalo y tamaño de lote acotados.
- Reclamar una fila en una transacción breve y asignar lease con vencimiento. Envío y llamadas HTTP ocurren fuera de la transacción; el resultado se guarda en otra transacción breve.
- El worker no toma otro lote si su ejecución anterior sigue activa. La serialización de SQLite y el soporte de una instancia son parte del contrato actual.
- Si el proceso cae después de reclamar una fila, el vencimiento del lease la mueve a RESULTADO_INCIERTO; no se vuelve a enviar a ciegas. La administración revisa el proveedor antes de decidir.
- Separar el POST de Gmail de los reintentos generales de HttpClienteOta. Cada intento visible del outbox produce como máximo un POST de envío; la política general del cliente HTTP no debe repetir a escondidas esa operación no idempotente.

### Panel y API administrativa

- Extender CorreoAdminController con una lectura autenticada que devuelva configuración, conteos por estado y los eventos recientes necesarios para operar. No incluir asunto, cuerpo, correo completo, token ni cabeceras.
- Mostrar el estado como una sección de correo en PaginaAdminIntegraciones, aprovechando navegación existente: configuración real, pendientes, en espera, bloqueados, resultados inciertos, aceptados por proveedor y agotados. Indicar hora del último intento y próximo reintento cuando existan.
- En eventos RESULTADO_INCIERTO o AGOTADO, el administrador podrá ordenar un reenvío explícito con confirmación y aviso de posible duplicado. La acción requiere ROLE_ADMIN, CSRF y registro de auditoría con identificador del evento y actor; no registrar el cuerpo ni el correo.
- El panel debe explicar que el sistema no prueba entrega en la bandeja del destinatario. Sin configuración autorizada, muestra pendiente de configuración y no una prueba de envío.

## 6. Estados y política de reintentos

Estados propuestos: PENDIENTE, ENVIANDO, REINTENTO, BLOQUEADO_CONFIGURACION, RESULTADO_INCIERTO, ACEPTADO, AGOTADO y EXPIRADO.

- PENDIENTE: aún no se ha reclamado.
- ENVIANDO: reclamado con lease vigente.
- REINTENTO: el proveedor rechazó explícitamente con un error temporal; queda fecha de próximo intento.
- BLOQUEADO_CONFIGURACION: el envío no puede empezar porque faltan datos de correo. No consume intento y vuelve a ser elegible cuando el correo queda configurado.
- RESULTADO_INCIERTO: no se obtuvo respuesta concluyente luego de enviar el POST. Se detiene el reenvío automático para evitar duplicado silencioso; el panel pide revisar Gmail antes de tomar una decisión.
- ACEPTADO: Gmail respondió éxito; conservar id y fecha reportados por Gmail, sin decir entregado.
- AGOTADO: se agotó el límite automático o hay un rechazo permanente que requiere corregir configuración/destinatario. Se conserva el motivo seguro y se permite una decisión administrativa.
- EXPIRADO: venció la retención de 30 días; el payload se borró y el evento ya no se puede reenviar.

Política inicial propuesta: máximo cinco intentos automáticos; esperas de 1, 5, 15 y 60 minutos entre fallos temporales; respetar Retry-After cuando la respuesta lo proporcione y no exceder el máximo configurado. Un HTTP 429 es temporal; errores permanentes 4xx no se repiten automáticamente. La clasificación final de respuestas y de excepciones debe preservar los detalles suficientes para distinguir rechazo explícito de resultado incierto.

El reenvío manual debe mostrar advertencia, quedar auditado y conservar la cantidad de acciones manuales por separado de los intentos automáticos. No se reinicia ni borra la historia anterior.

## 7. Privacidad y retención

El correo y el contenido de confirmación son datos personales. Solo se guardan mientras la entrega pueda recuperarse. Al llegar a ACEPTADO se eliminan destinatario, asunto y cuerpo, y permanecen metadatos mínimos del resultado vinculados a la reserva. Para eventos bloqueados, inciertos o agotados se mantiene el payload durante un máximo de 30 días para permitir la intervención administrativa; después se marca EXPIRADO y se borra el payload, manteniendo código de reserva, estado y timestamps. Una tarea periódica aplica esa retención y el panel explica que el mensaje ya no puede reenviarse tras expirar.

Errores persistidos y logs se sanean con las utilidades existentes y se limitan en longitud. No se guardan cuerpos HTTP del proveedor, valores Authorization, secretos, refresh tokens ni access tokens.

## 8. Casos de fallo y respuesta esperada

| Caso | Resultado esperado |
|---|---|
| Migración o insert outbox falla al crear | Rollback completo de la nueva reserva; error normal de la operación, sin inventario reservado a medias. |
| Correo sin configurar | Reserva y evento quedan persistidos; evento bloqueado sin consumir intentos; pantalla administrativa lo señala. |
| Gmail responde éxito con id | Evento ACEPTADO, id y fecha almacenados; el huésped no espera dentro del POST de reserva. |
| Gmail responde 429 o error temporal reconocido | Evento en REINTENTO, backoff persistido; la reserva no cambia. |
| Gmail responde error permanente | Evento AGOTADO con motivo seguro; el panel permite corrección y acción manual. |
| Se pierde la respuesta del POST | RESULTADO_INCIERTO, sin reenvío automático; administración revisa proveedor antes de reintentar. |
| Proceso cae durante ENVIANDO | Tras vencer el lease, el evento pasa a RESULTADO_INCIERTO; no se reenvía automáticamente porque Gmail pudo haberlo aceptado. |
| Reserva repetida con la misma clave | Mismo código y un solo evento outbox. |
| Reintento manual | Solo administrador autenticado, confirmación explícita, advertencia de posible duplicado y auditoría. |

## 9. Verificación de aceptación

### Backend

- Prueba de integración con SQLite que demuestre atomicidad: reserva y outbox aparecen juntas; provocar un fallo del outbox revierte ambas.
- Reintento idempotente produce un solo evento y cero envío directo desde la petición HTTP.
- Worker reclama, persiste lease y estado final; al simular caída, el lease vencido mueve la fila a RESULTADO_INCIERTO y bloquea un segundo envío automático.
- Probar correo sin configurar, HTTP 429, error permanente, error de red ambiguo, éxito, límite de intentos, Retry-After y reenvío administrativo auditado.
- Probar que Gmail send no ejecuta reintentos invisibles dentro de HttpClienteOta.
- Prueba de seguridad: acceso anónimo/rol no administrador rechazado y ausencia de destinatario/cuerpo/token en respuestas.
- Ejecutar suite API completa, empaquetado y arranque con SQLite temporal y Gmail desactivado. Una prueba simulada no cuenta como envío real.

### Interfaz

- Pruebas de PaginaAdminIntegraciones para estados de carga, error, ausencia de correo configurado, conteos, estados de entrega y confirmación de reenvío.
- Estilos solo en SCSS central; controles táctiles y semántica accesible coherentes con componentes del panel.
- Verificación visual con capturas en escritorio y móvil, sin datos reales, además de cero errores de consola.

### Operación

- Documentar nuevos valores opcionales de intervalo/límite si se exponen como configuración. Los valores predeterminados deben permitir arrancar sin configuración de correo.
- Arranque real en perfil de pruebas con migraciones, worker inactivo para Gmail no configurado y estado visible en API/panel.
- La entrega real de Gmail queda pendiente de una configuración y autorización del hotel; no forma parte de las pruebas locales ni del criterio de este código.

## 10. Fuera de alcance

- Tema oscuro/claro: se mantiene en Etapa 9, según el plan general.
- Incorporar nuevos proveedores, mensajería instantánea, colas externas o múltiples instancias.
- Confirmar lectura, llegada a bandeja de entrada, rebotes o reputación del dominio; requieren capacidades del proveedor y configuración posterior.
- Garantía de entrega exactamente una vez. El protocolo HTTP de Gmail documenta un POST de envío y no se presupone deduplicación del proveedor. Se hace explícito el riesgo de duplicado tras una respuesta ambigua.
- Cambiar el contenido del aviso al huésped o afirmar que la reserva está confirmada: sigue siendo una solicitud pendiente.

## 11. Archivos candidatos

La implementación debe confirmar nombres y límites al escribir el plan, pero se prevé tocar únicamente:

- API: migración V22, paquete correo (repositorio, worker, servicio de outbox y controlador), ReservaService, CorreoService/HttpClienteOta, AppConfig y sus pruebas.
- Web: PaginaAdminIntegraciones, su prueba, App si la ruta actual exige cambios y los SCSS centralizados del panel.
- Documentación: plan general, evidencia R213 y manual operativo de correo.

Cualquier cambio a esquemas previos, envío en tiempo real desde la petición o exposición de payload personal requiere justificarlo antes del código.
