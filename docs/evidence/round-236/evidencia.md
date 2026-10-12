# Ronda 236 — El chat responde solo

## El problema

El hilo de conversación se releía solo al abrir y con el botón "Actualizar". El huésped
escribía, el hotel contestaba un minuto después, y el huésped **no veía la respuesta** hasta
que pulsaba Actualizar.

Un chat que hay que refrescar a mano se siente roto. Y en la vista del huésped el botón queda
pequeño y arriba del todo, lejos de los mensajes nuevos.

## Lo que ya existía

El panel ya sondea sus datos secundarios con `useActualizacionPeriodicaVisible`
(`apps/web/src/api/useActualizacionPeriodicaVisible.ts`): cada 30 s, **solo con la pestaña a
la vista**, y relee al volver a mostrarla.

El hilo no lo usaba. En vez de escribir otro temporizador, se reutiliza: un solo sitio donde
vive el sondeo, y quien mantenga uno mantiene el otro.

## El cambio

`apps/web/src/componentes/HiloMensajes.tsx`:

1. **Sondea con el hook existente**, así que la respuesta del hotel aparece sin que nadie
   pulse. Con la pestaña oculta no se gasta nada.
2. **Aviso "N mensajes(s) nuevo(s)"** cuando la respuesta entra mientras el usuario está
   leyendo arriba. Al pulsarlo baja al final y el aviso se limpia.
3. El contador **acumula hasta que el usuario baje**, no se recalcula cada sondeo.

## Un bug que salió durante la ronda

La primera versión del contador lo calculaba comparando con los mensajes ya en pantalla.
Funcionaba en el test y **fallaba en cuanto el sondeo corría dos veces**: al segundo ciclo la
respuesta ya estaba en la lista, así que la diferencia daba 0 y el aviso **desaparecía**
aunque nadie lo hubiera leído. Peor que no tener el aviso: invita a no confiar en él.

Se arregló con `ultimoVistoRef`: la marca de "hasta aquí he leído" solo se mueve cuando el
usuario está al final o pulsa el aviso. Hay un test específico
(`el aviso no se borra solo mientras nadie baje a mirar`) que se comprobó en RED
revirtiendo la corrección.

## Verificación

### TDD

4 tests nuevos. RED inicial:

```
FAIL src/componentes/HiloMensajes.test.tsx
  > HiloMensajes > la respuesta del otro lado llega sola, sin tener que pulsar Actualizar
Tests  4 failed | 9 passed (13)
```

El test del contador se verificó dos veces: pasa con la corrección y **falla sin ella**.

### Suite

```
Test Files  32 passed (32)
     Tests  231 passed (231)
Type Errors  no errors
```

### Navegador real (Chromium, stack `hotelr236`, 40 mensajes sintéticos)

El usuario abre la conversación y sube a releer algo antiguo:

| Paso | Resultado |
| --- | --- |
| Posición al abrir el hilo | `y: 0` de 3367 px (leyendo arriba) |
| Peticiones al hilo | 2 — la segunda la hizo el sondeo, no el test |
| Mensajes en pantalla | 3 → 41 |
| Aviso | **"1 mensaje nuevo"** |
| Tras pulsar el aviso | aviso limpio, baja a `y: 3152` de 3440 |

Capturas:

- `hilo-huesped.png` — el hilo abierto, leyendo arriba.
- `hilo-aviso-nuevos.png` — la respuesta llegó sola y el aviso aparece.

Nota honesta: las primeras pruebas de navegador usaban un hilo corto, donde el mensaje entra
a la vista y el aviso **no** debe aparecer (el usuario ya está al final). La medición con 40
mensajes es la que demuestra el aviso. El indicador solo existe para el caso de estar lejos
del final.

## Lo que sigue

El chat ahora se sostiene solo. Queda pendiente del hotelero lo de siempre: punto GPS,
habitaciones y contacto en producción.
