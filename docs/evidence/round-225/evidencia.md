# Ronda 225 — Lo que le falta a la web, en una lista en vez de tres tarjetas

## Los tres bloqueos, medidos en producción

```
GET /api/lugares     → {"hotel":{"ubicado":false}}   el mapa y las distancias no se ven
GET /api/admin/tipos → []                             no hay nada que vender
GET /api/hotel       → contacto_email:"" telefono:""   el cliente no tiene a quién escribir
```

Las tres funciones están construidas (mapa y distancias de R177-R180, inventario guiado,
contacto). Lo que faltaba es que el hotelero **se enterara** de que su web está ciega. Las
rondas 218 y 224 pusieron avisos de contacto y de venta; esta añade el del mapa y, sobre
todo, consolida los tres.

## El problema que apareció al verlo

Con tres tarjetas apiladas, medido a 1280x800: los avisos terminaban en **~750px** y
"Mi cuenta" empezaba ahí, **fuera de la pantalla**. El hotelero veía solo avisos y tenía que
hacer scroll para llegar a lo que había venido a hacer. Tres tarjetas es más código y peor
jerarquía.

## El cambio

`PaginaAdminPanel.tsx` — una sola lista de pendientes derivada de las tres banderas, y un
único `Aviso` que la pinta:

- **Orden por gravedad**: mapa, venta, contacto. Sin punto en el mapa no hay mapa; sin
  habitaciones no hay nada que vender; sin contacto el cliente se queda sin a quién escribir.
- **Singular/plural**: "una cosa pendiente" / "3 cosas pendientes". Un contador mal conjugado
  delata descuido.
- **Cada línea lleva su enlace**, para que la lista sea accionable y no un reproche.
- **Sin SCSS nuevo**: la `ul` usa `pila gap-e1`, que ya existe.

Las banderas conservan la semántica de las rondas anteriores: si una lectura falla, esa
bandera se queda en `null` y esa tarea **no entra** en la lista. Un fallo de red no es una
tarea pendiente.

## Pruebas

TDD RED → GREEN. RED de la lista:

```
Unable to find an element with the text: /tu web tiene 3 cosas pendientes/i
```

Cinco casos: los tres juntos con sus tres enlaces, el singular, el estado completo sin aviso,
y una lectura fallida que no cuenta como pendiente. El último es el que protege la regla de
no inventar problemas.

```
Test Files  32 passed (32)
Tests       217 passed (217)
Type Errors no errors
```

## Verificación visual

`panel-consolidado.png` — un aviso con tres viñetas, cada una con su enlace. Medido a
1280x800: el aviso termina en **437px** y "Mi cuenta" empieza en **437px**, ambos dentro del
viewport. La diferencia con el diseño anterior es la que motivó el cambio: antes la cuenta
quedaba fuera de pantalla.

Build:

```
dist/assets/index-*.js   ~400 kB │ gzip: ~115 kB
✓ built
```

## Nota

Los tres avisos se alimentan de tres lecturas independientes. Si más adelante se quiere una
pantalla de "puesta en marcha" con pasos, esta lista es el material; por ahora una lista
honesta dentro de un aviso es menos código y cumple.