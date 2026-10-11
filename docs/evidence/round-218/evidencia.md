# Ronda 218 — El panel avisa que los huéspedes no pueden contactar al hotel

## El problema, medido en producción

La web pública está en un estado invendible y el hotelero no lo sabe:

```
GET /api/disponibilidad → {"ofertas":[], ...}   (no se puede reservar)
GET /api/hotel          → contacto_email:"" contacto_telefono:""
```

Quien llega queriendo reservar ve "Reserva en línea no disponible" y, como el hotel no tiene
correo ni teléfono configurados, la línea de contacto del código desaparece entera. Se queda
**sin a quién escribir**: ni reservar, ni contactar. Y en el panel no había ni un aviso, así
que el hotelero podía llevar días sin enterarse.

## El cambio

`PaginaAdminPanel.tsx` — la puerta del panel lee `/api/hotel` y, si no hay correo **ni**
teléfono, muestra un `Aviso` con el problema y un enlace directo a "Pon tu contacto".

Decisiones deliberadas:

- **Sólo avisa cuando faltan los dos.** Si hay uno, el aviso no tiene sentido.
- **Si la lectura falla, no avisa.** `sinContacto` se queda en `null`: no se afirma un fallo
  de configuración cuando lo que hubo fue un fallo de red.
- **El aviso lleva enlace.** Un reproche sin salida no arregla nada; el enlace lleva a donde
  se corrige.

Sin SCSS nuevo: reutiliza el componente `Aviso` y sus tokens.

## Pruebas

TDD RED → GREEN. El RED real:

```
Unable to find an element with the text: /los huéspedes no pueden contactarte/i
```

Tres casos, no uno:

1. Sin correo ni teléfono → avisa y ofrece el enlace.
2. Con correo → **no** avisa (para que no aparezca siempre).
3. La lectura del hotel falla → no inventa el aviso.

```
Test Files  31 passed (31)
Tests       206 passed (206)
Type Errors no errors
```

Build:

```
dist/assets/index-B8gbyVJD.js   396.90 kB │ gzip: 114.19 kB
✓ built in 3m 18s
```

## Verificación visual

`panel-aviso-escritorio.png` (1280 px) — el aviso encabeza el panel, por encima de "Mi cuenta"
y de las tarjetas. Se lee antes que cualquier otra cosa, que es el punto.

`panel-aviso-movil.png` — captura del elemento, no de la página: `boundingBox` da
`x:16, width:343` sobre un viewport de 390. Ocupa el ancho correcto con sus márgenes y no
desborda. Se descartó así la sospecha de un fallo de responsive: la franja oscura que
aparecía en la captura de pantalla completa era el área sin pintar de la ventana del
navegador, no del documento (`body 375 = docScroll 375`).

## Consola

Con la API real del stack y sin sesión: dos `401`. **Son el comportamiento correcto**, no un
defecto: el hook de sesión pregunta al montar, el servidor responde 401 y la aplicación
muestra la puerta de "Sesión requerida". La verificación del aviso se hizo con sesión
interceptada, porque el flujo de login real requiere OAuth.

## Nota de coordinación

R217 (`goal/round-217-mapa-error`) pertenece a otro agente; esta ronda usa el 218 y sólo toca
`PaginaAdminPanel.tsx`. No hay solapamiento.