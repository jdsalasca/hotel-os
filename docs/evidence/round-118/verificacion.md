# Ronda 118 - El detalle pertenece a la búsqueda, no al formulario (Etapas E + B)

Fecha: 2026-10-08. Rama: `goal/round-118-detalle-snapshot`.

## Misión (brainstorming, vía acotada)

Opciones: (a) E4+E5 snapshot del detalle —bounded, embudo central—; (b) comprobante
descargable (zona activa del colega, colisión); (c) calendario con arrastre
(alto/alto, fuera de ventana). Va (a): el desglose visible podía ser de otro viaje.

## Problemas (reproducidos con tests antes de afirmar)

- **E5.** `verDetalle()` pedía con el formulario vivo: buscar A→B, editar a C sin
  buscar y abrir el detalle traía el desglose de C sobre la tarjeta de A→B.
  Rojo: `detalle?...llegada=2030-07-01...` en vez de `llegada=2030-06-10`.
- **E4.** La caché era por habitación sin plan y el interruptor por habitación: al
  editar el formulario, el desglose abierto desaparecía ("Ocultar detalle" sin
  nada) y dos planes de la misma habitación compartían interruptor (abrir el
  segundo cerraba el primero sin pedir nada). Rojo: `.desglose__noches` null tras
  editar; segundo clic con 1 sola petición.
- Drive-by en el mismo bloque: `key={habitacion.id}` duplicada con dos planes y el
  subtítulo con fechas vivas en vez de buscadas.

## Cambio (`PaginaInicio.tsx` + test nuevo)

- `baseBusqueda = busqueda ?? {llegada, salida, huespedes}` (el mismo fallback que
  `elegir()`); `claveDetalle = id| fechas | huéspedes | plan.id`.
- `verDetalle`, render, cargando y subtítulo usan la clave/parámetros del snapshot.
- Nueva búsqueda limpia `detalles/abierto/cargando`.
- Test `PaginaInicio.test.tsx` (3, página real con `api` mockeado + MemoryRouter).

## Verificación real

- Rojo→verde: 3/3 en el archivo; suite frontend **12 archivos / 45 tests**;
  `tsc --noEmit` y `tsc -p tsconfig.tests.json` exit 0; `vite build` exit 0.
- Visual en vivo (stack dev reconstruido con el cambio): búsqueda 10→12 dic con
  ofertas reales → detalle abierto (Plan Flexible −20 %) → salida editada a 15 dic
  sin buscar → **resumen intacto ("al 12 de dic"), desglose intacto, sin redirigir**.
  Capturas desktop y móvil 390px en esta carpeta. (Sin este fix: subtítulo al 15 y
  desglose vacío.)
- Backend sin tocar en la ronda (cambio solo frontend): no se corre su suite.

## Trabajo en equipo

- Solo `PaginaInicio.tsx` + su test en el commit (el colega va en
  comprobante/chat/mis-reservas: intactos). `round-118/` nuevo y libre.
- Nota de entorno: vitest en este host necesita `--pool=forks` cuando el árbol
  está roto; con `npm ci` limpio el pool por defecto vuelve a ir.

## Archivos

- Tocados: `web/src/paginas/PaginaInicio.tsx`.
- Nuevos: `web/src/paginas/PaginaInicio.test.tsx`, este archivo, 2 PNG.
- Plan: entrada de ronda 118.
