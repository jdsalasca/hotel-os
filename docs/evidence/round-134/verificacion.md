# Ronda 134 - Cambiar fechas en Mis reservas (Etapa H, rebanada UI)

Fecha: 2026-10-08. Rama: `goal/round-134-fechas-ui`. (La 132 la ocupó el colega
con CSP yendo yo ya en marcha; la 133 también es suya en curso.)

## Misión (brainstorming, vía acotada)

Opciones: (a) formulario sobre el endpoint de la R130 —bounded, cierra el
autoservicio—; (b) H6 rol (delgado + zona SecurityConfig activa del colega);
(c) check-in (estado + UI, fuera de ventana). Va (a): la API sin formulario no
la usa nadie.

## Cambio (`PaginaMisReservas.tsx` + test nuevo)

- Botón "Cambiar fechas" por fila vigente + tarjeta con nuevas fechas, Guardar
  (deshabilitado incompleto) y "Volver sin guardar". Envía al endpoint propio,
  muestra el error del servidor y relee la lista (el precio se recalcula fuera).
- El formulario vive bajo la tabla, no dentro de la celda: la primera versión
  dentro del `<td>` mezclaba visualmente "Cancelar" entre los dos campos en
  móvil; se movió tras verificarlo en captura.
- Test `PaginaMisReservas.test.tsx` (2): mueve y refresca con lo guardado; ante
  409 avisa y conserva.

## Verificación real

- Rojo→verde: 2/2 en el archivo.
- Suite frontend: 19/20 archivos en verde (68/69 tests); el único rojo es TDD en
  curso del colega en `PaginaLoginAdmin.test.tsx`, intacto y fuera de mi commit.
- `tsc --noEmit` y `tsc -p tsconfig.tests.json` exit 0.
- Visual en vivo (stack dev reconstruido, sesión simulada por init-script: sin
  OAuth en dev no hay otra forma): tarjeta "Cambiar fechas de H-DEMO1" con los
  dos campos y Guardar, desktop 1440 y móvil 390 en esta carpeta. (Nota de
  entorno: `page.add_init_script` es camelCase; el `initScript` del navegar no
  quedó demostrado; el viewport heredado de otra sesión exige fijarlo explícito.)
- Backend sin tocar en la ronda (endpoint de la R130, suite 414 entonces).

## Trabajo en equipo

- Solo mis rutas en el commit (página + test + evidencia + plan). Lo del colega
  (OAuth/PuertaAdmin/hotel-vacio) intacto y fuera.

## Archivos

- Tocados: `web/src/paginas/PaginaMisReservas.tsx`.
- Nuevos: `web/src/paginas/PaginaMisReservas.test.tsx`, este archivo, 2 PNG.
- Plan: entrada de ronda 134.

## Pendiente

- Check-in/out y estados de estancia; H6; identidad por (subject, issuer).
