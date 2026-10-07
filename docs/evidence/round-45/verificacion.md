# Ronda 45 — El parte también dice quién duerme en casa

## Lo que faltaba

«Hoy en el hotel» contaba quién llega y quién se va, pero no quién duerme esa noche sin moverse:
la recepcionista no veía la ocupación real del día de un vistazo.

## Lo que se entrega

El parte trae `enCasa`: reservas vigentes que cubren esa noche (llegada ≤ día < salida, el mismo
semiabierto de todo el sistema), ordenadas por habitación. El que sale ese día ya no duerme; el
que llega, sí. Y la pantalla muestra la tercera lista con su conteo.

## Verificación

```text
.\mvnw.cmd test
Tests run: 252, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS

docker compose build api web  (tsc --noEmit + Vite en verde)
node tools/operacion/verificar-despliegue.mjs
despliegue: rutas de proxy, cabeceras y CSP coherentes   exit=0
```

En vivo el 12 de mayo de 2027: Llegadas (1) Ana, Salidas (1) Bruno, En casa (2) Ana + Carmen
(la que sigue sin moverse). Capturas: `parte-en-casa.png` y `parte-en-casa-movil.png`.

## Nota de git

Sin push por regla vigente. Commit selectivo: solo archivos de esta ronda.