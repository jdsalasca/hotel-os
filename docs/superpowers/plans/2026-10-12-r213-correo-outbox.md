# R213 — Outbox durable de avisos de reserva: plan de implementación

> **Para agentes de ejecución:** usar la skill superpowers:executing-plans y completar las tareas en orden. Cada paso usa casillas para registrar el avance. Mantener un worktree de ejecución aislado; no integrar ni activar el worker hasta que todas las verificaciones de la ronda estén verdes.

**Objetivo:** guardar la intención del aviso en la misma transacción que la reserva y enviarla después con un worker durable, reintentos acotados, estado administrativo seguro y recuperación que nunca duplique a ciegas un correo.

**Arquitectura:** una tabla SQLite V22 será la fuente de verdad del outbox. El alta de reserva inserta una sola tarea dentro de la transacción existente; un worker de instancia única reclama tareas con lease y un transporte Gmail específico, sin los reintentos ocultos del cliente OTA. Una API administrativa de solo lectura y una sección independiente de Integraciones expondrán estados allowlist; el modo de mantenimiento y la cuarentena protegerán las restauraciones.

**Stack:** Java 25, Spring Boot, Spring JDBC, SQLite y Flyway; Gmail API con OAuth 2.0; React, TypeScript, Vite, SCSS y Vitest; Docker Compose para las comprobaciones integradas.

**Especificación:** docs/superpowers/specs/2026-10-11-correo-outbox-design.md, aprobada el 2026-10-12. Este plan todavía requiere aprobación.

## Restricciones globales

- Persistir reserva y outbox en la misma transacción SQLite; mantener una sola instancia de escritura y no añadir broker ni base de datos.
- La migración nueva es V22, posterior a V21, verificada con SQLite temporal y Flyway.
- Un solo worker procesa como máximo una fila por sondeo de cinco segundos; no permitir workers activos en varias réplicas.
- Cada intento hace como máximo una petición OAuth y un POST Gmail; el transporte de correo no sigue redirecciones ni reintenta por su cuenta.
- Timeout por petición: 20 segundos; deadline total por tarea: 50 segundos; lease: 120 segundos.
- Máximo: cinco intentos, con esperas base de 1, 5, 15 y 60 minutos; respetar Retry-After hasta 24 horas y agotar una espera mayor.
- No repetir automáticamente RESULTADO_INCIERTO. La pantalla no tendrá acciones de reenvío ni resolución.
- No guardar email, nombre, asunto, cuerpo, token, autorización, cuerpo HTTP ni error libre en outbox, API o logs.
- El aviso reconstruido será genérico, confirmará recepción de la solicitud e incluirá código y consulta del estado actual; no copiará datos que puedan quedar desactualizados.
- Validar buzones ASCII simples de máximo 254 octetos, parte local de máximo 64, dominio con al menos dos etiquetas y sin controles, nombres visibles, comentarios, listas o dominios literales.
- Los estados administrativos se identifican con texto y no solo con color; estilos nuevos en SCSS; teclado, foco visible, lector de pantalla y móvil son criterios de aceptación.
- Todas las llamadas de prueba usan servidores HTTP locales y dominios .test; no usar credenciales reales ni enviar correo real.
- No incluir el tema claro/oscuro, cambios de navegación ni reenvío manual en R213.
- Commits pequeños e imperativos con la identidad Git canónica JUAN DAVID SALAS CAMARGO <31742974+jdsalasca@users.noreply.github.com>; publicar solo con la cuenta existente jdsalasca, sin cuentas nuevas ni force-push.

## Foco de revisión

