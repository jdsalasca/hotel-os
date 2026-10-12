# Ronda 231 — «Mis reservas» permite reintentar una carga fallida

## Problema observado

Cuando fallaba `GET /api/mis-reservas`, la página del huésped mostraba el error sin una acción
para recuperarse. Recargar el navegador era la única salida y podía perder el chat o el formulario
de fechas que el huésped tenía abierto.

R221 ya ofrecía este patrón en el panel del hotelero. R231 lo lleva a «Mis reservas» sin cambiar
la API ni la base de datos.

## Cambio

- La carga vive en `cargarReservas()` y la comparten la entrada inicial, el reintento y los
  refrescos posteriores a cambios en fechas o grupo.
- El error incluye el botón accesible «Reintentar». Durante la petición se deshabilita y muestra
  «Reintentando…».
- El aviso permanece visible mientras se intenta recuperar la lista. Solo desaparece cuando la
  respuesta trae datos; si vuelve a fallar, el huésped puede intentarlo otra vez.
- El SCSS organiza aviso y acción en columna, usando los tokens de espaciado existentes.

## Pruebas y comprobaciones

Las tres pruebas nuevas se ejecutaron primero contra el comportamiento anterior y fallaron por
no encontrar el botón «Reintentar». Cubren la recuperación exitosa, el estado deshabilitado
mientras la petición está pendiente y la persistencia del error si el segundo intento también
falla. La promesa controlada en la prueba de espera mide el estado de la interfaz, sin depender
de la velocidad de la máquina.

| Comprobación | Resultado |
|---|---:|
| `vitest run src/paginas/PaginaMisReservas.test.tsx` | **22/22** |
| `vitest run --no-file-parallelism` | **251/251** en 32 archivos |
| `npm run typecheck` | aprobado |
| `npm run test:typecheck` | aprobado |
| `npm run build` | aprobado; Vite transformó 76 módulos |

La suite completa terminó correctamente. Su salida incluyó el aviso conocido de jsdom
(`Window.scrollTo()` no implementado) y un dato informativo de rendimiento por crear el entorno
jsdom en los 32 archivos; no afectaron el resultado.

## Revisión visual en navegador

El frontend real se sirvió con el build de esta ronda. Se interceptaron `/api/yo`, `/api/hotel` y
`/api/mis-reservas` con respuestas sintéticas para no requerir el inicio de sesión OAuth de
Google. El error, el componente, el estilo y el reintento son los del producto.

- **Escritorio, 1440 × 1000:** el documento midió 1440 px de ancho, el aviso usa `role="alert"` y
  el botón de 92 × 35 px queda 12 px debajo, alineado con el texto.
- **Móvil, 390 × 844:** aviso y botón se disponen en columna; el documento no se desborda en
  horizontal.
- **Recuperación:** en Chromium se pulsó «Reintentar» y la respuesta exitosa sustituyó el aviso
  por el estado vacío «Todavía no has reservado».

Capturas:

- [Error y botón, escritorio](error-reintentar-escritorio.png)
- [Error y botón, móvil](error-reintentar-movil.png)
- [Estado recuperado, escritorio](recuperado-tras-reintento-escritorio.png)

## Alcance

No se unificó este patrón con los reintentos de `/consulta` ni del panel. Esa refactorización
cruzaría varias pantallas y queda para una ronda propia.
