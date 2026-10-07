# Ronda 51 — Cancelar también desde Mis reservas

## Lo que faltaba

La R50 dejó cancelar al anónimo en la consulta, pero el huésped con sesión tenía que irse a la
consulta con código y correo teniendo sus reservas delante. Incoherente.

## Lo que se entrega

Columna «Acciones» en Mis reservas: botón «Cancelar» por fila viva, en dos pasos («Sí,
cancelar» en rojo), con el correo de la sesión. Al confirmar, la fila pasa a CANCELADA sin
recargar y lo cerrado muestra «Sin acciones». El endpoint es el mismo de la R50, ya probado;
aquí no hay backend nuevo.

## Verificación

```text
.\mvnw.cmd test
Tests run: 267, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS

docker compose build web  (tsc --noEmit + Vite en verde)
node tools/operacion/verificar-despliegue.mjs
despliegue: rutas de proxy, cabeceras y CSP coherentes   exit=0
```

En el navegador (sesión simulada + reserva real `H-678E2D42`): primer clic arma el «Sí,
cancelar», segundo clic cancela; la fila queda en CANCELADA y el servidor confirma CANCELADA.
Capturas: `cancelar-mis-reservas.png` y `cancelar-mis-reservas-movil.png`. El «Sin precio» del
total es artefacto de la sesión simulada (el mock no trae totales), no del código.

## Nota de git

Sin push por regla vigente. Solo archivos de esta ronda en el commit.