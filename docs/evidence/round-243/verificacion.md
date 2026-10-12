# R243 — Verificación de disponibilidad en la portada

Fecha: 2026-10-12. Verificación local contra el cambio R243 y su base `05e19747`.

## Hallazgo reproducido

Con la respuesta de `/api/hotel/venta` pendiente, la versión base renderiza el título «reserva directa», el enlace «Ver disponibilidad», fechas, búsqueda y calendario. El huésped ve el flujo de reserva antes de que el sitio sepa si puede vender. La captura de antes reproduce esa espera con API sintética.

## Resultado

- **Comprobando:** título principal «Hotel Eridu», título de pestaña «Hotel Eridu · Inicio» y navegación «Inicio». No aparece formulario, fecha, búsqueda ni calendario. Se conserva el acceso a reservas existentes.
- **Disponible:** vuelve el encabezado de reserva directa, el formulario, el calendario y «Ver disponibilidad».
- **No disponible:** una sola comunicación del estado; sin controles de compra; se conserva el acceso a reservas existentes. El contacto solo aparece cuando la API sintética lo proporciona.
- **Error al verificar venta:** la prueba automatizada confirma el mensaje de error y conserva el fallback actual de búsqueda.
- **Contacto ausente:** comprobación en Chromium sin sección de contacto ni datos inventados.

## Pruebas primero

- La regresión de disponibilidad pendiente falló antes del cambio: encontró la afirmación «reserva directa» y los controles en vez del título neutral esperado.
- La regresión de navegación falló con `Reservar` en el enlace que realmente lleva a `/`.
- Las regresiones del título de pestaña fallaron con `Hotel Eridu · Reserva directa` tanto en la página como en la función de títulos.
- Las tres regresiones pasaron tras sus respectivos cambios.

## Verificación automatizada

- `PaginaInicio.test.tsx`: 48/48; `App.test.tsx`: 10/10; `TituloRuta.test.tsx`: 7/7.
- Suite frontend: 262/262 en 35 archivos.
- `npm run typecheck`, `npm run test:typecheck` y `npm run build`: código 0. Vite transformó 77 módulos.
- Estos resultados son de la rama aislada; se repetirán después del cherry-pick a `develop`.

La suite imprime el error deliberado `pum` del test del muro de error y el aviso jsdom sobre `window.scrollTo()`. Ambos son parte de esas pruebas; el proceso termina con código 0.

## Inspección visual en Chromium

Respuestas de API sintéticas, sin llamadas mutantes ni cambios de datos. Viewports: escritorio 1440×1000 y móvil 390×844.

- Pendiente, escritorio: formulario, campos de fecha y calendario ausentes; CTA a reservas existentes presente.
- No disponible, escritorio y móvil: un aviso de indisponibilidad, contacto solo con datos configurados y ninguna fecha o búsqueda.
- Disponible, móvil: formulario, dos fechas, calendario y CTA presentes.
- Ancho del documento: 1440/1440 en escritorio y 390/390 en móvil. Sin errores de consola en los estados disponible y no disponible.

### Capturas

- Antes, con consulta pendiente: [escritorio](antes-escritorio-pendiente.jpg).
- Después, con consulta pendiente: [escritorio](despues-escritorio-pendiente.jpg).
- Después, no disponible: [escritorio](despues-no-disponible-escritorio.jpg) y [móvil](despues-no-disponible-movil.jpg).

Las imágenes se tomaron en local con contenido de prueba; no son evidencia de despliegue ni alteran la web publicada.

## Integración

- Cherry-pick a `develop`: `8cbe950` (`Alinea la portada con el estado de venta`) y `608f4e9` (`Documenta la verificación visual de R243`). Autor y committer: `JUAN DAVID SALAS CAMARGO <31742974+jdsalasca@users.noreply.github.com>`.
- Verificación posterior al cherry-pick, ejecutada desde `apps/web`: suite **262/262** en 35 archivos; `npm run typecheck`, `npm run test:typecheck` y `npm run build` terminaron con código 0; Vite transformó 77 módulos.
- Revisión independiente del rango `05e19747…67c391d`: Ready to merge; cero hallazgos críticos o importantes. Dos detalles menores se difieren explícitamente: título estático pre-montaje en `apps/web/index.html` y frase incondicional del pie sobre reservas directas.
- Pendiente al redactar esta evidencia: publicar `develop` en `origin` y confirmar el commit remoto bajo la cuenta existente `jdsalasca`.