- Caída después de iniciar el POST Gmail: un lease vencido en GMAIL_SEND debe acabar incierto y bloquear el segundo POST. Prueba: CorreoOutboxWorkerTest.leaseVencidoDuranteGmailNoRepitePost.
- Una respuesta Gmail 2xx sin id no acredita aceptación durable; debe quedar incierta. Prueba: CorreoServiceTest.respuestaExitosaSinIdEsIncierta.
- Un cursor mal formado no debe ampliar consultas ni exponer filas fuera del límite. Prueba: CorreoOutboxAdminControllerTest.cursorInvalidoEs400YLimiteNuncaSuperaCien.
- La restauración puede volver al pasado una base local mientras Gmail conserva efectos externos; cualquier fila recuperada elegible debe quedar en cuarentena antes de iniciar el worker. Prueba: CorreoOutboxRecoveryTest.restauracionPoneEnCuarentenaAntesDeHabilitarWorker.
- Email histórico inválido o CR/LF en campos de cabecera no debe originar una petición externa ni filtrarse en errores. Pruebas: CorreoOutboxWorkerTest.destinatarioHistoricoInvalidoNoHaceHttp y CorreoServiceTest.cabecerasConControlesNoHacenHttp.

## Etapa 1 — Validación y frontera externa de correo

### Tarea 1: normalizar y validar buzones y cabeceras

**Archivos**

- Crear: apps/api/src/main/java/co/hotel/correo/ValidacionCorreo.java.
- Modificar: apps/api/src/main/java/co/hotel/reservas/ReservaService.java, apps/api/src/main/java/co/hotel/correo/CorreoService.java y apps/api/src/main/java/co/hotel/correo/CorreoAdminController.java.
- Pruebas: apps/api/src/test/java/co/hotel/correo/ValidacionCorreoTest.java, apps/api/src/test/java/co/hotel/reservas/ReservaServiceTest.java y apps/api/src/test/java/co/hotel/correo/CorreoAdminControllerTest.java.

**Interfaces**

- ValidacionCorreo.normalizarBuzon(String): String recorta solo espacios ASCII exteriores, conserva mayúsculas/minúsculas y rechaza entradas inválidas con error traducido a HTTP 400.
- ValidacionCorreo.validarCabecera(String campo, String valor): void rechaza controles ASCII antes de cualquier HTTP.
- Alta de reserva, envío de prueba y envío interno usan las mismas funciones.
- La gramática es exactamente la de la sección 11 de la especificación: buzón ASCII simple, parte local dot-atom de hasta 64 octetos, dominio de dos o más etiquetas de 1–63 caracteres con guiones interiores y sufijo alfabético de al menos dos letras, total máximo 254 octetos. Primero se rechazan controles y unicode; después se recortan solo espacios U+0020 exteriores.

- [ ] Escribir pruebas para buzón válido de 254 octetos, inválido de 255, unicode, nombre visible, parte local citada, dominio literal, múltiples destinatarios, puntos inválidos en la parte local, parte local mayor de 64, guiones/longitudes/sufijo de etiquetas, tabulador, CR/LF y espacios ASCII externos.
- [ ] Ejecutar desde la raíz: apps/api/mvnw.cmd -f apps/api/pom.xml -Dtest=ValidacionCorreoTest,ReservaServiceTest,CorreoAdminControllerTest test. Confirmar que los casos nuevos fallan antes de implementar.
- [ ] Implementar las funciones compartidas y normalizar el correo antes de entrar a la transacción, persistiendo el valor normalizado.
- [ ] Probar que un alta inválida no deja reserva, historial, línea ni outbox, y que /api/admin/correo/prueba responde 400 sin llamadas al servidor HTTP local.
- [ ] Repetir el comando dirigido y comprobar BUILD SUCCESS.
- [ ] Crear commit atómico: Valida y normaliza direcciones de correo.

### Tarea 2: aislar Gmail del cliente HTTP de OTAs

**Archivos**

- Crear: apps/api/src/main/java/co/hotel/correo/GoogleCorreoHttp.java y apps/api/src/main/java/co/hotel/correo/ResultadoIntentoCorreo.java.
- Modificar: apps/api/src/main/java/co/hotel/correo/CorreoService.java, apps/api/src/main/java/co/hotel/correo/ResultadoEnvio.java y apps/api/src/main/java/co/hotel/config/AppConfig.java.
- Pruebas: apps/api/src/test/java/co/hotel/correo/CorreoServiceTest.java y apps/api/src/test/java/co/hotel/correo/GoogleCorreoHttpTest.java.

