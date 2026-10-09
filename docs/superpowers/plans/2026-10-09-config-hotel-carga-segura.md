# Ronda 186: proteger la configuración del hotel durante la carga

## Problema observado

`PaginaAdminHotel` muestra controles vacíos y activos mientras solicita los datos guardados. Si la respuesta llega después de que el hotelero empiece a escribir, `setValores` reemplaza sus cambios; el botón también permite enviar antes de conocer el estado actual.

## Cambio

- Indicar que el formulario está ocupado mientras lee la configuración.
- Desactivar campos, área de texto y botón de guardar durante esa lectura.
- Al terminar, habilitar el formulario tanto si la lectura tuvo éxito como si falló, para permitir edición y recuperación manual.

## Criterios verificables

1. Una lectura diferida mantiene desactivados los campos y el guardado hasta recibir respuesta.
2. Al cargar la configuración, los valores recibidos se muestran y los controles se habilitan.
3. Si la lectura falla, el formulario se habilita y aún permite guardar una corrección.
4. Las pruebas fallan primero con el formulario actual; suite, chequeos TypeScript y build Docker pasan después.
5. Chrome confirma visualmente el estado de carga y el estado cargado en escritorio y móvil.

## Verificación

Ejecutar `npm ci`, prueba enfocada en rojo, implementación, suite y chequeos de tipos. Compilar la imagen web con el Dockerfile del proyecto y revisar los dos estados en Chrome con API simulada. Conservar comandos, resultados y capturas en `docs/evidence/round-186/`.
