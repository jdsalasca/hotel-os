# R243 — La portada verifica la venta antes de ofrecer reservas

## Problema observado

En el sitio publicado, `/api/hotel/venta` devuelve `a_la_venta=false`. Durante la carga, la portada inicia con `venta=null`; el JSX interpreta ese valor como si la venta estuviera activa y muestra el título de reserva directa y los controles de búsqueda antes de recibir la respuesta. En una conexión lenta, el huésped ve controles que luego desaparecen. El estado no disponible también repite el aviso en el hero y en la sección inferior.

## Resultado esperado

La entrada pública representa cuatro estados sin contradicciones:

- **Comprobando:** mensaje accesible y neutral; no ofrece fechas, calendario, búsqueda ni promesa de reserva. La consulta de reservas existentes sigue disponible.
- **Disponible:** conserva el flujo actual de búsqueda y reserva.
- **No disponible:** comunica una sola vez el estado real, elimina controles de compra y conserva la consulta de reservas existentes. Solo muestra contacto si el API entrega un dato confirmado.
- **No comprobado por error:** explica que no se pudo verificar el catálogo y deja intentar la búsqueda como mecanismo de recuperación existente.

La vista evita afirmar «reserva directa» hasta que el endpoint confirme disponibilidad. No se cambian datos de producción ni el criterio de venta del backend.

## Archivos previstos

- Modificar `apps/web/src/paginas/PaginaInicio.tsx` y `PaginaInicio.test.tsx`.
- Mantener neutrales los nombres de la ruta inicial en `apps/web/src/App.tsx` y `tituloRuta.ts`, con regresiones en `App.test.tsx`, `ShellRoto.test.tsx` y `TituloRuta.test.tsx`.
- Modificar estilos existentes en `apps/web/src/styles/_componentes.scss` solo si la presentación del estado de carga lo requiere; no añadir CSS suelto ni estilos inline.
- Añadir evidencia de la ronda en `docs/evidence/round-243/` y actualizar `docs/plan.md`.

## Pasos y criterios verificables

1. **TDD rojo:** simular una respuesta pendiente a `/api/hotel/venta`. El test comprueba que no se muestran fechas, búsqueda, calendario ni el mensaje de reserva activa, y que la consulta de reservas previas sigue visible. Ejecutar el test dirigido y guardar el fallo previo al cambio.
2. **Estados visibles:** representar «comprobando», «disponible», «no disponible» y «fallo de verificación» con condiciones explícitas. Renderizar la búsqueda solo después de respuesta positiva o ante el fallback actual de error; no renderizarla mientras se espera ni cuando la respuesta sea `false`.
3. **Copy y salida:** usar un título principal y un título de pestaña neutrales mientras se comprueba o no hay venta; llamar «Inicio» al enlace global que navega a `/`; mostrar un solo mensaje de estado y una acción principal para consultar/gestionar reservas previas. Mantener teléfono, correo y el reintento de contacto únicamente cuando existan datos o un error de carga real.
4. **Regresiones:** verificar la transición pendiente→disponible, pendiente→no disponible, error del endpoint con fallback, desaparición de calendario/controles al no vender y conservación de búsqueda cuando sí vende.
5. **Verificación de frontend:** ejecutar pruebas dirigidas y suite, `npm run typecheck`, `npm run test:typecheck` y `npm run build` desde `apps/web`.
6. **Verificación visual real:** con API sintética en no disponible, revisar Chromium en 1440×1000 y 390×844. Confirmar título y navegación neutrales, una sola comunicación del estado, CTA visible, información de contacto solo si existe, ningún control de compra durante la carga/no venta, ausencia de desbordamiento horizontal y cero errores de JavaScript. Guardar capturas antes y después y resultados en `docs/evidence/round-243/verificacion.md`.
7. **Integración:** revisar diff y `git diff --check`; crear commit atómico con identidad canónica, cherry-pick a `develop`, repetir suite/typechecks/build después de integrar, push normal a `origin/develop` con la cuenta existente `jdsalasca`, comprobar autoría remota y eliminar únicamente el worktree y la rama R243 ya integrados. Preservar los worktrees de otros agentes.

## Límites

No añadir tema oscuro/claro, nuevos datos del hotel, acción de reserva mientras el catálogo confirma indisponibilidad, ni cambios de back-end. R213 sigue esperando aprobación de su plan detallado y no forma parte de esta ronda.
