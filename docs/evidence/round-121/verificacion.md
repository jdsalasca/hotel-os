# Ronda 121 - Toda pantalla del panel ofrece entrar (fin del callejón sin salida)

## Reporte
El dueño en prod: `/admin/hotel` muestra "Sesión requerida / Inicia sesión para
configurar el hotel" SIN ningún botón ni enlace para hacerlo. Reproducido en
prod con navegador: el aviso no lleva a `/admin/entrar` y la navegación
principal tampoco lo enlaza. Solo 2 de 9 pantallas enlazaban la entrada.

## Causa raíz
`PaginaLoginAdmin` (`/admin/entrar`, con clave y Google) existe y funciona —
verificado en prod: credenciales falsas devuelven "credenciales inválidas" limpio
(la tubería front → `/api/admin/login` → 401 está viva). El bug es UX: 7 de 9
pantallas cerradas (`Panel`, `Hotel`, `Inventario`, `Integraciones`,
`Indicadores`, `Auditoria`, `Lugares`) mostraban el aviso sin salida. Ni siquiera
`/admin` (la puerta) enlazaba la entrada.

## Cambio
- `PuertaAdmin` en `Estado.tsx`: el mismo `Aviso` en todas, siempre con botón
  `Iniciar sesión → /admin/entrar`. Un solo lugar para este patrón.
- Las 9 pantallas lo usan, conservando cada una su texto (`Hoy` y `Reservas` ya
  tenían el enlace: ahora usan el compartido; `Inventario` e `Indicadores`
  entraron vía el commit de la R120 del colega, mismo contenido).
- Cero clases y cero estilos nuevos (reutiliza `aviso` + `boton--primario`).

## TDD rojo-verde
- `Estado.test.tsx`: `PuertaAdmin` indefinido → error de export; con el
  componente, 1/1 (título, texto y `href="/admin/entrar"`).

## Verificación (salida real, 2026-10-08)
- `tsc --noEmit` y `tsc -p tsconfig.tests.json`: exit 0.
- `vitest`: todo verde junto al trabajo del colega (conteos exactos inestables
  porque edita tests en paralelo; mi archivo 1/1 y 0 fallos en cada corrida).
- Visual: captura post-deploy de `/admin/hotel` en prod (el mismo URL del
  reporte) con el botón visible — ver `puerta-desktop.png`.
- Backend sin cambios (no hizo falta).

## Despliegue
- Merge a `develop`, push y `compose up -d --build` en TopNUC, con captura real
  del URL reportado después.
