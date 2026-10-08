# Ronda 100 - Aviso de mensajes sin leer en la nav

Fecha: 2026-10-08.

## Diseño (brainstorming, vía acotada)
El chat (R64) avisaba solo dentro de Mis reservas y del panel: si el hotel respondía,
el huésped no se enteraba hasta entrar a mirar. Opciones: (a) badge en la nav con el
conteo existente, sin backend nuevo; (b) push/notificaciones (infra nueva, no). Va (a):
`BadgeMensajes` usa `GET /api/mis-reservas/mensajes/nuevos`, solo con sesión y solo si
hay >0. Sin backend, sin polling (se actualiza al navegar), estilo `insignia` con tokens.

## Verificación real (TDD)
- Test primero: falló sin el componente; con él, **23/23 vitest** y tsc con tests limpio.
- Despliegue: `git reset --hard origin/develop` + `up -d --build`; sanos; `insignia` en
  el bundle. El badge con sesión se ve con cuenta real.
- Archivos del otro agente intactos.

## Nota de coordinación
Esta ronda iba a ser la 99 pero ese número era del colega (su Etapa L): lo suyo quedó
intacto en `round-99/` y lo mío vive aquí.

## Archivos
- Nuevos: `BadgeMensajes.tsx`, `BadgeMensajes.test.tsx`, este archivo.
- Tocados: `App.tsx`, `_chat.scss`.
