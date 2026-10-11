# Auditoría de producto y plan integral de mejora

**Hotel OS · corte de auditoría: 10 de octubre de 2026**<br>
**Referencia de código revisada:** `571e201874cf67bfcda37d189d03449c925c0277` (`develop`)<br>
**Sitio observado:** [hotel.eridu.top](https://hotel.eridu.top/)<br>
**Estado:** diagnóstico de solo lectura; los hallazgos operativos necesitan decisiones del hotel antes de cambiar datos reales.

## 1. Para qué sirve este documento

Este documento convierte observaciones reproducibles del sitio y del código en un orden de trabajo que el equipo puede tomar por tareas pequeñas. Busca que el producto vuelva confiable la reserva directa, comunique con honestidad qué puede hacer hoy y reduzca los puntos donde una modificación rompe otra pantalla.

No es una aprobación para inventar datos del hotel, publicar precios, habilitar ventas ni conectar canales externos. Cada tarea debe separar lo que puede corregirse en código de lo que solo puede confirmar el hotel. El trabajo de software se considera terminado por evidencia revisable: prueba que reproduce el comportamiento, resultado de build o prueba aplicable, inspección del cambio y, cuando se toca la interfaz, verificación real en escritorio y móvil con capturas.

El modo oscuro/claro queda aplazado hasta una etapa final. Antes deben quedar resueltos el flujo de reserva, la información real del establecimiento, los patrones públicos y la consolidación de tokens visuales. Añadir un segundo tema al sistema actual antes de estabilizar esas bases multiplicaría el costo de cada arreglo visual.

## 2. Alcance y método

### Comprobaciones realizadas

- Se abrió el sitio público desplegado sin iniciar sesión y se probaron las rutas públicas principales, estados vacíos, navegación móvil y recuperación de una URL inexistente.
- Se consultaron únicamente endpoints de lectura: salud, datos públicos del hotel, venta derivada de inventario y lugares de interés. No se ingresaron credenciales ni datos personales; no se creó, canceló o modificó una reserva ni se cambió configuración de producción.
- Se revisó el checkout base indicado arriba, sus migraciones, servicios, pruebas, documentación operativa, scripts de frontend y estructura de estilos.
- Se guardaron capturas de portada real en escritorio y móvil. Las capturas representan solo la fecha y estado observados; no certifican disponibilidad futura.
- Se inició la batería de pruebas y build en una copia de trabajo aislada. Los resultados completos se agregan en la sección 4 cuando concluya la ejecución.

### Límites explícitos

- La vista pública no permite conocer por qué faltan habitaciones o tarifas ni cuál es la intención comercial del hotel. No se abrió el panel administrativo.
- La señal pública `a_la_venta` no equivale a una pausa manual: el código la deriva del catálogo. El operador debe confirmar si se espera vender y qué datos reales hacen falta.
- El estado HTTP de una llamada anónima protegida que responde 401 se registra como acceso denegado esperado; no se trata por sí solo como fallo de autorización.
- No se verificó la configuración de GitHub, reglas de rama, secretos de CI, proveedor de correo, proveedor de pagos ni destino externo de respaldos.
- Los tamaños de archivos grandes señalan costo de mantenimiento y riesgo de cambios, no demuestran por sí solos un defecto funcional.

## 3. Resumen para priorizar

La aplicación y el sitio están vivos: la portada y sus recursos cargan, la API pública responde, la ruta de salud informa base accesible, el menú móvil puede operarse con teclado y las pantallas de consulta muestran estados entendibles. La auditoría no respalda la frase “todo está roto”. Sí encontró una brecha principal: en la observación de producción la ruta de compra no podía empezar porque `/api/hotel/venta` devolvió `false`; el sitio mostraba ese resultado como reservas “pausadas”, sin explicar que deriva de datos de catálogo.

La siguiente decisión sobre producción pertenece al hotel: confirmar si la venta debe estar abierta y verificar habitaciones vendibles, planes de tarifa, precios, fechas y condiciones. En paralelo, sí podemos mejorar código con pruebas para que la bandera de venta represente relaciones válidas del catálogo y para que el mensaje público no sugiera una pausa temporal que el sistema no ha detectado.

### Estado verificado del sitio público

| Área | Evidencia observada | Lectura para el plan |
|---|---|---|
| Portada y recursos | Documento, JS, CSS e imágenes principales responden HTTP 200. | El sitio está desplegado y renderiza; esto no demuestra que se pueda reservar. |
| Salud API | `/api/health` responde 200 con `estado=ok`, cuatro tablas y base accesible. | Señal básica de proceso/base; no es una prueba de copia de seguridad, catálogo vendible ni restauración. |
| Venta | `/api/hotel/venta` responde 200 con `a_la_venta=false`. | La búsqueda/compra no se ofrece en la portada en esa visita. Confirmar intención y causa con el hotel. |
| Perfil público | `/api/hotel` incluye Hotel Eridu y COP, pero contacto, dirección, zona horaria, horas y política están vacíos. | El visitante no recibe datos suficientes para ubicación y contacto; no llenar esos campos por inferencia. |
| Ubicación y guía local | `/api/lugares` responde `hotel.ubicado=false` y lista vacía. | Mapa/guía no tienen datos disponibles en esa respuesta. Requiere coordenadas y selección editorial confirmadas. |
| Recorridos | Portada, consulta, mis reservas, acceso administrativo y URL inexistente cargan estados entendibles. | Mantener lo que ya funciona; comprobar los flujos con datos sintéticos completos en la etapa de aceptación. |
| Móvil y teclado | En 390×844 el menú abre; Escape lo cierra y el foco vuelve al botón. | Comportamiento correcto observado; conservarlo al trabajar navegación o estilos. |
| Fotografía | La foto de balcones tiene bandas negras dentro del archivo y se ven en galería. | Problema visual de activo de imagen; buscar original/licencia y reemplazar o recortar con proporción aprobada. |
| Metadatos de ruta | Todas las rutas examinadas conservan el mismo `document.title`; una ruta desconocida muestra 404 visual con documento HTTP 200. | Mejorar título por ruta y decidir tratamiento de soft 404 en despliegue/SPA. |
| Consola y Lighthouse | Accesibilidad 100, buenas prácticas 96, SEO 100 y Agentic Browsing 67; 52 comprobaciones pasaron y 2 fallaron. | Las dos fallas fueron solicitudes anónimas de sesión que responden 401 y recomendación opcional de `llms.txt`; separarlas de fallas JS y de trabajo comercial. |

### Evidencia visual guardada

- [Portada de producción en escritorio](../../output/playwright/produccion-portada-escritorio.png)
- [Portada de producción en móvil](../../output/playwright/produccion-portada-movil.png)

## 4. Hallazgos con prioridad y evidencia

Las prioridades indican orden de atención propuesto, no severidad de seguridad. “Confirmado” significa que la conducta o condición aparece en la respuesta observada o en el código revisado. “Riesgo” significa que el diseño permite el problema, pero no se afirma que ocurriera en los datos de producción.

### P0 — bloqueo de negocio, condicional a la intención de venta

**La reserva directa no estaba disponible durante la visita.** El endpoint público devolvió `a_la_venta=false`; la portada retiró el buscador y presentó “Reservas en línea pausadas” y “Vuelve a consultar más adelante”. El usuario no podía iniciar una reserva directa desde la portada.

- Evidencia: respuesta en vivo de `/api/hotel/venta`; estado público renderizado en [PaginaInicio.tsx](../../apps/web/src/paginas/PaginaInicio.tsx#L493-L529).
- El indicador en [HotelConfigRepository.java](../../apps/api/src/main/java/co/hotel/hotel/HotelConfigRepository.java#L41-L47) no lee una bandera de pausa: cuenta habitaciones `ACTIVA` y filas de tarifa.
- **No confirmado:** si el hotel desea vender hoy, si la causa son habitaciones, tarifa, fecha, plan o contenido incompleto. No cambiar el estado ni poblar registros para “arreglar” la observación.
- Cierre requerido: operador confirma intención y completa una lista real de datos; después se ejecuta el camino de reserva de extremo a extremo en un entorno controlado y se acuerda evidencia para producción.

### P1 — riesgo de que “hay venta” no represente un catálogo relacionado

La implementación inicial contaba habitaciones activas y tarifas en consultas independientes. El esquema relaciona cada habitación con un tipo de habitación y cada tarifa con un tipo y un plan tarifario; los planes tienen `activo`. En una base sintética, las pruebas de regresión reprodujeron dos falsos positivos del código anterior: una tarifa del tipo de habitación equivocado y una tarifa asociada únicamente a un plan inactivo. No afirmamos que esas combinaciones existieran en los datos de producción.

- Evidencia: [HotelConfigRepository.java](../../apps/api/src/main/java/co/hotel/hotel/HotelConfigRepository.java#L41-L47), esquema base [V1](../../apps/api/src/main/resources/db/migration/V1__esquema_base.sql) y esquema de tarifas [V2](../../apps/api/src/main/resources/db/migration/V2__tarifas_y_canales.sql).
- La prueba [HotelVentaTest.java](../../apps/api/src/test/java/co/hotel/hotel/HotelVentaTest.java) cubre ahora ambos negativos relacionados y un positivo válido. Las fechas disponibles se calculan por separado y no se infieren de este indicador.
- Acción aplicada en la ronda 205: la consulta relaciona habitación activa → mismo tipo → tarifa → plan activo. No equipara una fila de precio cualquiera con disponibilidad para cualquier fecha.

### Cerrado en la ronda 204 — un fallo del armazón dejaba la página en blanco

**Causa precisa.** `MuroAntipanico` (en `componentes/Estado.tsx`) envuelve únicamente a
`<Routes>`. La cabecera —`SaludoSesion`, `BadgeMensajes`, `CierreSesionPanel`—, el componente
`VolverArribaAlNavegar` y el pie quedan **fuera** de ese muro. React no propaga hacia arriba
el error lanzado desde un `useEffect`: al producirse fuera de todo muro, desmonta el árbol
entero y el visitante se queda con una pantalla vacía, sin mensaje y sin salida.

La reproducción no fue una suposición: la traza real del fallo apunta a
`commitHookEffectListMount`, por encima de cualquier componente, lo que confirma que el muro
de rutas no intercepta ese caso.

**Corrección.** Sin añadir un segundo muro: `MuroAntipanico` envuelve ahora también el shell, y
admite una variante `sutil` que ofrece **Recargar** en lugar de "Volver al inicio" —cuando lo
que se rompió es la cabecera, ese enlace vive justo en lo que falló y sería inútil. `App.test.tsx`
sigue en 9/9: envolver el shell no rompió ninguna ruta.

**Pruebas.** `ShellRoto.test.tsx` sobre el `App` real: el fallo del shell pasa de Pantalla en
blanco a aviso con salida, más un test de no-regresión que comprueba que las rutas siguen
funcionando. Suite completa **183/183** frontend, `tsc --noEmit` y `test:typecheck` limpios,
`vite build` correcto, `git diff --check` limpio.

**Evidencia visual.** `docs/evidence/round-204/` con fallo inyectado de verdad en contenedores:
1 alerta visible, botón "Recargar la página", `pagina en blanco: no`, 0 errores JS y sin
desborde a 1440 px y 390 px. Antes del cambio, esa misma pantalla era completamente blanca.

- Nota de método: el diagnóstico inicial de esta ronda ("no hay ningún ErrorBoundary y
  cualquier fallo deja la pantalla en blanco") **era falso** y se descartó antes de
  commitear. `MuroAntipanico` ya existía y ya estaba probado.

### P1 — el texto público atribuye una pausa que no existe como dato

El código deriva `false` del catálogo, pero la UI habla de “pausadas” y promete consultar más adelante. El hotel puede estar pausado intencionalmente, aunque el endpoint actual no lo distingue. La UI hace una inferencia que los datos no respaldan y no orienta al visitante sobre el paso disponible.

- Evidencia: [PaginaInicio.tsx](../../apps/web/src/paginas/PaginaInicio.tsx#L493-L529) y [HotelConfigRepository.java](../../apps/api/src/main/java/co/hotel/hotel/HotelConfigRepository.java#L41-L47).
- Acción aplicada en R206: mostrar “Reserva en línea no disponible” y explicar que el sitio no puede confirmar nuevas reservas. Se conservan teléfono/correo solo cuando existen y el acceso para gestionar reservas anteriores. No se infiere una pausa ni se promete reapertura futura.
- Aceptación verificada: mensaje factual; sin formulario/calendario en el estado no vendible; el enlace a reservas existentes sigue visible; contacto ausente no genera un dato ficticio. La política de pausa comercial manual queda fuera de alcance hasta definir su señal y responsable.

### P1 — faltan datos públicos necesarios para confianza y visita

La respuesta de perfil observada trae nombre y moneda, pero campos de contacto, dirección, zona horaria, entrada/salida y política sin valor. La respuesta de lugares indica que el hotel no está ubicado y no incluye sitios.

- Evidencia: endpoints de solo lectura `/api/hotel` y `/api/lugares` en la fecha de corte; [HotelConfigController.java](../../apps/api/src/main/java/co/hotel/hotel/HotelConfigController.java#L20-L28).
- Efecto observable: no aparece una vía pública de contacto ni un punto confirmado para llegar; la guía local no puede dar resultados.
- Acción operativa: obtener información aprobada por el hotel, verificar formato y consentimiento para publicarla. Acción de código: hacer que cada campo ausente se represente con gracia y guiar al administrador sobre completitud sin publicar valores de ejemplo.

### P1 — respaldo externo y recuperación no tienen evidencia de cierre

La guía operativa enumera destino externo de respaldo como pendiente/no configurado o no probado; el comentario de despliegue explica que perder el disco nombrado implica perder reservas y que el servicio diario guarda en un volumen local.

- Evidencia documental: [despliegue-respaldos.md](../operations/despliegue-respaldos.md) y comentario de [compose.production.yaml](../../compose.production.yaml).
- Lectura cuidadosa: la salud de `/api/health` no acredita backup; una tarea que genera archivo en el mismo host tampoco prueba retención independiente, integridad ni restauración. La auditoría no accedió al servidor para comprobar estado actual.
- Cierre: decidir ubicación fuera del host, cifrado, credenciales de mínimo privilegio, retención, alerta y responsable; completar una restauración aislada y cronometrada antes de declarar operativo.

### P1 — integraciones OTA siguen siendo preparación, no conectividad activa

La documentación describe Booking.com, Despegar y Airbnb como no configurados y condicionados a aprobación, contrato o credenciales de socios. No hay evidencia actual de sincronización validada.

- Evidencia: [ota-estado.md](../integrations/ota-estado.md) y lista de preparación en [plan.md](../plan.md#L902-L907).
- Cierre: identificar dueño de la relación, aprobación del socio, contrato de datos, pruebas de reconciliación y monitoreo. No anunciar canales “conectados” ni construir un conector real usando credenciales ficticias.

### P2 — recurso fotográfico con bandas incrustadas

`apps/web/public/img/balcones.jpg` incluye bandas negras dentro de los píxeles; no se resuelve solo con `object-fit`. Las bandas aparecen en la galería pública de escritorio y móvil.

- Evidencia: inspección del archivo y capturas de producción enlazadas arriba.
- Cierre: recuperar original de calidad y permiso conocidos; documentar autor/licencia; recortar a las proporciones usadas con revisión visual, compresión apropiada, texto alternativo y sin cambiar la imagen por una foto contextual que pueda inducir a error.

### P2 — título idéntico en todas las páginas y ruta desconocida con respuesta 200

El documento HTML tiene un título estático y la aplicación mantiene `Hotel Eridu · Reserva directa` en rutas examinadas. El fallback de SPA presenta mensaje de página inexistente, pero la petición del documento llega con estado 200.

- Evidencia: [index.html](../../apps/web/index.html#L34); navegación viva a una URL aleatoria con documento HTTP 200.
- Cierre: títulos descriptivos por ruta, actualización de metadatos y canonical si aplica; validar que la plataforma sirva respuesta 404 real para rutas inexistentes sin romper rutas válidas del cliente.

### P2 — dos fuentes de ruido de consola y auditoría automática

Las páginas públicas consultan `/api/yo` y `/api/admin/sesion` de forma anónima; ambos endpoints protegidos responden 401. No se observaron excepciones JS sin capturar ni advertencias de ejecución. Lighthouse registró el rechazo esperado como error de consola.

- Acción: decidir si hay una señal de sesión anónima pública en una API no protegida o una captura de 401 prevista en cliente, y observar cuál llamada dispara el auditor antes de suprimir cualquier mensaje. No convertir el endpoint de administración en público ni silenciar fallos reales.
- Aceptación: cero errores JS inesperados; 401 no se confunde con un fallo de red ni con sesión válida; prueba de cobertura del comportamiento anónimo.

### P2 — componentes y hoja de estilos grandes aumentan el radio de cambio

En la referencia medida: `PaginaAdminInventario.tsx` 1.333 líneas; `PaginaInicio.tsx` 1.101; `PaginaAdminReservas.tsx` 756; `PaginaMisReservas.tsx` 555; `_componentes.scss` 1.440. La búsqueda no halló estilos inline activos; el proyecto ya usa SCSS y clases, lo que sirve como base.

- Evidencia: conteo de líneas en el checkout `571e201` y lectura de los componentes/estilos.
- Interpretación: deuda de separación y revisión, no prueba de una falla para el huésped.
- Acción: extraer una responsabilidad por vez, con pruebas de caracterización y comparación visual antes/después. Mantener estilos agrupados bajo clases de componente y tokens SCSS semánticos; no introducir CSS suelto ni reescribir una página completa en una sola ronda.

### P2 — Vitest se degrada al paralelizar archivos bajo carga

La primera corrida completa de Vitest, lanzada al tiempo que Maven, informó archivos sin trabajador por espera agotada y un test de mapa excediendo cinco segundos; lo que alcanzó a ejecutarse sumó 83 aprobados y 1 fallido en 21 archivos, con siete errores no manejados del pool. El coordinador observó un timeout similar en otra ejecución paralela del `develop` limpio. Repetida esta suite sola con `--no-file-parallelism`, pasó completa: 179/179 en 28 archivos y 78,85 s. `test:typecheck` y build también terminaron correctamente.

- Evidencia disponible: un modo paralelo falló bajo carga y un modo de archivo único pasó. Esto demuestra sensibilidad al modo de ejecución local y deja el origen exacto sin confirmar; no demuestra un defecto funcional determinista del mapa.
- Acción: verificar recursos disponibles, política de paralelismo y duración del test de mapa en CI. Preferir reducir trabajo por test o parametrizar trabajadores si se requiere estabilidad; no aumentar globalmente todos los timeouts sin medir.

### P2 — los conteos de pruebas documentados están desactualizados

`README.md` declara 299 pruebas en su resumen y 264 en el comentario de su comando de verificación; `docs/plan.md` conserva el hito de 299. La salida actual de `./mvnw.cmd test` reporta 465 ejecuciones, 0 fallas, 0 errores y 1 omitida. La divergencia dificulta saber qué validación representa el repositorio actual.

- Cierre: actualizar una sola fuente canónica con el comando que genera el resultado, sin fijar un conteo manual que vuelva a quedar viejo. Conservar notas de rondas como evidencia histórica fechada.

### Observación de automatización — falta evidencia visible de CI en rutas convencionales

En la referencia revisada, `git ls-files` no encontró archivos rastreados en `.github/workflows/*`, `.gitlab-ci.yml`, `Jenkinsfile` o `azure-pipelines.yml`. Esto solo demuestra que esos archivos no estaban en esas rutas; no demuestra que GitHub u otro proveedor no tenga una configuración externa.

- Cierre: revisar configuración del repositorio, protecciones de rama y estado de checks con acceso autorizado. Luego definir pipeline reproducible para frontend, API, dependencias y artefacto de despliegue.

## 5. Orden de etapas

Las etapas describen entregas acumulativas. Una etapa puede dividirse en rondas menores y tickets independientes; no se debe esperar a terminar todo el plan para entregar correcciones seguras. La condición de cada hito es que su aceptación quede visible en el repositorio y verificada localmente. Datos reales y contratos con terceros tienen responsables externos al código y pueden mantener una etapa bloqueada sin que el agente los invente.

### Etapa 0 — decisiones de operación e inventario real

**Prioridad:** P0<br>
**Tipo:** hotel/operaciones, con apoyo de ingeniería<br>
**Dependencias:** responsable autorizado del hotel.

**Objetivo:** decidir si la reserva directa debe estar disponible en esta etapa comercial y reunir datos verificados sin tocar los registros de producción por inferencia.

**Tareas**

1. Confirmar por escrito si se espera recibir reservas directas ahora, en fecha futura o si la venta debe estar deshabilitada indefinidamente.
2. Revisar el catálogo autorizado: tipo de habitación, capacidad, cuartos que se venderán, moneda, tarifa, impuestos/cargos incluidos, vigencia, restricciones y cantidad real.
3. Confirmar zona horaria, horarios, dirección visible, coordenadas, contacto, política de cancelación y texto legal que corresponda al negocio.
4. Confirmar quién puede capturar los datos en administración y quién debe revisarlos antes de publicarlos.
5. Si no hay decisión o faltan datos, conservar venta cerrada y publicar un mensaje honesto con una alternativa válida solo si hay un canal confirmado.

**Aceptación verificable**

- Existe una lista de valores con fuente, responsable y aprobación, o un registro explícito de los que siguen pendientes.
- Ningún precio, tipo, amenidad, contacto, ubicación ni política se creó con datos de ejemplo en producción.
- El endpoint de venta y la portada reflejan la decisión sin cambiar datos de compra durante el audit.

### Etapa 1 — disponibilidad vendible y estado honesto de reserva

**Prioridad:** P0/P1<br>
**Tipo:** API + interfaz pública<br>
**Dependencias:** contrato semántico de “a la venta” acordado; primero se puede implementar la matriz con datos sintéticos.

**Objetivo:** que el producto anuncie compra solo si el catálogo y sus relaciones satisfacen el contrato, y que todo estado tenga una explicación cierta.

**Rondas propuestas**

1. Escribir pruebas para habitación inexistente/inactiva, habitación activa sin tipo, tipo sin tarifas, tarifa de tipo no alojado, plan inactivo, precio cerrado, tasa cero si el dominio permite gratuito, tarifas antiguas y catálogo válido. No fijar el tratamiento de fechas sin acordarlo con las reglas de reserva existentes.
2. Corregir la consulta de readiness con relaciones existentes (habitación activa → tipo → plan activo → tarifa utilizable) y datos parametrizados. Evitar N+1/consultas separadas cuya combinación genere un positivo falso.
3. Decidir si `a_la_venta` significa “hay al menos una tarifa configurada” o “hay al menos una fecha de llegada reservable”; nombrar la respuesta y documentar límites. Si se expone razón interna, proteger datos sensibles y no inferir inventario públicamente.
4. Cambiar el texto público de `false` para que no diga pausa manual ni sugiera reactivación; usar contacto solo con valor confirmado. Considerar un motivo administrativo explicativo que no se exponga a bots anónimos.
5. Probar la misma matriz con API, portada y flujo disponibilidad → selección → huéspedes → confirmación en base temporal.

**Aceptación**

- Cada condición de elegibilidad tiene prueba de API que pasa y al menos una regresión negativa relevante.
- El frontend no ofrece una búsqueda que el backend sabe que no puede resolver, ni muestra “completo” si en realidad no existe tarifa vendible.
- El copy describe el estado actual, ofrece solo acciones habilitadas y no inventa causa, precio, fecha o contacto.
- Los recorridos felices y vacíos tienen evidencia de extremo a extremo en ambiente de prueba.

### Etapa 2 — perfil público del hotel y llegada

**Prioridad:** P1<br>
**Tipo:** panel administrativo + API pública + sitio<br>
**Dependencias:** datos confirmados en etapa 0.

**Tareas**

1. Inventariar campos requeridos, opcionales y sensibles que guarda `hotel_config`; documentar quién los administra y valida.
2. Revisar la pantalla de configuración: etiquetas, ejemplos seguros, formato, zona horaria, moneda, longitud, errores de guardado, éxito, carga y abandono.
3. Definir contrato para coordenadas (par, rango, precisión y vacío) y lugares de interés con nombre, categoría, texto, fuente/autoridad y distancia solo si se calcula correctamente.
4. Diseñar estado “no proporcionado” sin marcador falso, coordenadas centro del mapa inventadas o tarjeta en blanco. La interfaz no debe dar por localizado el hotel si falta el par.
5. Verificar contraste, teclado, lector de pantalla y vista móvil del mapa/listado con datos ficticios señalados como test.

**Aceptación**

- Perfil público solo muestra datos aprobados; omite campos vacíos con intención visible y sin espacios huérfanos.
- El panel identifica lo que falta para completitud, valida antes de persistir y confirma qué guardó.
- Pruebas cubren campos vacíos, entradas inválidas y actualización correcta; datos sintéticos no pueden confundirse con producción.

### Etapa 3 — recorridos públicos y presentación

**Prioridad:** P1/P2<br>
**Tipo:** frontend público y contenido.

**Tareas**

1. Dibujar la ruta de un huésped: conocer alojamiento → encontrar fechas → comparar cuartos y precio total → ingresar datos → revisar condiciones → confirmar → consultar/cancelar según reglas.
2. Probar móvil real o emulado al menos en 390×844 y escritorio 1440 px para cada paso con disponibilidad y sin disponibilidad; revisar scroll horizontal, foco, teclado, mensajes y carga.
3. Hacer que la galería use originales autorizados, proporciones previsibles, carga eficiente y fallback; resolver bandas negras mediante fuente/recorte aprobado.
4. Añadir título por ruta, descripción/canonical si son parte del contrato SEO y respuesta HTTP 404 para ruta desconocida sin romper deep links válidos.
5. Revisar textos de disponibilidad, moneda/formato COP, errores de red, errores 4xx/5xx, doble envío, abandono y regreso del navegador.
6. Medir accesibilidad, rendimiento percibido y errores de consola con las llamadas anónimas identificadas. No mejorar una puntuación ocultando errores.

**Aceptación**

- Reservar o volver de forma segura se entiende en primer vistazo; no se pierden selección ni información por doble click/recarga.
- La ruta no disponible muestra causa conocida o lenguaje neutral y un siguiente paso real.
- Capturas comparables antes/después están guardadas; en cambios solo de contenido también se registra el viewport.
- No hay texto, imagen, precio o política de prueba en artefactos productivos.

### Etapa 4 — recorridos administrativos y consistencia de catálogo

**Prioridad:** P1<br>
**Tipo:** panel, contratos de API y pruebas<br>
**Dependencias:** base de prueba aislada; no autenticarse ni escribir en producción como parte de pruebas automatizadas.

**Tareas**

1. Mapear desde inicio administrativo hasta crear habitación, tarifa y bloqueo; publicar disponibilidad; revisar y operar reserva; registrar pago si aplica; responder mensajes.
2. Asegurar que cada formulario distingue borrador, guardado, error de validación, error de servidor, conflicto y cambio exitoso.
3. Comprobar efectos de ciclo de vida: cuarto fuera de servicio, plan tarifario inactivo, fecha sin precio, restricción de estancia, cancelación y reintegro sin alterar reservas históricas.
4. Añadir pruebas de autorización por rol a operaciones mutables, auditoría y CSRF/sesión donde el marco lo requiera.
5. Simplificar paneles y tablas según tareas frecuentes; no copiar lógica de cálculo de estado en varios componentes.

**Aceptación**

- Pruebas cubren roles y estados negativos importantes; ninguna operación administrativa se filtra a rutas públicas.
- Un cambio de catálogo produce estado consistente en API, panel y sitio, con historial suficiente para explicar quién lo hizo.
- No se verifican operaciones de escritura usando la instancia de producción.

### Etapa 5 — separar componentes para que cada cambio sea seguro

**Prioridad:** P2, como inversión continua<br>
**Tipo:** frontend / SCSS<br>
**Regla:** un área funcional y un dueño por ronda; no ejecutar varias reescrituras de la misma página simultáneamente.

**Orden sugerido**

1. `PaginaInicio.tsx`: extraer secciones de portada, presentación de disponibilidad, galería y pequeñas consultas encapsuladas; mantener el componente como composición.
2. `PaginaAdminInventario.tsx`: separar búsqueda/edición de habitación, calendario, reglas y paneles sin mover reglas de negocio al SCSS.
3. `PaginaAdminReservas.tsx`: extraer ficha, acciones y estados de pago/mensajes con contratos explícitos de props.
4. `PaginaMisReservas.tsx`: separar formulario, listado y estados una vez que la ronda R203 que lo toca esté integrada y revisada.
5. `_componentes.scss`: dividir por componente y dominio usando `@use`/convención ya elegida; conservar tokens compartidos y nombres de clases actuales durante el primer movimiento.

**Aceptación**

- Cada extracción pasa primero por pruebas de caracterización que cubren la pantalla antes de mover lógica.
- diff legible sin reformat de toda la aplicación ni cambio visual no solicitado.
- `npm run test:typecheck`, suite de la página tocada y `npm run build` pasan; captura de escritorio/móvil confirma comportamiento preservado.
- Tamaño de componente se reduce por responsabilidades trasladadas, no mediante compresión de líneas o fragmentación artificial.

### Etapa 6 — contratos API, persistencia, seguridad y privacidad

**Prioridad:** P1/P2<br>
**Tipo:** backend + frontend + datos.

**Tareas**

1. Revisar controladores públicos y administrativos: campos expuestos, validación de entrada, códigos HTTP, idempotencia, límites y mensajes de error.
2. Revisar claves foráneas, nulos y migraciones con SQLite de la versión desplegada; probar actualización desde la base del esquema anterior y reversibilidad operativa definida.
3. Confirmar que los cálculos de fechas usan zona horaria del hotel, límites semiabiertos acordados y que las mismas reglas se usan en cotización y persistencia.
4. Revisar datos de huéspedes, minimización, retención, exportación y auditoría; redactar en logs secretos/datos personales.
5. Realizar pruebas de autorización negativas, sesiones que expiran, CSRF cuando aplique, límites ante enumeración de reservas y sanitización de entradas.
6. Mantener dependencias actualizadas en ventanas revisadas, con SBOM/alertas del proveedor si el equipo puede mantenerlas.

**Aceptación**

- Contratos quedan descritos por endpoint y validados en pruebas de controlador y servicio.
- No hay fugas de datos de huéspedes entre cuentas ni acceso anónimo a administración.
- Migraciones corren sobre base de prueba vacía y una copia representativa de versión anterior.
- Fallos responden con código y texto apropiados sin registrar PII innecesaria.

### Etapa 7 — despliegue, monitoreo y recuperación comprobable

**Prioridad:** P1 antes de recibir dinero y reservas a escala<br>
**Tipo:** DevOps / responsable de operación.

**Tareas**

1. Confirmar proveedor CI y protecciones actuales; si no hay pipeline, definir check requerido para PR: pruebas API, frontend, tipos, build y migraciones de prueba.
2. Separar secretos por ambiente, revisar privilegios mínimos y rotación sin copiar variables reales a logs o plan.
3. Definir health/readiness diferenciados: proceso listo, BD lista, datos operativos completos e integraciones disponibles no deben confundirse.
4. Configurar respaldo cifrado fuera del host con frecuencia, retención, alertas y responsable; documentar restauración en ambiente aislado.
5. Medir tiempo real de recuperación y punto máximo de pérdida aceptable; comparar con la operación de hotel y reservas.
6. Añadir alerta para caída, subida de 5xx, errores de confirmación, job de respaldo vencido y desajuste de inventario; evitar alertas con datos personales.
7. Documentar despliegue, comprobaciones posterior al despliegue y rollback con aprobación operativa.

**Aceptación**

- Una ejecución observable de CI pasa con código de la misma revisión; protección de rama se confirma en la plataforma.
- Se restauró una copia desde el destino externo a entorno aislado y se verificaron reservas/configuración de prueba.
- Hay evidencia de alertas y de quién actúa; `/api/health` no se presenta como prueba de resiliencia.
- La actualización productiva tiene checklist, revisión, rollback y comprobación pública de lectura.

### Etapa 8 — comunicaciones e integraciones externas

**Prioridad:** P2, condicionada a socios y operación<br>
**Tipo:** hotel + proveedor + software.

**Tareas**

1. Para correo y mensajería: dominio, proveedor, DNS, plantillas aprobadas, privacidad, rebotes, reintentos y registro de envío verificable.
2. Para Booking.com, Despegar y Airbnb: confirmar contrato/onboarding, identificadores y habilitación de API; revisar sentido de cada flujo, moneda, zona horaria y mapeo.
3. Diseñar idempotencia, outbox/reintento, reconciliación de reservas, orden de eventos y conciliación de precio/stock antes de una conexión real.
4. Construir simulador/fixtures con payloads sin datos personales y contract tests mientras no haya acceso formal al sandbox del socio.
5. Mostrar en panel el estado “no configurado”, “pendiente”, “sincronizando”, “con error” o “sincronizado” con última sincronización real; no usar una insignia decorativa.

**Aceptación**

- La conexión no se declara operativa hasta prueba autorizada de ida y vuelta y reconciliación de una muestra con el socio.
- Repetir eventos no crea duplicados; divergencias son visibles y recuperables.
- No se versionan credenciales ni se copia información de huéspedes a fixtures.

### Etapa 9 — sistema visual estable y tema oscuro/claro

**Prioridad:** P2, posterior a etapas de catálogo, reservas y estilo público<br>
**Tipo:** diseño e implementación SCSS.

**Tareas**

1. Consolidar paleta semántica, superficies, bordes, focus ring, estado de error/éxito/aviso, tipografía y espaciado compartidos; documentar contraste.
2. Corregir estados públicos y administrativos con un único modo claro ya consistente.
3. Añadir tema oscuro únicamente al sitio público cuando tokens y componentes estén migrados; dejar administración sin cambio hasta decisión explícita de producto.
4. Respetar preferencia de sistema, selector accesible y persistencia. Asegurar SSR/primer render sin destello si la arquitectura lo necesita.
5. Comprobar contraste, controles del navegador, tablas/calendarios, imágenes, modal, focus y `prefers-reduced-motion` en ambos temas.

**Aceptación**

- Tema claro sigue siendo utilizable si storage está vacío o corrupto; la selección se puede cambiar y persiste sin errores.
- No quedan fondos/textos codificados que hagan ilegible una página pública al cambiar de tema; se verifica cada ruta objetivo en escritorio y móvil.
- Cambio exclusivamente visual: no cambia disponibilidad, permisos, precios, mensajes funcionales ni solicitudes de API.

## 6. Contrato de ejecución para el equipo y agentes

Cada tarea tomada del plan debe dejar en su descripción o PR lo siguiente:

1. **Hallazgo y alcance:** identificador de hallazgo/etapa, comportamiento que corrige y rutas/usuarios afectados.
2. **Evidencia inicial:** reproducción, endpoint, captura o prueba roja; si el hallazgo es un riesgo, indicar la combinación sintética que lo demuestra.
3. **Dueño y archivos:** una persona/agente responsable por archivos compartidos; listar archivos de solapamiento antes de comenzar. Dos agentes no editan a la vez el mismo módulo o checkout.
4. **Implementación pequeña:** un cambio funcional por commit, nombres y comentarios en español cuando siguen convención del módulo, reglas en TypeScript/Java y estilos en SCSS. No estilos inline, hoja CSS nueva suelta ni componentes monolíticos de reemplazo.
5. **Prueba primero:** test nuevo que falla con el comportamiento actual cuando sea viable, corrección mínima y limpieza. Pruebas con SQLite temporal y datos inventados deben quedarse aisladas del despliegue real.
6. **Verificación:** salida completa del comando relevante, build cuando aplique, revisión de diff, `git diff --check`; para interfaz, screenshot real de al menos escritorio y móvil y lista de errores de consola.
7. **Revisión:** verificar no se tocó `.env`, secreto, respaldo ni trabajo de otro agente; buscar copy/assets/datos ficticios que puedan filtrarse al bundle.
8. **Cierre:** commit imperativo y atómico, integrar a `develop` después del gate, volver a correr verificación sobre el commit integrado, push autorizado de `develop`, eliminar solo el worktree y rama propia, `git worktree prune`, `git status` limpio en el área propia. No borrar ni limpiar el checkout de otro agente.
9. **Actualización del plan:** marcar solo aceptación demostrada; si hay dependencia externa, indicar quién aporta el dato y qué falta, sin convertir una espera de operador en “completado”.

Si el árbol compartido de `develop` está sucio por otra ronda, no se usa para implementar ni se limpian sus cambios. Se trabaja en worktree aislado y se integra cuando el dueño de esa ronda confirme la revisión y el HEAD remoto permita avanzar en fast-forward.

## 7. Puerta mínima de verificación por tipo de cambio

| Cambio | Verificación requerida |
|---|---|
| Endpoint/API | Pruebas del controlador/servicio afectado; errores HTTP esperados; suite backend si cambia regla transversal; build del artefacto. |
| Persistencia/migración | Prueba desde base vacía y desde fixture previo; integridad y datos tras migrar; nunca escribir en producción. |
| React/TypeScript | Test del componente/flujo y `npm run test:typecheck`; `npm run build`; revisar consola. |
| Interfaz pública o panel | Pruebas funcionales más captura de escritorio y móvil (mínimo 390×844), inspección de foco/teclado, overflow y estados vacío/error/éxito relevantes. |
| SCSS/tokens | Solo SCSS existente o módulos SCSS bajo convención; revisar estados y contraste en cada superficie alcanzada; diff sin cambios visuales colaterales. |
| Despliegue/respaldo | Evidencia de CI/despliegue del mismo SHA; health pública; copia fuera del host y restauración aislada; ruta de rollback. |
| Plan/docs | Enlaces internos válidos, archivos de evidencia en el repositorio, revisión de referencias y `git diff --check`. |

## 8. Primera tarea recomendada después de integrar este plan

**Tarea:** hacer que la bandera pública de venta exija una ruta coherente de catálogo y que la portada explique el estado sin afirmar que alguien pausó el sistema.

**Por qué empieza aquí:** coincide con el primer bloqueo observable para el huésped, es reversible, se puede demostrar con base sintética y no necesita reactivar ni alterar ventas reales. También evita que una futura carga de datos aparentemente exitosa muestre un buscador sin tipo/plan/precio relacionados.

**Límite inicial:** no implementar una política de disponibilidad por fechas nuevas sin revisar el cotizador actual; no introducir configuración manual de pausa antes de decidir quién la controla; no tocar el catálogo productivo.

**Entregable mínimo:** pruebas que demuestran positivos válidos y negativos inválidos según contrato, consulta relacional corregida, copy neutro con alternativa configurada si existe, verificación del recorrido público en estados vendible/no vendible sintéticos, capturas y reporte breve de la etapa.

**Estado por subalcance (10 de octubre de 2026):** API completada en R205; el indicador ahora requiere una habitación activa, su mismo tipo, una tarifa relacionada y un plan activo. Los dos negativos nuevos fallaban antes del cambio (5 pruebas, 2 fallas) y pasaron después (5/5); la suite backend pasó con 467 pruebas, 0 fallas, 0 errores y 1 omitida. El copy y el flujo público del estado no vendible se cerraron en R206, con datos API sintéticos y capturas en escritorio/móvil. Queda decidir si la señal de venta necesita una vigencia por fechas; no se modificaron datos productivos.

## 9. Registro de resultados de la línea base

Los comandos se ejecutaron contra la referencia `571e201`, en un worktree separado y con SQLite de prueba para el backend. La corrida web inicial compartió recursos de máquina con Maven; se repetirá en serie antes de afirmar su estado.

| Comprobación | Estado al corte del documento | Evidencia / siguiente acción |
|---|---|---|
| `npm run test:typecheck` | Pasó en primera ejecución. | TypeScript de tests sin errores. |
| `npm run build` | Pasó en primera ejecución: Vite terminó build de producción. | Build de producción verificado sobre la línea base. |
| `npm test` | La corrida concurrente con Maven reportó 83 pasadas, un timeout en la prueba del mapa y siete errores del pool en 21 archivos; otra corrida paralela contra `develop` limpio informó 178/179 por timeout. | No tratar los errores del pool como fallas funcionales confirmadas; mantener anotada la sensibilidad al modo de ejecución. |
| `npm test -- --no-file-parallelism --reporter=dot` | Pasó: 179/179 en 28 archivos, 78,85 s, sobre `571e201` sin Maven paralelo. | Línea base web exitosa con recursos acotados. |
| `./mvnw.cmd test` | Pasó: 465 pruebas, 0 fallas, 0 errores y 1 omitida; BUILD SUCCESS. | Surefire contra SQLite de prueba; duración observada 17 min 35 s. El README actual cita conteos anteriores (299 y 264). |
| UI en producción | Portada revisada visualmente en escritorio y móvil. | Capturas en `output/playwright`; menú móvil y rutas públicas básicas probados. |
| API pública de producción | Lecturas de salud, hotel, venta y lugares respondieron. | No se realizaron cambios ni escrituras. |

### Verificación posterior a integrar la ronda R203

La revisión R203 quedó en `develop` como `2ed1e692`; el plan se rebasó sobre esa referencia y se verificó en el mismo checkout integrado antes de publicarlo. Esta tabla separa los resultados de `571e201` de la comprobación del estado actual:

| Comprobación en `2ed1e69` + plan | Resultado |
|---|---|
| Web `npm test -- --no-file-parallelism --reporter=dot` | 181/181 pruebas en 28 archivos; 60,98 s. |
| `npm run test:typecheck` | Pasó. |
| `npm run build` | Pasó; Vite generó el bundle de producción. |
| API `./mvnw.cmd -q test` | Pasó; 465 pruebas, 0 fallas, 0 errores y 1 omitida, confirmadas en 62 reportes Surefire. |
| Estado de producción | Solo se realizaron consultas públicas de lectura; no hubo escrituras ni cambios de configuración. |

Las [capturas de Mis reservas en escritorio](../evidence/round-203/01-mis-reservas-escritorio.png) y [móvil](../evidence/round-203/02-mis-reservas-movil.png), junto con la [nota de verificación R203](../evidence/round-203/verificacion.md), conservan la evidencia visual de esa mejora.

### Verificación de la ronda 205 — elegibilidad del catálogo

| Comprobación | Resultado |
|---|---|
| `./mvnw.cmd -q -Dtest=HotelVentaTest test` | 5/5; incluye tipo de habitación no relacionado y plan tarifario inactivo. |
| `./mvnw.cmd -q test` | 467 pruebas, 0 fallas, 0 errores y 1 omitida, en 62 reportes Surefire. |
| Migraciones / configuración de producción | Sin cambios; pruebas sobre SQLite temporal y producción sin escrituras. |

### Verificación de la ronda 206 — copy de venta no disponible

| Comprobación | Resultado |
|---|---|
| `npm test -- --no-file-parallelism --reporter=dot src/paginas/PaginaInicio.test.tsx` | 33/33 pruebas focalizadas, sobre la rama rebasada en el `develop` que ya contiene R204. |
| `npm test -- --no-file-parallelism --reporter=dot` | 183/183 pruebas web en 29 archivos; ejecución serial para evitar los timeouts ya observados bajo carga. |
| `npm run test:typecheck` / `npm run build` | Pasaron; incluye `tsc --noEmit` y build Vite. |
| Navegador real con APIs simuladas | En 1440 px y 390 × 844 px se ve el mensaje neutral; no aparecen búsqueda ni calendario; se conserva la acción de gestionar reservas existentes. Contacto de prueba usa `example.test`. |
| Overflow y consola | Móvil: 390 px de contenido para viewport de 390 px, sin desborde horizontal. Sin errores de consola. |

Las [capturas de escritorio](../evidence/round-206/01-disponibilidad-escritorio.png) y [móvil](../evidence/round-206/02-disponibilidad-movil.png), además de la [nota de verificación](../evidence/round-206/verificacion.md), documentan el estado sintético. No se contactó ni escribió a producción.

## 10. Fuentes internas clave

- [Plan de producto existente](../plan.md)
- [Estado de respaldos y despliegue](../operations/despliegue-respaldos.md)
- [Estado de integraciones OTA](../integrations/ota-estado.md)
- [Regla actual de venta derivada](../../apps/api/src/main/java/co/hotel/hotel/HotelConfigRepository.java#L41-L47)
- [Pruebas actuales de venta](../../apps/api/src/test/java/co/hotel/hotel/HotelVentaTest.java)
- [Mensaje público de venta no disponible](../../apps/web/src/paginas/PaginaInicio.tsx#L493-L529)
- [Respuesta pública y administrativa de configuración](../../apps/api/src/main/java/co/hotel/hotel/HotelConfigController.java#L20-L28)
- [Esquema base de tipos/habitaciones](../../apps/api/src/main/resources/db/migration/V1__esquema_base.sql)
- [Planes y tarifas](../../apps/api/src/main/resources/db/migration/V2__tarifas_y_canales.sql)
- [Título HTML estático](../../apps/web/index.html#L34)
