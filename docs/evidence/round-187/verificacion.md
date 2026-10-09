# Ronda 187 — selector de moneda compartido

## Cambio

El catálogo de nueve monedas se comparte entre Datos del hotel y el inventario. El campo Moneda
de Datos del hotel ahora es un selector nativo con etiqueta accesible. Si la configuración
existente contiene un código que no aparece en el catálogo, se mantiene seleccionado y se envía
sin cambios mientras el hotel no elija otro. Si no se puede leer la configuración, el formulario
conserva la recuperación manual del código ISO de tres letras.

## TDD y pruebas

- RED: el primer test no encontró un combobox llamado «Moneda»; los dos casos existentes pasaron.
- GREEN: tras extraer el catálogo y añadir el selector, la suite enfocada pasó con 3 pruebas.
- RED de regresión: la configuración `CHF` quedó en valor vacío porque no había opción coincidente.
- GREEN de regresión: al mostrar el código actual fuera del catálogo, la suite enfocada pasó con
  4 pruebas; también confirmó que `CHF` se conserva al guardar.
- RED de recuperación: con la lectura inicial fallida, la moneda seguía siendo un selector y no
  admitía `CHF` (`expected INPUT, received SELECT`).
- GREEN de recuperación: durante ese error se muestra el campo manual de tres letras; permite
  enviar `CHF`. La suite enfocada pasó con **5 pruebas**.
- `npm test`: **25 archivos y 139 pruebas aprobadas**. jsdom mostró el aviso preexistente de
  `Window's scrollTo()` no implementado; no afectó el resultado.
- `npm run test:typecheck`: código de salida 0.
- `npm run typecheck`: código de salida 0.
- `npm run build`: código de salida 0; Vite 7.3.7 transformó 71 módulos. Bundle JavaScript
  375.14 kB (107.96 kB gzip) y CSS 22.32 kB (4.80 kB gzip).
- `git diff --check`: sin errores.

## Revisión visual en Chrome

Se revisó `/admin/hotel` servido desde `http://127.0.0.1:5190`. La sesión y las respuestas API
fueron simuladas con datos sintéticos en el navegador; no se usaron credenciales ni datos de
producción.

- Carga correcta, escritorio 1365 × 900: documento de 1350 px de ancho, sin desbordamiento; las
  nueve monedas están disponibles y `COP` permanece seleccionado.
- Carga correcta, móvil 390 × 844 con emulación táctil: documento de 390 px, sin desbordamiento;
  el selector está visible y `COP` permanece seleccionado.
- Lectura fallida, escritorio y móvil: el campo manual reemplaza el selector, acepta hasta tres
  letras y permanece visible; ambos documentos conservan el ancho del viewport.
- Consola de Chrome: sin errores ni advertencias en los cuatro estados revisados.
- Capturas: `moneda-escritorio.png`, `moneda-movil.png`,
  `moneda-recuperacion-escritorio.png` y `moneda-recuperacion-movil.png`.

## Alcance de la evidencia

El build y la verificación visual son locales. Las capturas documentan esta rama con una API
simulada; no demuestran despliegue ni funcionamiento contra producción.
