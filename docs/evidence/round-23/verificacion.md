# Ronda 23 — Huéspedes con Google: cuenta, reservas propias y página

Cierra el encargo: el panel entra con Google (ronda 22) y ahora también los huéspedes.

## Lo entregado

**Migración V7** (V6 ya la usó la ronda 20, la idempotencia por huésped):

```sql
CREATE TABLE usuarios (id, google_sub UNIQUE, email, nombre, creado_en);
ALTER TABLE reservations ADD COLUMN usuario_id INTEGER REFERENCES usuarios(id);  -- NULLABLE
```

`usuario_id` es nullable a propósito: quien reserva sin entrar no tiene cuenta, y esa reserva no
puede quedarse huérfana ni romperse. `google_sub` es la clave, no el correo, porque dos personas
pueden compartir un correo: las reservas cuelgan del id, no del texto.

**Alta solo al primer login.** `porSubOCrear` usa un solo `INSERT ... ON CONFLICT DO UPDATE`: entre
"buscar" y "crear" hay una ventana en la que dos peticiones del mismo primer login insertarían dos
filas. No se toca ninguna reserva previa: las anónimas siguen siendo anónimas.

**Al reservar con sesión, la reserva se vincula** (`vincular` solo rellena si `usuario_id IS NULL`).
Antes no pasaba nada y ahora aparece en "Mis reservas" sin guardar el código.

**Endpoints:** `/api/yo` y `/api/mis-reservas`, con `hasAnyRole("HUESPED","ADMIN")`; y
`POST /api/huesped/logout` (POST con CSRF, porque cierra sesión).

**Front:** `PaginaMisReservas`, `useSesionHuesped` (hook aparte del del panel: uno pregunta "soy
admin" y este "soy huésped"), botón en la navegación y ruta. Sin estilos nuevos: reusa `tabla`,
`cifra`, `etiqueta`, `vacio`, `aviso`.

## Verificación

```text
.\mvnw.cmd test
Tests run: 194, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS
```

Los 6 tests de `HuespedGoogleTest`, escritos antes que la implementación:

| Test | Qué fija |
|---|---|
| `primerLoginCreaElUsuario` | alta con sub, email y nombre |
| `elSegundoLoginNoDuplica` | entrar otra vez no crea una segunda fila |
| `laReservaAnonimaSigueSiendoValida` | `usuario_id` queda NULL, no revienta |
| `lasReservasSonDelUsuarioQueIngreso` | dos cuentas con el mismo correo no se mezclan |
| `sinSesionNoDevuelveNada` | 401 en ambos endpoints, no datos |
| `conSesionVeSusReservas` | `/api/yo` y `/api/mis-reservas` por HTTP |

### El riesgo que señalaste, verificado

> tu /api/health hoy exige 4 tablas — confirmo que con 6 siga en ok

**Confirmado, no hace falta tocar el health.** Cuenta `name IN (...)` de cuatro tablas concretas, así
que añadir tablas no lo altera. Sobre la base real del proyecto, que ya tenía reservas y
respaldos:

```text
Successfully validated 7 migrations
Migrating schema "main" to version "7" - usuarios huespedes
Successfully applied 1 migration

GET /api/health  -> {"estado":"ok","base":"accesible","tablas":"4"}
docker healthcheck -> healthy
```

### Un fallo mío que salió en el camino

Reutilicé la misma `MockHttpServletResponse` para dos `sendRedirect` del mismo test:
`Cannot send redirect - response is already committed`. Es del test, no del código, pero conviene
saberlo: una respuesta HTTP no admite dos redirecciones.

### Visual

`mis-reservas-sin-sesion.png` (aviso + botón Google + enlace a consulta sin entrar),
`mis-reservas-con-sesion.png` y `mis-reservas-movil.png`.

La primera versión del móvil **se veía mal**: la tabla colapsaba a tarjetas con los valores sueltos
y sin etiqueta, imposible saber qué era "2" o "$ 3.000". Se corrigió con `data-label` en cada
celda, el patrón que el resto de tablas ya usaba: ahora cada valor lleva su nombre y no hay
desbordamiento horizontal (`scrollWidth <= clientWidth`).

Aviso sobre la evidencia con sesión: el estado autenticado se renderizó **interceptando `/api/yo` y
`/api/mis-reservas` en el navegador** con datos representativos, porque entrar de verdad requiere una
cuenta de Google. El componente es el real; lo que se verificó visualmente es jerarquía, etiquetas,
estados de color y responsive. El comportamiento de verdad está cubierto por los tests de
integración.

## Lo que falta para que funcione en el hotel

```bash
# mismo Client ID del correo y del panel, un solo par de credenciales
GOOGLE_CLIENT_ID=...
GOOGLE_CLIENT_SECRET=...
# URI de redireccionamiento autorizado en Google Cloud:
#   https://TU-DOMINIO/login/oauth2/code/google-huesped
```

Sin esto, el botón "Entrar con Google" de huéspedes lleva a `/oauth2/authorization/google-huesped` y
el backend responde 404 porque el registro no existe: la contraseña del panel y las reservas
anónimas siguen funcionando igual.