# R237 — verificación de la lista única de preparación

## Cambio

El panel conserva una sola fuente para los pasos de preparación: GET /api/admin/preparacion. Se eliminaron avisos adicionales calculados en el navegador que podían repetir o contradecir lo que ya decía la lista. La API añade el contacto como séptimo paso y lo marca completo cuando hay correo o teléfono. Con el fixture de siete pasos y uno completado, la página muestra seis pendientes.

## TDD y pruebas

- RED: PreparacionTest.contactoPendienteEnLaLista, con el punto de contacto temporalmente retirado de PreparacionController. Resultado guardado en backend-contacto-red.log: 1 prueba, 1 fallo, 0 errores; falla porque la respuesta no contiene la clave contacto.
- GREEN actual: `mvn -f apps/api/pom.xml -Dtest=PreparacionTest test`; 3 pruebas, 0 fallos, 0 errores. Salida completa en backend-preparacion-current.log.
- Suite API completa actual: `mvn -f apps/api/pom.xml test`; 485 pruebas, 0 fallos, 0 errores y 1 omitida. Maven terminó con `BUILD SUCCESS`; salida en backend-api-full-current.log.
- Prueba dirigida actual: `npm exec -- vitest run src/paginas/PaginaAdminPanel.test.tsx`; 6/6. Salida en frontend-admin-panel-current.log.
- Suite completa actual: `npm test`; 32/32 archivos y 249/249 pruebas, 0 fallos. Salida en frontend-tests-current.log.
- Chequeo actual de tipos de pruebas: `npm run test:typecheck`; sin errores. Salida en frontend-test-typecheck-current.log.
- Build actual de producción: `npm run build` en Node 24.21.0, Vite 7.3.7, 76 módulos transformados, generado en 2.15 s. Salida en frontend-build-current.log.
- `backend-preparacion.log` y `frontend-tests.log` se conservan como historial de ejecuciones anteriores a la última escritura de los archivos fuente (03:38:32). No representan la verificación final: la primera ejecutó dos tests y la segunda corrió cuatro pruebas obsoletas que luego se reemplazaron; los registros `*-current.log` son los resultados posteriores al cambio final.
- `mi-suite-front.log`, `mi-backend-preparacion.log`, `mi-build.log` y las capturas `lista-unica-*.png` se conservan como evidencia complementaria; la validación autoritativa es la ejecución completa posterior consignada arriba.
- npm ci instaló dependencias que ya declara apps/web/package.json conforme a package-lock.json. No se modificó el lockfile; node_modules y dist son artefactos ignorados.

## Revisión visual

Se abrió el servidor local del worktree en Chrome y se usaron datos sintéticos: sesión de administración, los siete pasos, contacto de prueba y cero mensajes. También se respondieron con datos neutros las consultas de sesión y configuración del huésped para que el panel no dependiera de una base local ni produjera 401 ajenos a la vista.

- Escritorio: viewport 1440×1000; ancho del documento 1425 px por la barra vertical del navegador. Captura: escritorio.png.
- Móvil: viewport 390×844; ancho del documento 390 px y altura 2130 px. Captura: movil.png.
- Antes del cambio: panel-primera-vez.png conserva la captura que estaba sin seguimiento en develop al iniciar esta revisión. Se copió sin modificar; el SHA-256 coincide con el original.
- Capturas complementarias: lista-unica-escritorio.png y lista-unica-movil.png muestran los seis pendientes; lista-unica-completo.png muestra el panel sin checklist cuando todos los pasos están hechos.
- En ambos tamaños aparecen los seis pendientes una sola vez; el séptimo elemento terminado queda excluido.
- Consola: sin mensajes de error o advertencia después de instalar los mocks sintéticos. Red: sin solicitudes pendientes del panel.

## Incidencia del contenedor

El build lanzado en node:22-alpine desde el montaje de Windows permaneció en la fase transforming con CPU sostenida durante más de cuatro minutos. El mismo comportamiento se reprodujo sobre el checkout limpio de develop, por lo que no se atribuyó al cambio de R237. Se detuvieron únicamente los dos contenedores efímeros creados para esos builds. El build completo pasó en el host con Node 24.21.0 y quedó registrado arriba.

## Integración y verificación posterior

La captura previa se conservó primero en el commit `5a34362`; el cambio funcional se integró a `develop` con cherry-pick del commit `93b65aa`.

- Después de integrar: `mvn -f apps/api/pom.xml -Dtest=PreparacionTest test`, 3/3, `BUILD SUCCESS` (backend-preparacion-postintegracion.log).
- Después de integrar: `npm test`, 32/32 archivos y 249/249 pruebas (frontend-tests-postintegracion.log).
- Después de integrar: `npm run test:typecheck`, sin errores (frontend-test-typecheck-postintegracion.log).
- Después de integrar: `npm run build`, TypeScript limpio y 76 módulos transformados por Vite (frontend-build-postintegracion.log).
- La suite API completa, ejecutada antes del cherry-pick sobre el mismo árbol de código, terminó con 485 pruebas, 0 fallos, 0 errores y 1 omitida.

## Archivos de evidencia

- backend-contacto-red.log
- backend-preparacion-current.log
- backend-api-full-current.log
- backend-preparacion-postintegracion.log
- frontend-admin-panel-current.log
- frontend-tests-current.log
- frontend-test-typecheck-current.log
- frontend-build-current.log
- frontend-tests-postintegracion.log
- frontend-test-typecheck-postintegracion.log
- frontend-build-postintegracion.log
- backend-preparacion.log y frontend-tests.log (historial anterior, conservado y señalado arriba)
- escritorio.png
- movil.png
- panel-primera-vez.png (captura previa preservada)
- lista-unica-escritorio.png, lista-unica-movil.png y lista-unica-completo.png (capturas complementarias)
- frontend-build.log y frontend-test-typecheck.log (ejecuciones previas, conservadas)
- mi-suite-front.log, mi-backend-preparacion.log y mi-build.log (salidas complementarias conservadas)