**Interfaces**

- GoogleCorreoHttp.postOAuth(String url, Map<String,String> headers, String body): RespuestaGoogle.
- GoogleCorreoHttp.postGmail(String url, Map<String,String> headers, String body): RespuestaGoogle.
- RespuestaGoogle entrega código, cuerpo acotado para parseo, Retry-After parseable y clase de fallo; su representación no expone credenciales ni texto crudo.
- CorreoService.enviarIntento(String destinatario, String asunto, String cuerpo): ResultadoIntentoCorreo distingue aceptación, reintento conocido, configuración bloqueada, rechazo permanente e incertidumbre, además de la fase OAuth/Gmail.

- [ ] Añadir primero pruebas con HttpServer local que cuenten llamadas: éxito con id, éxito sin id, OAuth sin token, 401/403/429/5xx, redirect, timeout y caída de conexión.
- [ ] Ejecutar apps/api/mvnw.cmd -f apps/api/pom.xml -Dtest=CorreoServiceTest,GoogleCorreoHttpTest test; verificar rojo en reintento implícito, redirect y clasificación.
- [ ] Crear HttpClient de correo con timeout de 20 s y Redirect.NEVER; no inyectar ni cambiar HttpClienteOta, que conserva su política para conectores.
- [ ] Implementar un solo POST por fase y mapear los reason JSON de Google a una allowlist. Tras iniciar Gmail, cualquier resultado no concluyente se clasifica como RESULTADO_INCIERTO.
- [ ] Validar remitente, nombre visible, asunto y destinatario antes de OAuth; permitir saltos de línea solo en el cuerpo.
- [ ] Repetir las pruebas y comprobar conteos de cero o un POST Gmail por intento.
- [ ] Crear commit atómico: Aísla el transporte de correo.

## Etapa 2 — Persistencia transaccional y envío durable

### Tarea 3: migrar V22 y crear el repositorio de outbox

**Archivos**

- Crear: apps/api/src/main/resources/db/migration/V22__outbox_correo_reserva.sql, apps/api/src/main/java/co/hotel/correo/EstadoCorreoOutbox.java, apps/api/src/main/java/co/hotel/correo/CorreoOutboxFila.java, apps/api/src/main/java/co/hotel/correo/CorreoOutboxClaim.java, apps/api/src/main/java/co/hotel/correo/CambioCorreoOutbox.java, apps/api/src/main/java/co/hotel/correo/CursorCorreoOutbox.java, apps/api/src/main/java/co/hotel/correo/PaginaCorreoOutbox.java y apps/api/src/main/java/co/hotel/correo/CorreoOutboxRepository.java.
- Pruebas: apps/api/src/test/java/co/hotel/correo/CorreoOutboxRepositoryTest.java.

**Interfaces**

- CorreoOutboxRepository.crearAvisoReserva(long reservationId, Instant creadoEn): long.
- CorreoOutboxRepository.reclamarSiguiente(Instant ahora, Duration lease): Optional<CorreoOutboxClaim>.
- CorreoOutboxRepository.guardarResultado(CorreoOutboxClaim claim, CambioCorreoOutbox cambio): boolean; la escritura condicional valida estado, lease y versión.
- CorreoOutboxRepository.listar(EstadoCorreoOutbox estado, int limite, CursorCorreoOutbox cursor): PaginaCorreoOutbox.
- La fila conserva solo relación, tipo, estado/fase, contador y máximo, UTC, lease/versión, clasificación allowlist e id de mensaje según la especificación; guarda solo el estado/resultado vigente, FK ON DELETE RESTRICT y unicidad reserva/tipo, sin bitácora append-only de transiciones.

