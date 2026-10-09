# Ronda 150 - Al navegar se vuelve arriba

## Hallazgo en prod
Con scroll a 1500 en la portada, clic en "Consultar reserva" aterriza con scroll
en 492: la página nueva se abre a la mitad (medido con navegador). React Router
no restaura el scroll solo.

## Cambio
- `VolverArribaAlNavegar` en `App`: `window.scrollTo(0, 0)` con cada cambio de
  ruta. Sin estilos, sin estado, sin pedir nada.

## TDD rojo-verde
- `App.test` (navegación real a nivel App): sin el componente, `scrollTo` jamás
  se llama; con él, 1/1.

## Verificación (salida real, 2026-10-09)
- `vitest` full en verde; `tsc --noEmit` exit 0. Backend sin cambios.
- Visual: repetida la sonda en prod tras el deploy (1500 → 0). Captura de la
  página de consulta arriba del todo — ver `consulta-arriba.png`.

## Despliegue
- Merge a `develop`, push y `compose up -d --build` en TopNUC con health OK.
