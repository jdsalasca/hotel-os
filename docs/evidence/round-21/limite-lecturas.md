# Ronda 21 — Tope de lecturas públicas por IP

## Lo entregado

La ronda 18 puso tope a las escrituras públicas (`POST /api/reservas`), pero las lecturas quedaron
abiertas. Tres endpoints públicos sin límite:

- `GET /api/reservas/{codigo}?email=...` y `/comprobante` — exigen código + correo, así que son
  seguros para quien tiene sus datos. Pero los códigos son `H-` + 8 hex (32 bits) y el correo se
  compara sin mayúsculas: sin tope, el endpoint es un oráculo para recorrer códigos probando
  correos y descubrir qué correos están registrados.
- `GET /api/disponibilidad` — la lectura más pesada y totalmente abierta: un bucle martillea la
  base sin autenticarse.

`LimiteConsultasPublicas`: 30 lecturas por minuto por IP, en memoria como los otros dos
limitadores (instancia única). Un huésped que revisa su reserva, su comprobante o la
disponibilidad repite unas pocas veces, muy por debajo del tope.

## Verificación

```text
.\mvnw.cmd test -Dtest='LimiteConsultaReservasTest'
Tests run: 3, Failures: 0, Errors: 0
```

| Test | Qué fija |
|---|---|
| `adivinarUnaReservaAjenaSeTope` | 30 correos ajenos → 404, el 31 → 429 |
| `elTopeEsPorIp` | otra IP sigue consultando con 200 |
| `laDisponibilidadTambienSeTope` | 30 lecturas → 200, la 31 → 429 |

Por HTTP contra el stack real (el `@` en curl hubo que codificarlo, si no el 400 era del
shell y no del servidor):

```text
correo correcto:  200
correo ajeno:     404
30 lecturas ajenas: 404 ... 404 429 429 429
el 31 (legítimo, misma IP): 429
{"error":"demasiadas consultas seguidas desde esta conexión. Espera un minuto e inténtalo de nuevo."}
disponibilidad 30×: 200 ... 200, luego 429 429
```

## Límites honestos de este arreglo

- El cubo es por IP, no por persona: un atacante agota el cupo de su propia IP, y un huésped
  legítimo detrás de la misma IP (misma wifi, mismo NAT) también recibe 429 durante un minuto.
  Es el precio del tope por IP y la ventana es de un minuto, no de quince.
- `GET /api/hotel` y `/api/health` quedan sin tope a propósito: son baratos y el healthcheck de
  Docker los llama cada 15 segundos.
- Como los otros limitadores, el contador es en memoria y se vacía al reiniciar.
