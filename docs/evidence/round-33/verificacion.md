# Ronda 33 — El panel por fin tiene salida

## Lo que faltaba

El botón de cerrar sesión del panel solo existía enterrado en la página de Actividad. En un PC de
recepción, eso significa que el siguiente turno puede entrar como admin sin pedir nada. Los
huéspedes sí tenían «Salir» en Mis reservas; el personal, no.

## La prueba que falló primero

`CookieSesionTest.cerrarSesionInvalidaLaSesion` hace el ciclo completo por HTTP real: entra como
admin, cierra con la misma cookie y vuelve a pedir el panel. Falló con un 302 vacío en vez del 200
con JSON que el controlador prometía:

```text
expected: <200> but was: <302>
```

El motivo: hay dos cierres para la misma ruta y solo uno puede ganar. El filtro de logout de Spring
atiende `/api/admin/logout` antes de que la petición llegue al controlador, así que el método
`logout` del controlador era código muerto y la respuesta era la redirección por defecto a
`/login?logout`. En el navegador, `fetch` seguía esa redirección a otro origen y la CSP la
bloqueaba; `salir()` fallaba y no había navegación a `/admin/entrar`.

La corrección mínima: el cierre lo atiende el filtro —que ya invalida la sesión y borra la
cookie— y ahora responde `200` con JSON en vez de redirigir. Se eliminó el método muerto del
controlador para no mantener dos cierres.

```text
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## El botón

«Cerrar sesión» aparece junto a la navegación del panel, con el mismo estilo que los enlaces, y
solo cuando hay sesión y solo en las rutas del panel —nunca en las públicas ni en `/admin/entrar`.
Tras cerrar, recarga a `/admin/entrar` para no dejar datos del panel en memoria.

Verificado en el navegador contra el servidor en marcha:

| Paso | Resultado |
|---|---|
| Entrar como admin e ir a `/admin/reservas` | Aparece «Cerrar sesión» en el pie del panel |
| Pulsar «Cerrar sesión» | Va a `/admin/entrar`, sin el botón |
| Pedir `/api/admin/reservas?limit=1` con la sesión cerrada | `401` |

Capturas: `panel-cerrar-sesion.png` (escritorio) y `panel-cerrar-sesion-movil.png` (390 px, el pie
envuelve los enlaces y el botón sin desbordar).

## Verificación

```text
.\mvnw.cmd test
Tests run: 226, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS

node tools/operacion/verificar-despliegue.mjs
despliegue: rutas de proxy, cabeceras y CSP coherentes   exit=0
```

Build web con `tsc --noEmit` + Vite en verde dentro de Docker.