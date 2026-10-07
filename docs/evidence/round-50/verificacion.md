# Ronda 50 — El huésped cancela su reserva sin llamar al hotel

## Lo que faltaba

Solo el personal podía cancelar, desde el panel. El huésped que cambiaba de planes tenía que
llamar o escribir: fricción para él y trabajo para recepción.

## Lo que se entrega

`POST /api/reservas/{codigo}/cancelar` con el correo en el cuerpo: la misma compuerta que la
consulta (código + correo), así que nadie cancela reservas ajenas adivinando el código. Viaja por
las transiciones del dominio: de PENDIENTE o CONFIRMADA a CANCELADA; lo cerrado da 409. El actor
queda en la auditoría como `huésped:correo`, distinguible del personal. Con otro correo o código
inexistente, 404 sin distinguir el motivo.

En la consulta, el comprobante ofrece «Cancelar esta reserva» solo si está viva, en dos pasos
(aviso + botón rojo de confirmación) y con estado final visible. La noche liberada se vuelve a
vender: probado reservando las mismas fechas y habitación después.

## Verificación

```text
.\mvnw.cmd test
Tests run: 267, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS

docker compose build api web  (tsc --noEmit + Vite en verde)
node tools/operacion/verificar-despliegue.mjs
despliegue: rutas de proxy, cabeceras y CSP coherentes   exit=0
```

Recorrido en el navegador: reserva `H-C564D9E4` consultada, primer clic arma el aviso «¿Seguro
que la cancelas?», segundo clic la cancela y la pantalla muestra CANCELADA sin el botón.
Capturas: `cancelacion-huesped.png` (escritorio) y `cancelacion-huesped-movil.png` (390 px).

## Nota de git

Sin push por regla vigente. Solo archivos de esta ronda en el commit.