- [ ] Escribir pruebas SQLite real para V22 aplicada por Flyway, sin generación de correos retroactivos, unicidad, FK, índice de elegibilidad, campos no personales y UTC uniforme.
- [ ] Probar inserción, reclamación de una fila elegible, lease/versionado y rechazo de una actualización con claim viejo.
- [ ] Ejecutar apps/api/mvnw.cmd -f apps/api/pom.xml -Dtest=CorreoOutboxRepositoryTest test; confirmar fallas previas por tabla u operaciones faltantes.
- [ ] Crear V22 posterior a V21 y operaciones SQL con transacciones SQLite cortas; ninguna operación de red vive en el repositorio.
- [ ] Confirmar que una segunda inserción reserva/tipo no crea otra fila y que el FK impide borrar una reserva relacionada.
- [ ] Repetir pruebas dirigidas sobre archivo SQLite temporal y comprobar BUILD SUCCESS.
- [ ] Crear commit atómico: Agrega persistencia para outbox de correo.

### Tarea 4: insertar el aviso con la reserva y retirar el envío síncrono

**Archivos**

- Modificar: apps/api/src/main/java/co/hotel/reservas/ReservaService.java, apps/api/src/main/java/co/hotel/reservas/CrearReserva.java y apps/api/src/main/java/co/hotel/correo/CorreoConfirmacion.java.
- Pruebas: apps/api/src/test/java/co/hotel/reservas/ReservaServiceTest.java, apps/api/src/test/java/co/hotel/correo/CorreoConfirmacionTest.java y apps/api/src/test/java/co/hotel/correo/CorreoConfirmacionEspanolTest.java.

**Interfaces**

- ReservaService recibe CorreoOutboxRepository y crea AVISO_RESERVA dentro del tx.enTransaccion que ya inserta reserva, línea e historial.
- CorreoConfirmacion.cuerpo(String codigo): String genera aviso genérico con código y enlace a consulta; no incluye nombre, fechas o estado susceptible de quedar desactualizado.

- [ ] Escribir pruebas para reserva nueva con exactamente una tarea, repetición idempotente sin fila duplicada y fallo del insert outbox que revierte reserva, línea e historial.
- [ ] Probar con servidor HTTP local que el alta no llama OAuth ni Gmail y no espera al proveedor.
- [ ] Ejecutar apps/api/mvnw.cmd -f apps/api/pom.xml -Dtest=ReservaServiceTest,CorreoConfirmacionTest,CorreoConfirmacionEspanolTest test y confirmar fallas nuevas.
- [ ] Mover la creación de outbox al callback transaccional y retirar avisarPorCorreo y su envío síncrono; actualizar constructores y fixtures SQL manuales con la tabla V22 necesaria.
- [ ] Reescribir la confirmación para no congelar datos personales ni datos de reserva que podrían quedar desactualizados.
- [ ] Repetir pruebas y verificar rollback completo e idempotencia.
- [ ] Crear commit atómico: Guarda el aviso junto con la reserva.

### Tarea 5: implementar worker, leases y clasificación

**Archivos**

- Crear: apps/api/src/main/java/co/hotel/correo/CorreoOutboxWorker.java, apps/api/src/main/java/co/hotel/correo/CorreoOutboxPolicy.java y apps/api/src/main/java/co/hotel/correo/CorreoOutboxProperties.java.
- Modificar: apps/api/src/main/java/co/hotel/correo/EstadoCorreoOutbox.java, apps/api/src/main/java/co/hotel/correo/CorreoOutboxRepository.java, apps/api/src/main/java/co/hotel/config/HotelProperties.java, apps/api/src/main/java/co/hotel/config/AppConfig.java, apps/api/src/main/java/co/hotel/reservas/ReservaRepository.java y apps/api/src/main/resources/application.properties.
- Pruebas: apps/api/src/test/java/co/hotel/correo/CorreoOutboxWorkerTest.java y apps/api/src/test/java/co/hotel/correo/CorreoOutboxPolicyTest.java.

**Interfaces**

