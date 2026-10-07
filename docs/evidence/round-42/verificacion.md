# Ronda 42 — Los errores del navegador hablan español

## Lo que pasaba

El cliente HTTP presuponía JSON en todas las respuestas. Tres casos reales lo rompían con mensajes
ilegibles:

1. **Sesión vencida**: el entry point respondía 401 con el JSON por defecto de Boot
   (`"error":"Unauthorized"`). Eso era lo que leía el personal en cada pantalla.
2. **Respuestas no JSON** (un 502 del proxy, un HTML inesperado): `JSON.parse` reventaba con un
   `SyntaxError` críptico en vez de un error manejable.
3. **Sin red**: el `TypeError: Failed to fetch` no le dice nada a nadie.

## Lo que cambia

En `cliente.ts`, que usan todas las pantallas: sin red, «No hay conexión con el hotel…»; cuerpo
no JSON, se ignora el cuerpo en vez de reventar; 401 sin motivo legible, «La sesión venció.
Entra de nuevo». Además `ErrorApi` ya llevaba el cuerpo desde la R38, así que los 409 con datos
siguen funcionando igual.

Y en la raíz: el entry point de Spring ya no usa `sendError` —Boot sustituía el cuerpo por el
suyo en inglés—. Ahora responde 401 con `{"error":"la sesión venció o no hay sesión: entra de
nuevo"}`, que es lo que el navegador muestra tal cual.

## Verificación

```text
.\mvnw.cmd test
Tests run: 247, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS

docker compose build api web  (tsc --noEmit + Vite en verde)
node tools/operacion/verificar-despliegue.mjs
despliegue: rutas de proxy, cabeceras y CSP coherentes   exit=0
```

En el navegador, contra el servidor en marcha:

| Caso | Antes | Ahora |
|---|---|---|
| Sesión muerta (reinicio) + «Ver detalle» | «Unauthorized» | «la sesión venció o no hay sesión: entra de nuevo» |
| Red cortada + «Buscar» | `TypeError: Failed to fetch` | «No hay conexión con el hotel…» |

Captura: `sesion-vencida.png`. Sin framework de tests de componentes en el repo (decisión
pendiente de la R7), la verificación de estos dos caminos es el navegador de verdad, no un mock.

## Nota de git

Sin push por regla vigente: `develop` va por delante de `origin/develop` (R34–R42).