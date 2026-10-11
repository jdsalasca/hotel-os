# Ronda 206 — mensaje honesto cuando la reserva no está disponible

## Comportamiento

La portada ya no atribuye la indisponibilidad a una pausa ni promete que el huésped vuelva más adelante. Explica que el sitio no puede confirmar una reserva nueva, conserva los contactos solo cuando están configurados y mantiene el acceso a reservas existentes. El formulario y el calendario permanecen ocultos cuando la API informa que no hay venta.

## TDD y pruebas

- Prueba focalizada antes del cambio: 31 pasaron y 2 fallaron por el título y el mensaje anteriores.
- `npm test -- --no-file-parallelism --reporter=dot src/paginas/PaginaInicio.test.tsx`: 33/33.
- `npm test -- --no-file-parallelism --reporter=dot`: 183/183 en 29 archivos, incluida R204 ya integrada en la base.
- `npm run test:typecheck`: pasó.
- `npm run build`: pasó; TypeScript y Vite completaron el build.

## Revisión visual

Se ejecutó la app local con respuestas API simuladas: venta no disponible, configuración de contacto sintética (`example.test`) y sesiones ausentes. No se usaron escrituras ni datos productivos.

- [Portada en escritorio, 1440 px](./01-disponibilidad-escritorio.png)
- [Portada en móvil, 390 × 844](./02-disponibilidad-movil.png)
- En móvil: ancho de documento 390 px; ancho visible 390 px; sin desborde horizontal.
- En ambos tamaños: copy anterior ausente, formulario/calendario ausentes y enlace para consultar reservas existentes visible.
- Errores de consola: ninguno.
