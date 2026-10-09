# Ronda 159 - El pie esconde el panel sin sesión

## Cambio
- El pie mostraba los 7 accesos del panel a todo el mundo (incluidos visitantes
  que caían en puertas con candado). Ahora esa navegación solo sale con sesión
  de personal; el visitante ve marca, contacto y legal. La cabecera ya orienta
  a cada lado por su cuenta. Cero estilos nuevos.

## TDD rojo-verde
- `App.test`: sin sesión el `nav` del pie existe (falla); con el cambio, 5/5
  (oculto sin sesión, presente con ella, resto intacto).

## Verificación (salida real, 2026-10-09)
- `vitest` full en verde; `tsc --noEmit` exit 0. Backend sin cambios.
- Visual: captura post-deploy del pie en prod sin sesión — ver `pie-publico.png`.

## Despliegue
- Merge a `develop`, push y `compose up -d --build` en TopNUC con health OK.
