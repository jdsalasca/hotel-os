# Ronda 185: hacer visible el alta inicial del inventario

## Problema observado

En `PaginaAdminInventario`, el hotelero que aún no tiene habitaciones ve primero el selector de mes, un calendario vacío y el mensaje de habitaciones sin registrar. El asistente que permite crear tipo, habitación, servicios y plan aparece después de esos bloques.

## Cambio

- Mostrar el asistente de alta guiada antes del calendario y de la tabla cuando los datos cargados confirmen que faltan tipos, habitaciones o planes.
- Mantener el orden operativo actual cuando el inventario está configurado.
- Reutilizar el mismo asistente y formularios; no duplicar IDs ni cambiar las llamadas a la API.
- Corregir el enlace contextual para que describa la posición real del asistente.

## Criterios verificables

1. Con inventario incompleto, el encabezado «Alta del hotel, paso a paso» aparece en el DOM antes de «Calendario de ocupación».
2. Con tipos, habitación y plan existentes, el calendario sigue antes del asistente.
3. La suite enfocada reproduce primero el fallo en la rama limpia y pasa tras el cambio.
4. Suite completa, chequeos TypeScript y build de producción pasan.
5. La vista se revisa visualmente en escritorio y móvil, y las capturas se conservan en `docs/evidence/round-185/`.

## Verificación

Ejecutar primero la suite base y la prueba enfocada en rojo; implementar después; correr suite, typechecks y build; abrir los dos estados con API simulada en navegador y guardar capturas con salida/comandos en `docs/evidence/round-185/verificacion.md`.