- CorreoOutboxPolicy.clasificar(ResultadoIntentoCorreo intento, int intentos): CambioCorreoOutbox.
- CorreoOutboxWorker.procesarUnaVez(Instant ahora): boolean reclama y procesa como máximo una fila.
- CorreoOutboxWorker lee la reserva actual mediante ReservaRepository.porId(long), valida el email actual y construye asunto/cuerpo con CorreoConfirmacion.asunto() y cuerpo(String codigo); no copia destinatario ni cuerpo a la tabla.
- Reloj, política de tiempos y cliente externo son inyectables; las pruebas no esperan minutos ni contactan Gmail real.

- [ ] Escribir pruebas con reloj fijo para todos los estados; presupuesto de cinco llamadas y esperas de 1/5/15/60 minutos.
- [ ] Probar Gmail 429 y 403 por cuota con Retry-After en segundos/fecha, ausente, malformado y mayor a 24 h; clasificar 403 de credenciales/cuota/razón desconocida.
- [ ] Probar timeout/5xx/redirección/2xx sin id tras iniciar Gmail como inciertos; OAuth transitorio como reintentable; OAuth inválido como bloqueado; rechazo 4xx permanente como agotado.
- [ ] Probar destinatario histórico inválido, reserva ausente y configuración incompleta sin HTTP ni intento consumido, clasificación sin dirección completa en errores/logs, timeout total 50 s y lease de 120 s con duraciones inyectadas.
- [ ] Ejecutar apps/api/mvnw.cmd -f apps/api/pom.xml -Dtest=CorreoOutboxWorkerTest,CorreoOutboxPolicyTest test y confirmar rojo.
- [ ] Implementar sondeo cada 5 s con la programación de Spring habilitada, una fila por sondeo, prevención de solapamiento, límite de intentos comprobado al reclamar, contador justo antes de la primera llamada externa, deadlines, leases y actualización CAS por versión.
- [ ] Hacer reintentable el lease vencido durante OAuth y terminal incierto el vencido después de iniciar GMAIL_SEND; una respuesta tardía no sobrescribe el claim vigente.
- [ ] Añadir CORREO_OUTBOX_ACTIVO con default true; el worker no corre hasta completar recuperación al arrancar.
- [ ] Repetir pruebas y comprobar que nunca hay más de un POST por intento ni más de cinco por tarea.
- [ ] Crear commit atómico: Procesa avisos desde el outbox.

## Etapa 3 — Recuperación y seguridad operativa

### Tarea 6: proteger restauración, mantenimiento y recuperación al reiniciar

**Archivos**

- Crear: apps/api/src/main/java/co/hotel/correo/CorreoOutboxRecovery.java, apps/api/src/main/java/co/hotel/correo/CorreoOutboxStartup.java, apps/api/src/main/java/co/hotel/correo/ResumenCuarentena.java y apps/api/src/main/java/co/hotel/seguridad/FiltroModoMantenimiento.java.
- Modificar: apps/api/src/main/java/co/hotel/config/HotelProperties.java, apps/api/src/main/resources/application.properties, apps/api/src/main/java/co/hotel/config/AppConfig.java, compose.yaml, compose.production.yaml y .env.example.
- Pruebas: apps/api/src/test/java/co/hotel/correo/CorreoOutboxRecoveryTest.java y apps/api/src/test/java/co/hotel/seguridad/ModoMantenimientoTest.java.

**Interfaces**

- CorreoOutboxRecovery.recuperarBloqueadosAlArrancar(Instant ahora) reactiva una vez por inicio las filas bloqueadas con menos de cinco intentos, conserva el contador y agota las que llegaron a cinco.
- CorreoOutboxRecovery.ponerEnCuarentenaTrasRestauracion(Instant ahora): ResumenCuarentena cambia filas recuperadas elegibles a incierto en una transacción y verifica cero elegibles.
- FiltroModoMantenimiento responde 503 y código MANTENIMIENTO_ACTIVO a rutas que mutan reservas y a /api/admin/correo/prueba; permite lecturas, salud y consulta administrativa.

