# Ronda 186: carga segura de Datos del hotel

## Cambio

Mientras se obtiene la configuración existente, el formulario anuncia `aria-busy="true"` y desactiva sus campos y el botón de guardado. Al completar la lectura, los controles vuelven a habilitarse; una lectura fallida también deja disponible la recuperación manual.

## Pruebas y compilación

- `npm ci`: completado; 150 paquetes instalados, 0 vulnerabilidades reportadas.
- TDD rojo: `npm test -- src/paginas/PaginaAdminHotel.test.tsx` falló inicialmente porque el campo Nombre seguía habilitado durante la lectura; el caso de recuperación por error pasó.
- TDD verde: la prueba enfocada pasó (1 archivo, 2 pruebas).
- `npm test`: **25 archivos y 136 pruebas pasaron**. Vitest mostró el aviso existente de jsdom `Not implemented: Window's scrollTo() method`; no afectó el resultado.
- `npm run test:typecheck`: código de salida 0.
- `npm run typecheck`: código de salida 0.
- `git diff --check`: código de salida 0.
- `docker build -t hotel-os-r186-web:verify .`: completado; Vite compiló 70 módulos y Docker exportó la imagen `hotel-os-r186-web:verify` (digest `sha256:daf7335ff31eb13265b174136680c48eae3c07c74dc0627cc17af274d539ec71`).

## Revisión visual y de interacción

Se revisó la ruta `/admin/hotel` en Chrome con sesión y API simuladas. La lectura de configuración se mantuvo pendiente para observar la carga, y luego se resolvió con datos de ejemplo.

- Escritorio, 1365 × 900: durante la carga `aria-busy="true"` y los 12 controles del formulario estaban desactivados. Tras recibir la respuesta, `aria-busy="false"`, ningún control quedó desactivado y se mostraron los datos de ejemplo.
- Móvil, 390 × 844: se repitieron ambos estados. El documento midió 390 px de ancho, sin desbordamiento horizontal; los 12 controles se desactivaron durante la lectura y ninguno después.
- Consola: sin errores ni advertencias de aplicación; solo mensajes informativos de Vite y React DevTools.
- La API fue simulada en navegador local; esta evidencia no representa una prueba contra producción ni un despliegue.

Capturas conservadas en esta carpeta:

- `carga-escritorio.png`
- `cargado-escritorio.png`
- `carga-movil.png`
- `cargado-movil.png`
