# Ronda 142 - El vacío trae cómo contactar (cierre R133)

## Cambio
- `PaginaInicio` lee `/api/hotel` una vez al abrir. Con `a_la_venta: false`, el
  `Vacio` suma el contacto público: "…o escríbenos a {email} o llámanos al
  {teléfono}"; sin contacto configurado, el mensaje base igual dice la verdad.
  Cero clases y cero estilos nuevos.

## TDD rojo-verde
- Mock de `/api/hotel` con interruptor en `PaginaInicio.test`: "el vacío trae
  cómo contactar" falla sin el cambio; con él, 7/7 en el archivo.

## Verificación (salida real, 2026-10-08)
- `PaginaInicio.test`: 7/7; `tsc --noEmit`: exit 0.
- En prod `/api/hotel` aún no trae contacto (email/teléfono vacíos): el vacío
  muestra el mensaje base, y el contacto aparecerá solo cuando el hotel lo
  configure en Panel → Hotel. Captura post-deploy — ver `vacio-desktop.png`.
- Backend sin cambios en la ronda.

## Despliegue
- Commit directo en `develop`, push y `compose up -d --build` en TopNUC.
