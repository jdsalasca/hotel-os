# Ronda 191 — Validación del correo en la consulta

## Alcance

En `/consulta`, comprobar el formato del correo antes de consultar una reserva. Si no es válido,
la búsqueda no se envía; el campo recibe `aria-invalid`, se asocia al aviso accesible y obtiene
el foco para corregirlo. Al introducir un correo válido, el aviso se retira. El endpoint y la
respuesta para las búsquedas válidas no cambian.

## Evidencia

- Prueba nueva: falló antes del cambio porque una dirección mal formada generaba la llamada API;
  pasó al bloquear el envío y mostrar el error junto al campo.
- `npm test`: **27 archivos, 144 pruebas aprobadas**.
- `npm run test:typecheck`: aprobado.
- `npm run build`: aprobado; Vite transformó 71 módulos. Bundle: CSS **23,38 kB**, JS **378,80 kB**.
- Chrome local, escritorio **1365 × 900** y móvil **390 × 844**: el mensaje cabe bajo el campo;
  en móvil, `scrollWidth` y viewport coinciden (**390 px**).
- El lector de pantalla ve el campo como inválido y el aviso como alerta; el foco queda en el correo.
- Con valor inválido no salió una llamada a `/api/reservas/...`. En local, `/api/yo` y
  `/api/admin/sesion` respondieron 401 por ausencia de sesión y `/api/hotel` respondió 200.

Capturas revisadas:

- `consulta-error-escritorio.jpg`
- `consulta-error-movil.jpg`
