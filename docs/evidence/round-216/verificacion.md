# Ronda 216 — Regresar a la pantalla protegida después del login

## Falla y corrección

Al abrir una pantalla administrativa sin sesión, el enlace de acceso llevaba a `/admin/entrar` sin guardar la ruta original. El login con contraseña y el cambio obligatorio de la clave inicial redirigían siempre a `/admin`, obligando a buscar de nuevo la pantalla solicitada.

La puerta de sesión ahora conserva la ruta, la consulta y el fragmento en el estado del router. Al completar el login o el cambio de clave, la aplicación restaura ese destino. Se validan los destinos y solo se aceptan páginas locales bajo `/admin`; un destino externo, inválido o dirigido al propio login vuelve a `/admin`. El acceso directo al login conserva su comportamiento de ir al panel.

## Pruebas

- TDD: los dos primeros casos de regresión fallaron antes del cambio porque la puerta no transmitía el destino y el login siempre dirigía a `/admin`; después del cambio pasaron. Se añadieron casos para destino externo y para cambio obligatorio de clave.
- `npm test -- --reporter=dot`: **31 archivos, 203 pruebas aprobadas**. Salida completa en `pruebas-frontend.log`.
- `npm run test:typecheck`: aprobado. Salida en `typecheck-pruebas.log`.
- `npm run build`: typecheck de aplicación aprobado; Vite transformó 75 módulos y generó el build de producción. Salida en `build.log`.
- `git diff --check`: aprobado antes del commit.

## Recorrido visual

Se inició en `/admin/integraciones?tab=correo#gmail`, se pulsó «Iniciar sesión» en el aviso de sesión requerida y se comprobó en el navegador que el destino preservado quedó como `/admin/integraciones?tab=correo#gmail`. El formulario se renderizó con el título «Panel del hotel».

- Escritorio: viewport 1440 × 1000; ancho documental 1440 px. Captura: `admin-login-escritorio.png`.
- Móvil: viewport 390 × 844; ancho documental 390 px, sin desbordamiento horizontal. Captura: `admin-login-movil.png`.
- El navegador registró dos respuestas 401 de las consultas de sesión `/api/admin/sesion` y `/api/yo` al abrir sin autenticar. Son respuestas de control de sesión; no se observaron excepciones de JavaScript.

## Alcance

La restauración cubre el inicio de sesión con contraseña y el cambio obligatorio de clave inicial. No cambia la integración OAuth, el backend ni la base de datos. El login visitado directamente continúa dirigiendo al panel `/admin`.
