# Ronda 188: filtro de amenidades en disponibilidad

## Objetivo

Permitir que el huésped reduzca las ofertas ya disponibles por las amenidades del tipo de habitación, sin cambiar disponibilidad, tarifas ni reglas de reserva. Al marcar varias, la oferta debe incluir todas. Mostrar cuántas opciones coinciden y permitir limpiar el filtro.

## Alcance

- Mantener la consulta de disponibilidad existente como fuente de ofertas vendibles.
- Reutilizar `/api/amenidades/por-tipo` para obtener las amenidades asignadas a los tipos ofrecidos.
- Añadir un grupo accesible de filtros en resultados, que solo aparece cuando existen amenidades asignadas.
- Aplicar selección múltiple como intersección (todas las amenidades seleccionadas).
- Aclarar en el panel que la marca se aplica a todas las habitaciones del tipo.
- Comunicar el conteo filtrado, el estado sin coincidencias y un botón para quitar filtros.
- Conservar la lista de amenidades en cada tarjeta y sus etiquetas.
- Mostrar estado de carga/error del catálogo de amenidades de resultados.
- Estilos en SCSS y verificación visual en escritorio y móvil.

## Fuera de alcance

- Cambios en backend, disponibilidad, calendario, reservas, pagos o precios.
- Cambios en el panel de inventario: sus amenidades se siguen asignando por tipo.
- Cambios en el orden o criterios de venta de las ofertas.

## Pasos y criterios verificables

1. [x] Escribir primero una prueba de interfaz: dos tipos, amenidades distintas, filtro AND, conteo y limpieza.
2. [x] Confirmar que la prueba falla antes del cambio.
3. [x] Implementar carga resistente a respuestas obsoletas, intersección de filtros, estados legibles y estilos SCSS.
4. [x] Ejecutar pruebas frontend, typecheck de aplicación y pruebas, y build de Vite.
5. [x] Verificar visualmente escritorio y móvil, incluyendo filtro sin coincidencias y quitar filtro; registrar capturas y resultados.
6. [x] Crear evidencia completa en `docs/evidence/round-188/` y marcar esta ronda en `docs/plan.md`.
7. [x] Integrar por cherry-pick en un worktree limpio sobre `origin/develop` y volver a validar.
8. [x] Publicar en `origin/develop` y comprobar el SHA con `git ls-remote`.

## Evidencia

Resultados, comandos, capturas e integración: `docs/evidence/round-188/verificacion.md`.
