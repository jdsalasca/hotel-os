# Ronda 229 — El estado vacío de búsqueda ofrece una salida real

## El hueco medido

Fechas agotadas es el caso **normal** de un hotel pequeño en temporada, no un error. La portada
ya lo trataba como tal: «No hay habitaciones disponibles para esas fechas / Prueba otras
fechas».

Pero ahí se acababa la ayuda. Quien quiere justamente esas fechas no puede resolverlo cambiando
de día, y la web ya tenía el correo y el teléfono del hotel cargados en memoria —los lee para
el pie— sin ofrecerlos aquí. Medido en el stack real: con el hotel a la venta y la búsqueda
devolviendo cero, el aviso no contenía ningún enlace de contacto. El visitante tenía que bajar
hasta el pie y esperar que el hotel hubiera configurado algo.

La ruta `venta === false` (R170) sí ofrecía contacto; la del estado vacío, no. La diferencia
es que R170 la escribió cuando la web no podía vender en absoluto, y el caso «hay venta pero
esas fechas están llenas» nunca se contempló.

## El cambio

El estado vacío de resultados ofrece el contacto que la web ya conoce, y solo ese: sin
contacto configurado no se inventa ninguno ni se promete una salida inexistente. Sin cambios
en la API ni en la base de datos — `contacto` ya venía de `/api/hotel`.

## Verificación

- **Prueba primero:** 4 pruebas nuevas en `PaginaInicio.test.tsx` escribidas y **observadas
  fallar** antes del cambio (3 de ellas por ausencia del bloque; la cuarta ya pasaba porque no
  había nada que inventar, que es justo lo que debe ocurrir sin contacto).
- **Suite frontend:** 227/227, 32 archivos, 0 fallos (sube de 223 con las 4 nuevas).
- `typecheck`, `test:typecheck` y build Vite: aprobados.

## Verificación visual (stack real `r229`, no mocks)

Montado con Compose sobre los puertos 30181/30191, hotel sembrado con tipo, plan, habitación
y 40 noches de tarifa, y contacto público configurado.

- Búsqueda del 10 al 12 de marzo de 2027 (fuera del rango tarifado) → cero resultados.
- El aviso ofrece `mailto:hola@hotelexample.co` y `tel:+573001234567`, verificados en el DOM.
- Escritorio 1440×1000 y móvil 390×844: sin desbordamiento horizontal
  (`scrollWidth === clientWidth`), bloque de 338 px de ancho en móvil.
- Consola: los dos 401 son de `/api/yo` y `/api/admin/sesion`, las consultas de sesión de la
  portada pública sin autenticar. Preexistentes, ajenas a este cambio.

Capturas: `vacio-con-contacto-escritorio.png`, `vacio-con-contacto-movil.png`.

## Lo que NO se hizo

- No se añadió un formulario «avísame cuando haya». Requiere tabla, endpoint y panel: es
  arquitectura, no una ronda corta, y es especulativo mientras el hotelero no tenga contacto.
- No se cambió el texto del `venta === false`, que ya funciona. Reutilizar el mismo patrón de
  contacto era suficiente; duplicar la redacción se evita extrayendo un componente solo si
  aparece un tercer uso.
