# Ronda 201 — Actualización automática de mensajes sin leer

## Resultado

Los contadores de mensajes nuevos se actualizan mientras el usuario mantiene abierta la página:

- El aviso global del huésped se consulta cada 30 segundos.
- Los conteos en «Mis reservas», el panel administrativo y la lista de recepción siguen el mismo intervalo.
- La consulta periódica solo corre mientras la página está visible. Al volver a la pestaña, se actualiza de inmediato.
- Sin sesión no queda un temporizador activo. Al desmontar el componente se retiran el intervalo y el listener.
- Una falla temporal conserva el último conteo válido. Las consultas de mensajes complementarias no bloquean la carga del chat.

No se modificaron la API ni la base de datos.

## Evidencia automatizada

- Suite frontend: **28 archivos, 179 pruebas aprobadas**. Registro: [pruebas-frontend.log](pruebas-frontend.log).
- Typecheck de pruebas: `npm run test:typecheck`, salida 0. Registro: [typecheck-pruebas.log](typecheck-pruebas.log).
- Typecheck de aplicación y build: `npm run build`, salida 0; Vite transformó 73 módulos. Registro: [build.log](build.log).
- `git diff --check`: salida 0.
- Prueba específica del temporizador y sus consumidores: 5 archivos, 35 pruebas aprobadas.

El registro de la suite incluye el error simulado `pum` de la prueba existente del límite de errores y un aviso de `scrollTo` no implementado por JSDOM; ambos son diagnósticos de pruebas, no fallos de la suite.

## Revisión visual local

Se montó el componente real `BadgeMensajes` y su SCSS de producción en una fixture local con respuestas API sintéticas. No se usaron cuentas ni mensajes reales.

| Vista | Resultado |
|---|---|
| Escritorio, 1440 × 1000 | La navegación muestra «3 sin leer» junto a «Mis reservas». |
| Móvil, 390 × 844 | Menú abierto, insignia visible y documento de 390 px de ancho; sin desbordamiento horizontal. |
| Accesibilidad | El árbol accesible anuncia `status: 3 mensajes sin leer`. |
| Consola | Cero errores y advertencias después de cargar la fixture. |

Capturas: [escritorio](escritorio.png) · [móvil](movil.png).
