# Ronda 232 — Reintento de canales de contacto

Fecha: 2026-10-11

## Falla observada

En `PaginaInicio`, el fallo de `GET /api/hotel` se convertía en `contacto = null`, igual que una respuesta válida sin correo ni teléfono. Los estados de reserva no disponible y búsqueda sin habitaciones omitían así toda explicación y no ofrecían recuperación.

## Cambio

- La carga distingue error de red de una respuesta válida sin canales.
- En ambos estados donde se ofrece contactar, el error explica qué falló y da un botón accesible para volver a consultar.
- Mientras se repite la consulta, el botón se deshabilita y anuncia «Reintentando…».
- Se ignora cualquier respuesta de una consulta cuyo componente ya fue desmontado.
- No se fabrican canales cuando el hotel responde correctamente sin contacto.

## Verificación automatizada

- La nueva prueba de regresión falló primero porque no encontraba el aviso de error en la versión anterior.
- Casos dirigidos para los dos estados de contacto: **2 aprobados**.
- `npm test`: **232 pruebas aprobadas, 0 fallidas; 32 archivos aprobados**.
- `npm run typecheck`: código 0.
- `npm run test:typecheck`: código 0.
- `npm run build`: código 0; Vite transformó 75 módulos y generó los artefactos de producción.
- `git diff --check`: código 0, sin errores de espacios o formato en el diff.

## Verificación visual

Playwright abrió el build de producción local y usó respuestas sintéticas para todos los endpoints consultados. El primer `GET /api/hotel` devolvió intencionalmente HTTP 503; al activar «Reintentar», devolvió correo y teléfono sintéticos y la interfaz los mostró como enlaces `mailto:` y `tel:`. Las consultas de sesión devolvieron 204 para representar una visita anónima. No se usaron credenciales ni datos de producción.

- Escritorio, viewport 1440 × 1000: [contacto-escritorio-1440x1000.png](./contacto-escritorio-1440x1000.png).
- Móvil, viewport 390 × 844: [contacto-movil-390x844.png](./contacto-movil-390x844.png).
- Móvil, página completa: [contacto-movil-390px-completa.png](./contacto-movil-390px-completa.png).
- En móvil, `document.documentElement.scrollWidth` fue 390 px para un viewport de 390 px. El aviso tuvo rol `alert` y el botón nativo fue visible al desplazar hasta esa sección.
- Consola: una respuesta HTTP 503 intencional del mock; 0 advertencias y 0 excepciones JavaScript.
