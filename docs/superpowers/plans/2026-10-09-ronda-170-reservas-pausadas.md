# Ronda 170 — navegación clara cuando las reservas están pausadas

## Problema y resultado

En producción, `GET /api/hotel/venta` informa que las ventas están pausadas, pero la portada sigue invitando a buscar; el formulario queda deshabilitado y el calendario conserva controles que no pueden conducir a una reserva. La navegación pública además reparte la gestión de reservas entre `/consulta` y `/mis-reservas`, aunque el segundo ya ofrece ambos caminos.

Resultado: que el huésped entienda el estado de compra en la primera pantalla y encuentre un único acceso para administrar o consultar una reserva existente.

## Cinco cambios verificables

1. La acción principal de portada comunica que las reservas en línea están pausadas cuando la API confirma `a_la_venta: false`.
2. La portada ofrece a huéspedes existentes un acceso claro al centro `Mis reservas`.
3. En pausa se ocultan el formulario, el botón deshabilitado, la navegación mensual y el calendario; un aviso explica el estado y presenta contacto configurado solo cuando existe.
4. Cabecera y portada comparten `/mis-reservas` como acceso único de huésped, que ofrece tanto Google como código y correo.
5. La navegación mantiene `Mis reservas` como sección activa al visitar `/consulta`, para conservar el contexto de la ruta secundaria.

## Alternativas consideradas

- **Dejar el formulario y calendario visibles pero deshabilitados.** Requiere poco cambio, pero mantiene controles que anuncian una acción imposible y el mensaje actual confunde ventas pausadas con inventario no publicado.
- **Reemplazar la zona de reservas por un estado explícito y un siguiente paso.** Decisión elegida: convierte el estado real en una instrucción legible, elimina callejones sin salida y no requiere modificar los datos del hotel.
- **Solicitar datos de contacto o avisar por correo cuando se reactive la venta.** Podría ser útil después, pero hoy producción no tiene contacto público configurado y un aviso requeriría nuevos datos o backend.

## Plan de ejecución

1. Actualizar pruebas de `PaginaInicio` y `App` para nombrar los cinco comportamientos y observar los controles y destinos reales.
2. Ejecutar esos tests y confirmar que fallan antes del cambio de producción.
3. Implementar el estado condicionado por venta, los accesos unificados y sus estilos bajo clases padre SCSS/tokens.
4. Pasar pruebas focalizadas, suite del frontend, chequeo de tipos y build; revisar el diff.
5. Abrir la app local en navegador, verificar venta pausada y activa en desktop/móvil, tomar capturas y registrar resultados en `docs/evidence/round-170/`.
6. Actualizar `docs/plan.md`, crear un commit funcional atómico, cherry-pick a `develop`, verificar y hacer push; retirar la rama/worktree temporal.

## Criterios de aceptación

- Con venta pausada, la portada no ofrece buscar; no hay campos de fecha/huéspedes ni grilla/navegación de calendario y el aviso enlaza a `Mis reservas`.
- Con venta activa o estado de venta desconocido por error de API, se conservan búsqueda y calendario.
- El único enlace público de cabecera para gestionar reservas apunta a `/mis-reservas`; allí siguen visibles las opciones Google y código/correo.
- En `/consulta`, el enlace de cabecera `Mis reservas` conserva el estado activo.
- Los tests focalizados fallan antes y pasan después; pruebas, tipos y build terminan sin errores.
- Las capturas guardadas muestran desktop y móvil con pausa, además del estado de venta activa.
