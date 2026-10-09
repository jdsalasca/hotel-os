# Ronda 182 — cinco mejoras de usabilidad

Fecha: 2026-10-09

## Cambios

1. Con las reservas pausadas, la portada ofrece una sola acción para gestionar una reserva y la deja visible en el hero. El aviso conserva la explicación y los datos de contacto.
2. Al cambiar fechas, los campos empiezan con la llegada y salida actuales.
3. La llegada no admite fechas pasadas; la salida debe ser al menos el día siguiente a la llegada.
4. El formulario explica que el precio se calcula de nuevo con las tarifas vigentes.
5. Al abrir el formulario, el foco pasa a la fecha de llegada.

## Verificación

- TDD: cada uno de los cinco cambios tuvo una prueba fallida antes de implementar y verde después. Se agregó además una regresión para confirmar que con venta activa siguen visibles «Ver disponibilidad» y «Gestionar una reserva».
- `npm test`: **24 archivos, 130 pruebas aprobadas**.
- `npm run test:typecheck`: salida 0.
- `npm run build`: salida 0; TypeScript y Vite compilaron 70 módulos. JavaScript 374.09 kB (107.71 kB gzip), CSS 22.29 kB (4.79 kB gzip).
- Revisión visual local: portada pausada y edición de fechas en escritorio y en vista móvil de 390 × 844. La portada conserva un solo enlace de gestión en el hero y no muestra el calendario; el formulario muestra las fechas existentes, el aviso de tarifa y el foco visible en llegada. En la vista de 390 px, `scrollWidth` fue 390 px: no hubo desbordamiento horizontal.
- Los datos de las capturas son una maqueta aislada (`ana@example.test`, `H-R182DEMO` y contacto `example.test`); no se consultó ni modificó la base real. La consola mostró solo el 401 esperado al comprobar la sesión administrativa de una cuenta huésped.

## Capturas

- `portada-escritorio.png`
- `portada-movil.png`
- `mis-reservas-escritorio.png`
- `mis-reservas-movil.png`
