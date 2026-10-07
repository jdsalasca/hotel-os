# Ronda 26 — Tope de intentos en el arranque del administrador

## Lo entregado

`POST /api/admin/init` es `permitAll` y su única barrera es `ADMIN_INIT_TOKEN`. Quien lo adivine
crea la cuenta de administrador **con la contraseña que elija**: es la ruta más sensible del
sistema y era la única que aceptaba un secreto sin ningún límite de intentos.

```java
// antes: sin tope, y comparación con String.equals
if (!props.adminInitToken().equals(req.token()))
  throw new ResponseStatusException(HttpStatus.FORBIDDEN, "token inválido");
```

Ahora `IntentoThrottle`: 5 intentos por IP cada 15 minutos. El contador se limpia con un acierto,
para que quien sí sabe el token no quede penalizado por los intentos que falló antes.

De paso, `String.equals` corta en el primer carácter distinto, así que el tiempo de respuesta
depende de cuántos caracteres del token aciertan: con un secreto corto eso acorta la búsqueda por
fuerza bruta. `Secretos.iguales` usa `MessageDigest.isEqual`, que compara siempre del tiempo
completo.

## Verificación

```text
.\mvnw.cmd test
Tests run: 199, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS
```

Contra un contenedor con token real, por HTTP:

```text
8 intentos con token equivocado: 403 403 403 403 403 429 429 429
token correcto tras toparse:     429
```

Que el token correcto también reciba 429 desde esa IP es lo correcto: el tope es por IP, no por
secreto. Si el hotel tiene el token bien puesto, entra desde su red y no se topa.

`SecurityIntegrationTest` (que crea el administrador con el token) sigue en verde: el camino bueno
no se rompió.

## Un test que decidí no escribir

La primera versión incluía un test de **tiempo de respuesta** para comprobar que la comparación es
constante. Lo quité: un benchmark de nanosegundos en una suite de CI es una prueba inestable, y una
prueba que falla un día sí y otro no entrena al equipo a ignorar el rojo. La comparación constante se
resuelve leyendo el código (`MessageDigest.isEqual`), no midiendo.

Los dos tests que sí quedan:

| Test | Qué fija |
|---|---|
| `adivinarElTokenSeTope` | 5 intentos → 403, el 6º → 429, y no se crea ningún usuario |
| `elTopeDelInitEsPorIp` | el bloqueo de una IP no impide el arranque desde otra |

## Lo que queda abierto en el mismo_border

`POST /api/admin/login` tiene tope, pero está indexado por `correo|IP`. Eso frena los intentos
contra **un** correo, y no contra un origen: una sola IP puede probar cinco contraseñas de mil
correos distintos sin toparse nunca. Es el camino habitual del credential stuffing, así que el
siguiente paso natural es un tope por IP además del actual. Queda anotado en el plan.

## Lo que no cambia

- El arranque sigue siendo de un solo uso: en cuanto existe un usuario, la ruta deja de crear
  cuentas, aunque el token sea correcto.
- Sin `ADMIN_INIT_TOKEN` en el entorno, la ruta responde 403 antes de mirar nada, y eso se comprueba
  antes que el tope de intentos: un despliegue mal configurado no gasta intentos.
- La contraseña mínima de 12 caracteres y el rol `ADMIN` forzado siguen igual.