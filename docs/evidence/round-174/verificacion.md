# Ronda 174 - El huésped cierra sesión desde la cabecera

## El hueco
`CierreSesionPanel` (`apps/web/src/App.tsx`) solo se activaba con la sesión del
**panel**. Un huésped que entró con Google veía su saludo "Bienvenido de vuelta,
Ana · Huésped" en la cabecera pero **no tenía forma de cerrar sesión ahí**: tenía
que bajar hasta `/mis-reservas` a buscar el botón "Salir". Login sin salida
visible, que es justo la queja de "la vista de visitantes logueados es horrible".

## Cambio
- `CierreSesionPanel` cierra por la puerta que esté abierta: el personal por
  `/api/admin/logout` (y vuelve a `/admin/entrar`), el huésped por
  `/api/huesped/logout` —endpoint que ya existía, no se creó nada— y a la
  portada, donde ya no hay saludo que mostrar.
- Cero estilos nuevos: reusa `.cabecera__salir` y `.cabecera__error`.
- Backend sin cambios.

## TDD rojo-verde
- Test nuevo en `App.test.tsx`: *"el huésped también puede cerrar sesión desde la
  cabecera"*. Sin el cambio **FALLA** con
  `Unable to find role="button" and name "Cerrar sesión"`; con él, el clic dispara
  `POST /api/huesped/logout` y `location.assign('/')`.
- `App.test.tsx` 7/7. Suite frontend completa: **24 archivos / 113 tests**,
  `Type Errors: no errors`.

## Verificación visual (navegador real, stack throwaway)
- `cabecera-huesped.png`: con sesión de huésped, la cabecera muestra el saludo
  "Bienvenido de vuelta, Ana · Huésped" y el botón "Cerrar sesión" en la misma
  fila, con la jerarquía y los estilos ya existentes.
- **Salvedad honesta:** la sesión se simuló en la capa de red de Playwright
  (`/api/yo` → 200, `/api/admin/sesion` → 401) porque entrar como huésped real
  exige completar OAuth2 con Google, credencial externa no disponible en este
  entorno. El `POST /api/huesped/logout` sí se verificó de punta a punta en el
  navegador y en el test; lo que no se probó con una cuenta real de Google es el
  establecimiento de la sesión.

## Nota de numeración
La ronda se numeró 174 porque 172 la ocupaba el agente de accesibilidad
(`54fd2e4`) y 173 la del cierre `NO_PRESENTADA`.

## Despliegue
- Pendiente de autorización: commit en `develop`, push y `compose up -d --build`
  en TopNUC con health OK.