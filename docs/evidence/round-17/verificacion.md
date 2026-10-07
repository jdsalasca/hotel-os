# Ronda 17 — Auditoría real de acciones administrativas

Fecha: 2026-10-07. Rama `develop`.

## El defecto que motivated la ronda

El único rastro que existía, `reservation_history`, tomaba el actor **del cuerpo de la petición**:

```jsonc
// AdminReservasController#cambiar, antes
public record CambioEstadoReq(String estado, String actor) {}
svc.cambiarEstado(codigo, nuevo, req.actor());
```

Y el frontend lo llenaba con un literal:

```ts
// apps/web/src/paginas/PaginaAdminReservas.tsx, antes
await api.post(`/api/admin/reservas/${codigo}/estado`, { estado, actor: 'panel' });
```

Consecuencias, ambas reales:

1. **El rastro era falso por construcción.** Cualquiera con sesión podía atribuir un cambio a otro
   usuario escribiendo `actor: "director@hotel.es"`. El servidor no contrastaba nada.
2. **El panel no decía quién confirmaba.** Guardaba la palabra "panel", no el correo de quien
   estaba dentro.

Además, 16 de los 17 endpoints de escritura del panel no dejaban **ningún** rastro: fijar precios,
retirar una habitación, borrar un mapeo de canal o crear el primer administrador.

## Lo entregado

### Un solo punto de auditoría, no dieciséis

`AuditoriaAdminFilter` es un filtro de servlet que registra todo `POST /api/admin/**` con actor,
método, ruta y estado HTTP. La alternativa —una llamada a la auditoría en cada controlador— es la
que produce rastros falsos: su cobertura depende de que alguien se acuerde de añadirla.

El filtro corre después de la cadena de seguridad, así que el `SecurityContext` ya tiene al
usuario, y en un `finally`, así que también queda lo que falló. Si escribir el rastro falla, se
registra un `WARN` y la petición sigue: el hotel no puede dejar de confirmar una reserva porque
falló el log.

### El actor sale de la sesión

`ActorActual.correo()` lee el `SecurityContext` (`ANONIMO` cuando no hay persona) y
`AdminReservasController` lo usa. El campo `actor` desapareció del contrato: el cliente ya no
puede declararlo, ni mintiendo.

### El rastro no guarda el cuerpo

No hay columna para el cuerpo de la petición, a propósito: `POST /api/admin/login` lleva
contraseña y `POST /api/admin/init` lleva el token de arranque. Un test concatena toda la fila y
verifica que ninguna contiene la contraseña del intento fallido.

### Pantalla "Actividad" en el panel

`/admin/auditoria`, enlazada en el pie. Traduce la ruta a lenguaje llano ("tocó los mapeos de
canales", "cambió el estado de una reserva · H-8597DC83"), marca en verde/rojo el resultado HTTP
y tiene estado vacío con explicación. Reutiliza las clases SCSS existentes: cero estilos nuevos.

## Verificación real

```text
.\mvnw.cmd test
Tests run: 176, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Los 4 tests de `AuditoriaAdminTest` se escribieron antes que la implementación y fallaron con
`no such table: admin_actions` y `404`. Los cuatro:

| Test | Qué fija |
|---|---|
| `todaAccionQuedaRegistrada` | actor = correo de la sesión, método, ruta y estado 201 |
| `elActorNoEsFalsificable` | con `actor: "mentira"` en el cuerpo, el historial guarda el correo real |
| `elRastroSeConsulta` | `GET /api/admin/auditoria` devuelve las filas |
| `elRastroNoGuardaSecretos` | la contraseña de un login fallido no aparece en el rastro |

Capturas contra los contenedores reales: `escritorio-11-auditoria.png` y `movil-11-auditoria.png`
(34 capturas en total, sin errores de consola ni de API). En móvil la tabla colapsa a tarjetas sin
desbordamiento horizontal, verificado también por `diagnostico.mjs`.

## Lo que esto NO arregla

- `GET /api/admin/indicadores` y `.csv` **escriben** en `indicator_results`, pero son informes, no
  acciones de una persona: quedan fuera del rastro a propósito.
- La reserva creada desde la web pública sigue usando el canal como actor (`WEB`, `BOOKING`). No
  hay persona autenticada ahí, y el canal es la atribución honesta.
- `admin_actions` no tiene clave foránea a `users`: si se borra un usuario, el rastro histórico se
  conserva, que es lo que un registro de auditoría debe hacer.