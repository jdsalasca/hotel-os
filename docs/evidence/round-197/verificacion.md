# Ronda 197 — Mensajes pendientes por reserva

## Resultado visible

La tabla de recepción identifica junto a «Ver detalle» cuántos mensajes del huésped siguen
sin leer para cada reserva. Al abrir la conversación, el servidor marca sus mensajes como
vistos y la lista actualiza la cifra. El endpoint administrativo conserva `nuevos` para el
contador global y agrega `porReserva`, agrupado por código.

## Verificación

- Prueba nueva de interfaz: primero falló porque no aparecía `2 sin leer`; tras implementar
  la insignia y la actualización al abrir el hilo, el archivo pasó 12/12.
- Prueba nueva de API: primero falló por ausencia de `$.porReserva.CHAT01`; tras agrupar los
  mensajes por reserva pasó 5/5 y comprobó que abrir CHAT01 la quita sin afectar CHAT04.
- Suite frontend: **159/159** en 27 archivos.
- Typecheck frontend: `npm run test:typecheck`, código 0.
- Build web: `npm run build`, código 0; Vite transformó 71 módulos.
- Suite completa API: **459 pruebas, 0 fallidas, 0 errores, 1 omitida**, `BUILD SUCCESS`.
- Chrome local con respuestas sintéticas (no se usaron datos ni credenciales reales): insignia
  visible en escritorio y móvil a 390 px; al abrir el hilo desaparece. La ventana móvil midió
  390 px y el documento 390 px, sin desbordamiento horizontal. Después de abrir la conversación,
  la consola no registró mensajes.

## Capturas

- Escritorio: [escritorio.png](escritorio.png)
- Móvil (390 px): [movil.png](movil.png)