- [ ] Probar reinicio: bloqueadas con 0–4 intentos se reactivan sin reiniciar contador; 5/5 pasan a agotado y no generan HTTP; el sondeo del mismo proceso no repite transición.
- [ ] Probar restauración SQLite temporal: mantener terminales; cambiar pendientes/enviando/reintento/bloqueadas a incierto, asignar RESPALDO_RESTAURADO_REVISAR, limpiar lease y próximo intento, dejar cero elegibles.
- [ ] Probar rollback de cuarentena y worker apagado ante fallo; probar mantenimiento en alta pública y administrativa, cancelación, cambios de fecha/habitación/huéspedes y estado desde huésped y administración, y correo de prueba. No bloquear consultas de solo lectura.
- [ ] Ejecutar apps/api/mvnw.cmd -f apps/api/pom.xml -Dtest=CorreoOutboxRecoveryTest,ModoMantenimientoTest test y confirmar rojo.
- [ ] Añadir HOTEL_MODO_MANTENIMIENTO=false y CORREO_OUTBOX_ACTIVO=true en propiedades y Compose, sin secretos.
- [ ] Ejecutar recuperación después de Flyway y antes de habilitar worker; con mantenimiento activo y outbox apagado, poner en cuarentena en una transacción y abortar arranque si quedan filas elegibles.
- [ ] Registrar solo conteos seguros por estado. Si falla la verificación, mantener cerradas las mutaciones y worker apagado.
- [ ] Repetir pruebas dirigidas y revisar el orden de recuperación, cuarentena y activación con reinicios sintéticos.
- [ ] Crear commit atómico: Protege el outbox al restaurar respaldos.

## Etapa 4 — Visibilidad administrativa

### Tarea 7: exponer consulta allowlist, paginada y solo administrativa

**Archivos**

- Crear: apps/api/src/main/java/co/hotel/correo/CorreoOutboxAdminController.java, apps/api/src/main/java/co/hotel/correo/CorreoOutboxAdminService.java, apps/api/src/main/java/co/hotel/correo/CorreoOutboxItemResponse.java y apps/api/src/main/java/co/hotel/correo/CorreoOutboxPageResponse.java.
- Modificar: apps/api/src/main/java/co/hotel/correo/CorreoOutboxRepository.java.
- Pruebas: apps/api/src/test/java/co/hotel/correo/CorreoOutboxAdminControllerTest.java.

**Interfaces**

- GET /api/admin/correo/outbox?estado=&limite=&cursor=; límite predeterminado 25 y máximo 100.
- Respuesta: código de reserva, estado, intentos actual/máximo, creación/último/próximo intento y clasificación allowlist; cursor opaco estable por creado_en DESC e id DESC.

