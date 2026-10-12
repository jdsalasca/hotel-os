# R244 — Metadatos y pie de página neutrales

Fecha: 2026-10-12. Verificación local en la rama aislada, con API sintética y sin modificar datos.

## Cambio

- El título y la descripción iniciales del archivo index.html dejan de presentar el sitio como canal de reserva directa. Open Graph comparte el nombre neutral «Hotel Eridu · Inicio» y una descripción informativa.
- El pie público dice «Información y gestión de reservas de Hotel Eridu», incluso cuando la venta está desactivada.
- No se modificaron estilos, lógica de reserva ni vistas administrativas.

## Pruebas

- RED: las regresiones añadidas a TituloRuta.test.tsx y App.test.tsx fallaron antes del cambio por el título estático y el texto incondicional del pie.
- GREEN dirigido: App.test.tsx y TituloRuta.test.tsx, 18/18.
- Suite completa: npm run test, 263/263 en 35 archivos. Vitest mostró su aviso informativo habitual sobre window.scrollTo() de jsdom; salida 0.
- npm run test:typecheck: salida 0.
- npm run build: salida 0; TypeScript limpio y Vite transformó 77 módulos.
- git diff --check: salida 0.

## Inspección visual

Playwright CLI, respuesta sintética a_la_venta false, sesiones anónimas y hotel sin ubicación configurada. No se hicieron POST ni se alteraron datos.

| Vista | Viewport | Ancho del documento | Estado |
|---|---:|---:|---|
| Escritorio | 1440×1000 | 1440/1440 | Aviso de venta no disponible; formulario ausente; enlace a gestionar reservas visible |
| Móvil | 390×844 | 390/390 | Mismo estado, sin desbordamiento horizontal |

El título de pestaña fue «Hotel Eridu · Inicio». El pie muestra el texto neutral. Hubo dos errores de consola por respuestas 401 de /api/yo y /api/admin/sesion, esperables al no iniciar sesión; cero advertencias y ninguna excepción de JavaScript.

### Capturas

- Escritorio inicial: [JPG](escritorio-inicial.jpg).
- Escritorio completo, incluido el pie: [JPG](escritorio-completo.jpg).
- Móvil inicial: [JPG](movil-inicial.jpg).
- Móvil completo: [JPG](movil-completo.jpg).

Las imágenes documentan el navegador local con contenido de prueba; no demuestran despliegue en producción.

## Integración

Pendiente: revisión independiente, cherry-pick a develop, verificación posterior, push y comprobación de autoría GitHub jdsalasca.
