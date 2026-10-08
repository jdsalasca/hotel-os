# Ronda 80 — Auditoría cubre PUT/DELETE y el logout queda atribuido (Etapa M)

Fecha: 2026-10-08.

## Dolor

1. El filtro solo miraba POST: `PUT /api/admin/tipos/{id}/amenidades` y `PUT/DELETE
   /api/admin/lugares/{id}` pasaban sin dejar rastro.
2. El logout quedaba como ANONIMO (o ni quedaba): `LogoutFilter` responde sin continuar
   la cadena, así que un filtro después de seguridad jamás lo ve. Probado con prints
   temporales: la petición logout ni invocaba el filtro.
3. De paso: `POST /api/admin/lugares` devolvía `{"id":0}` porque `last_insert_rowid()`
   es por conexión y el pool entrega otra (los demás repositorios buscan por código
   único; lugares era el único con este patrón).

## Cambio

- `AuditoriaAdminFilter`: cubre POST/PUT/DELETE/PATCH bajo `/api/admin/` y va con
  `@Order(HIGHEST_PRECEDENCE)` para ver también el logout.
- Actor desde la sesión (`DeferredSecurityContext`), capturado antes y después: se
  queda el que identifique (login → quien entró, logout → quien salió, fallido →
  ANONIMO). `ActorActual.correo(Authentication)` extraído para no duplicar.
- `LugaresRepository.crear()` con `GeneratedKeyHolder`: id real en la respuesta.

## Verificación real

```text
AuditoriaAdminTest: 8/8 (4 nuevos: PUT auditado, DELETE auditado + 404 auditado,
logout atribuido, login atribuido/fallido ANONIMO; los 4 vistos fallar antes).
En el camino, el test de login exigió el PasswordEncoder delegante ({bcrypt}),
no un BCrypt pelado.
Suite menos EscaladoSqliteTest: 279 corridos, 0 fallos, 0 errores, 1 omitida
(preexistente). BUILD SUCCESS.
```

## Archivos

- Tocados: `AuditoriaAdminFilter.java`, `ActorActual.java`, `LugaresRepository.java`
  (ya en origin), `AuditoriaAdminTest.java`, `docs/plan.md`.
- Nuevos: esta carpeta (sin capturas: sin cambios de UI).

## Riesgos y límites

- Número 80 porque 77–79 los publicó el colega (verificado libre antes de crear).
- Deploy a prod: bloqueado por falta de usuario SSH (probados jdsal/ubuntu/hotel en
  192.168.1.100, denegados; servidor responde red). Pedido al usuario; sin respuesta
  se sigue con backlog local. El tag `prod-2026-10-08` quedó publicado en origin.
- Trabajo del colega respetado: sin solapes.
