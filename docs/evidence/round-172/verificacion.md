# Ronda 172 - El auditor de accesibilidad llevaba muerto desde que se escribió

## El hueco
`tools/accesibilidad/auditar.mjs` es la herramienta que el proyecto usa para comprobar
accesibilidad, y **no arrancaba nunca**. Nadie lo ejecutaba, así que nadie lo sabía: dos
fallos encadenados, ambos antes de medir la primera página.

1. `page.goto(..., { waitUntil: 'networkidle' })`: la web mantiene conexiones abiertas,
   así que la red nunca queda quieta. `page.goto: Timeout 30000ms exceeded` en la primera
   ruta. Ni una sola página llegaba a auditarse.
2. `chromium.launch()` + `browser.newPage()`: `@axe-core/playwright` exige
   `browser.newContext()` y aborta con `Please use browser.newContext()`.

Además, solo miraba las 6 rutas públicas. El panel —donde el hotel pasa el día— nunca se
midió, porque todas las rutas del script eran públicas.

## Cambio
- Espera por contenido (`domcontentloaded` + margen para React y datos) en vez de
  `networkidle`. Ese era el primer fallo y el que impedía todo lo demás.
- `browser.newContext()`, que es lo que exige axe.
- Se amplía a 6 rutas del panel con sesión de demostración.
- Filtro explícito WCAG 2.0/2.1 A+AA; se deja fuera `best-practice` (reporta estilo, no
  incumplimiento, y genera ruido que nadie iba a corregir).
- Cada violación grave imprime id, ayuda y nodos afectados: antes solo contaba, y para
  arreglar había que abrir `informe.json` y correlacionar a mano.
- `compose.accesibilidad.yaml` levanta una API demo en su propio volumen
  (`a11y.sqlite3`), así que la auditoría no toca la base del hotel.

## TDD rojo-verde
Rojo reproducido: el script aborta con `Timeout` / `Please use browser.newContext()`.
Verde: `accesibilidad: sin violaciones graves`, exit 0, 12/12 rutas.

## Verificación (real, 2026-10-09, stack de contenedor)
- 6 rutas públicas: `/`, `/consulta`, `/reserva`, `/mis-reservas`, `/privacidad`,
  `/terminos` — 0 violaciones (0 graves).
- 6 rutas del panel: `/admin/hoy`, `/admin/reservas`, `/admin/inventario`,
  `/admin/indicadores`, `/admin/actividad`, `/admin/integraciones` — 0 (0 graves).
- `accesibilidad: sin violaciones graves`, **exit 0**.
- Una captura por página en `docs/evidence/round-172/` y `informe.json`.

## Riesgo / limitación
- El acceso al panel usa la cuenta demo (`HOTEL_DEMO_ADMIN`, solo arranca fuera de
  produccion; lo verifica la API). Sin esas variables el panel se omite y el script lo
  avisa: el informe no presume cobertura.
- No se tocó ningún archivo de recepción ni estilos compartidos, para no pisar el trabajo
  paralelo de otra agente en recepción.

## Estado real de la app
Cero violaciones graves en las 12 rutas. La app es accesible; lo que estaba roto era la
herramienta que lo comprobaba. La ronda no «arregla» accesibilidad porque no la necesitaba:
la deja medible por primera vez.