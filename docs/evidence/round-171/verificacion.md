# Ronda 171 - OAuth aterriza siempre en la app

## Reporte
Tras "Entrar con Google", el navegador mostraba el JSON crudo
`{"email":"savatar62@gmail.com","nombre":"","tieneReservas":false}` en
`hotel.eridu.top/api/yo?continue` en vez de la aplicación.

## Diagnóstico (logs reales de prod, 2026-10-09)
- `15:54:38Z` API: `entrada al panel con Google: savatar62@gmail.com` → **el login
  SÍ funcionó**; no hay ningún fallo OAuth real en todo el log (solo ruido `[sondeo]`).
- Misma hora, nginx: `GET /login/oauth2/code/google-admin?...&scope=email+profile+...` → 302.
- El `?continue` NO es del usuario ni del código: es Spring Security
  (`HttpSessionRequestCache`, parámetro `continue` por defecto) que lo añade a la
  URL guardada. La petición guardada era una visita anónima a `/api/yo` y el
  `SavedRequestAwareAuthenticationSuccessHandler` la honró tal cual → JSON crudo.
- El `nombre:""` es la respuesta correcta de `/api/yo` (puerta de huésped) para una
  sesión de ADMIN: los admin viven en `users`, no tienen fila de huésped.

## Cambio
- `ManejadorAdminOauth2`: antes de delegar en el continuador, descarta la petición
  guardada si es ruta de datos (`/api/…`) o interna del propio OAuth
  (`/oauth2/…`, `/login/oauth2/…`); vale el `/admin`. Una página del panel
  (`/admin/reservas`) se sigue respetando.
- El `setDefaultTargetUrl("/admin")` se movió al constructor que usan las pruebas:
  antes solo lo fijaba el constructor de Spring y sin petición guardada el redirect
  era `/`, no `/admin` (cazado por el test nuevo).

## TDD rojo-verde
- `peticionApiGuardadaNoSecuestraElLogin`: sin el cambio da
  `http://localhost/api/yo?continue` (la URL del reporte, byte por byte); con él, `/admin`.
- `peticionDePaginaGuardadaSeRespeta`: `/admin/reservas` se honra antes y después.
- Vecinas: `ManejadorOAuthRobustoTest` 2/2, `FalloOauth2Test` 3/3,
  `CookieSesionTest` 3/3, `HuespedGoogleTest` 8/8.

## Verificación (salida real)
- `Oauth2AdminTest`: `Tests run: 7, Failures: 0, Errors: 0`.
- Suite backend completa: `Tests run: 446, Failures: 0, Errors: 0, Skipped: 1` —
  `BUILD SUCCESS`. Sin cambios de frontend.
- El manejador de huésped no consulta petición guardada (siempre `/mis-reservas` o
  `?vuelve=` interno): no tenía el hueco y no se tocó.

## Despliegue
- Merge a `develop`, push y `compose up -d --build` en TopNUC con health OK.
