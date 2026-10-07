# Ronda 27 — Tope de intentos por IP además de por cuenta

## El hueco

`LoginThrottle` estaba indexado por `correo|IP`. Eso frena el ataque contra **una** cuenta: cinco
contraseñas y a parar. Pero no frena el credential stuffing, que es el patrón habitual contra un
panel de administración: probar cinco contraseñas de **mil correos distintos** desde la misma
máquina. Como cada intento usa una clave diferente (`correo|IP`), ninguno acumula nada.

Reproducido antes del arreglo, con siete correos distintos desde una misma IP:

```text
7 correos distintos desde 10.9.0.1 -> todos 401, nunca 429
```

Es decir: sin topes y sin límite de volumen, un atacante puede recorrer la lista de correos del
hotel contra el panel.

## El arreglo

Dos contadores, porque protegen cosas distintas:

- `throttlePorCuenta` (`correo|IP`): el de antes. Frena el ataque a una cuenta concreta, incluso
  desde muchos orígenes.
- `throttlePorIp` (IP sola): frena el stuffing, que cambia de correo en cada intento y por eso no
  toca el primero.

Los dos se limpian con un acierto, así que quien entra bien no queda penalizado. Se quedan los dos:
el primero protege la cuenta y el segundo protege el origen, y cada uno cubre el hueco del otro.

`LoginThrottle` dejó de ser un `@Component` para poder registrar dos instancias en `AppConfig`, con
`@Qualifier` para que cada una llegue al sitio correcto.

## Verificación

```text
.\mvnw.cmd test
Tests run: 202, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS
```

| Test | Qué fija |
|---|---|
| `stuffingDeCorreosSeCorta` | cinco correos distintos → 401, el siguiente → 429 |
| `elTopeEsPorIp` | otra conexión no se topa por lo que haga esta |
| `elTopeNoEnumeraCuentas` | la respuesta es idéntica exista o no la cuenta |

El tercero es el que evita que el tope se convierta en un oráculo de usuarios: si la respuesta
distinguiera "correo no existe" de "contraseña incorrecta", el propio tope serviría para enumerar
quién tiene cuenta en el hotel. El mensaje sigue siendo el mismo en los dos casos.

Contra los contenedores reales:

```text
7 correos distintos desde la misma IP  -> la conexión queda topada
admin login correcto, misma IP, topada -> 429
admin login tras reiniciar (cubo limpio) -> 200
GET /api/admin/reservas con esa sesión   -> 200
```

Que el acierto se topado dé 429 y no 200 es lo correcto: un atacante que ya falló cinco veces desde
esa conexión espera 15 minutos, y el coste de esperar es el mismo para quien olvidó su contraseña.

## Un efecto secundario que hay que saber

El tope es **por IP**, así que cinco fallos desde una red compartida (hotel con wifi de clientes,
oficina con varios formando parte) dejan al resto de esa red sin poder entrar durante 15 minutos. Es el
precio de frenar el stuffing. Con el login con Google de la ronda 22, el afectado tiene salida: no
depende de la contraseña.

## Lo que no cambia

- El tope por cuenta sigue igual: cinco intentos contra un mismo correo.
- El login bueno no se rompió: 200 con la contraseña correcta.
- El arranque del administrador y sus topes (ronda 26) no se tocan.