- [ ] Escribir pruebas MockMvc con admin, huésped y anónimo; filtro por estado; defaults, límite 100, cursor siguiente, cursor inválido y orden estable.
- [ ] Afirmar ausencia de email, nombre, asunto, cuerpo, id Gmail, token, Authorization, texto libre del proveedor y error HTTP crudo.
- [ ] Ejecutar apps/api/mvnw.cmd -f apps/api/pom.xml -Dtest=CorreoOutboxAdminControllerTest test y confirmar rojo.
- [ ] Implementar solo GET protegido por la regla existente /api/admin/**, DTO allowlist y validación de cursor/límite; no añadir mutaciones.
- [ ] Repetir pruebas y comprobar 401/403 correctos sin filtrar campos internos.
- [ ] Crear commit atómico: Expone estados seguros del outbox.

### Tarea 8: integrar estados del correo en Integraciones

**Archivos**

- Modificar: apps/web/src/paginas/PaginaAdminIntegraciones.tsx y, si necesita reglas propias, crear PaginaAdminIntegraciones.scss e importarla desde apps/web/src/styles/indice.scss.
- Crear: apps/web/src/paginas/PaginaAdminIntegraciones.test.tsx.

**Interfaces**

- El panel consulta /api/admin/correo/outbox con api.get; estado/error del outbox son independientes del estado de canales OTA.
- Cada fila muestra código, estado textual, intentos, fechas y clasificación; la paginación conserva el cursor.

- [ ] Escribir pruebas Vitest/Testing Library para carga, error del outbox sin ocultar OTA, lista vacía, todos los estados, texto de aceptado sin prometer entrega, agotado sin reenvío y cursor.
- [ ] Ejecutar desde apps/web: npm run test -- PaginaAdminIntegraciones.test.tsx; confirmar fallas esperadas por sección ausente.
- [ ] Añadir una sección independiente y de solo lectura con carga, error, vacío, resultados y paginación accesible; no agregar botones de reenvío o conciliación.
- [ ] Usar tokens y componentes SCSS existentes; estados por texto, foco visible y tarjetas/lista sin desbordamiento móvil.
- [ ] Ejecutar npm run test -- PaginaAdminIntegraciones.test.tsx, npm run typecheck, npm run test:typecheck y npm run build desde apps/web; todo debe terminar con código 0.
- [ ] Crear commit atómico: Muestra el estado del outbox en Integraciones.

## Etapa 5 — Operación y cierre de ronda

### Tarea 9: actualizar guías y reunir evidencia de cierre

**Archivos**

- Modificar: docs/operations/correo.md, docs/operations/despliegue-respaldos.md y docs/plan.md.
- Evidencia: docs/evidence/round-213/ con verificación, conteos de pruebas y capturas.
- Revisión visual de PaginaAdminIntegraciones en 1440 × 1000 y 390 × 844, con datos sintéticos.

- [ ] Actualizar guía de correo: envío automático durable, aceptación frente a entrega, estados, límites, reinicio tras corregir entorno y no reintento ante resultado incierto.
- [ ] Documentar restauración: activar mantenimiento y apagar worker, detener instancia, restaurar, arrancar con ambas banderas, verificar resumen/cero elegibles, conservar banderas si falla, detener y reiniciar modo normal.
- [ ] Confirmar que Compose y .env.example presentan defaults sin secretos; no afirmar conexión Gmail ni entrega real.
- [ ] Ejecutar apps/api/mvnw.cmd -f apps/api/pom.xml test; desde apps/web ejecutar npm run test, npm run typecheck, npm run test:typecheck y npm run build.
- [ ] Validar docker compose config y docker compose -f compose.production.yaml config con variables sintéticas requeridas.
- [ ] Migrar y restaurar solo sobre SQLite temporal; comprobar V22, integridad FK, conteos antes/después y cero filas elegibles tras cuarentena. No tocar volumen ni respaldos de producción.
- [ ] Levantar el build local, recorrer la sección con respuestas sintéticas y capturar escritorio 1440 × 1000 y móvil 390 × 844; comprobar consola, teclado/foco y scrollWidth menor o igual que clientWidth.
- [ ] Guardar comandos, resultados sin secretos y capturas en docs/evidence/round-213/; revisar diff y git diff --check.
- [ ] Crear commit atómico de documentación/evidencia con identidad canónica.
- [ ] Tras aprobación del plan y verificaciones verdes, integrar R213 en develop, repetir suite y build, hacer push normal a origin/develop bajo jdsalasca y comprobar en GitHub la autoría de cada commit.

## Cobertura de especificación

| Requisito | Tarea |
|---|---|
| Dirección, instancia única y opciones | 3–5 |
| Persistencia y datos personales | 1, 3 y 4 |
| Alta transaccional y trabajador | 4 y 5 |
| Estados, Gmail/OAuth, Retry-After, deadlines y reintentos | 2 y 5 |
| Reinicio y restauración | 6 |
| API administrativa, rol, allowlist y cursor | 7 |
| Integraciones, accesibilidad y responsive | 8 |
| Mensaje, guía de correo y guía de despliegue | 4 y 9 |
| Suite, migración, evidencia visual y publicación | 9 |

## Puerta de ejecución

Este archivo concreta la implementación pero no la autoriza por sí mismo. Esperar la revisión explícita del usuario. Después, ejecutar en worktree, tarea por tarea, preservar cambios ajenos, integrar a develop, verificar antes y después de integrar y publicar con la cuenta existente jdsalasca. El tema claro/oscuro sigue aplazado hasta la Etapa 9 del producto.
