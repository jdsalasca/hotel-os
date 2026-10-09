# Ronda 160 - Guardar nombre recarga (cazado en E2E real)

## Cómo se cazó
Stack throwaway local (compose + override de puertos, admin propio vía init,
TODO demolido después con `down -v`): login → guardar "E2E Dos" → aviso ok pero
saludo clavado en "e2e". El `comprobar()` refrescaba el hook de la página, no la
cabecera ya montada. Backend inocente (`/api/admin/sesion` devolvía el nombre
bien): bug 100% frontend.

## Cambio
- `PaginaAdminPanel`: tras guardar, recarga completa (mismo patrón que entrar y
  salir) en vez de refresco que no repinta. Fuera el aviso "Nombre guardado" y
  el estado muerto (el reload ES la confirmación: el nombre aparece arriba).
- Probado en vivo: guardar "E2E Tres" → recarga → "Bienvenido de vuelta,
  E2E Tres · Administrador"; logout del header → `/admin/entrar`.

## TDD rojo-verde
- Test "recarga con el saludo fresco" falla sin el cambio (navega, no asigna);
  con él, todo el archivo en verde.

## Verificación (salida real, 2026-10-09)
- `vitest` 24 archivos / 94 tests en verde; `tsc` ×2 exit 0; `vite build` ok
  (el stack E2E lo construyó y sirvió).
- Captura del saludo + salir + Mi cuenta en vivo — ver `saludo-e2e.png`.
- Backend sin cambios en la ronda.

## Despliegue
- Merge a `develop`, push y `compose up -d --build` en TopNUC con health OK.
- Stack E2E demolido (`down -v` con sus volúmenes): no queda nada fuera del repo.
