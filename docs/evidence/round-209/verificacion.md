# Ronda 209 - El huesped recibe un aviso de su reserva

## El hueco
Una reserva directa no tiene plataforma intermediaria: nadie le manda un correo al huesped.
`CorreoService.enviar()` existia y funcionaba, pero el unico que lo llamaba era el endpoint de
prueba del panel. Al reservar no salia absolutamente nada: el huesped se quedaba con un codigo en
una pantalla y sin ninguna constancia de que su reserva existia.

Para un huesped que reserve un hotel pequeno sin intermediarios, ese correo es la unica
prueba de que su reserva esta. es la pieza que mas hace falta.

## Las opciones
- **A. Avisar al huesped al confirmar la reserva.** El estandar del dominio; cabe en una ronda.
- B. Ademas recordatorios y avisos de cambios. Necesita plantillas y agenda; no cabe.
- C. No hacerlo porque Gmail esta bloqueado sin `GOOGLE_REFRESH_TOKEN`.

Se tomo **A**, con una condicion no negociable: **si el correo no sale, la reserva se guarda
igual**. El bloqueo de Gmail no puede impedir que alguien reserve.

## TDD rojo-verde
`CorreoConfirmacionTest` (3): el aviso lleva codigo, fechas y como consultar sin depender de
la cuenta; y **sin hotel configurado no dice "null"**.

`ReservaServiceTest > aviso al huesped` (3), con un doble que anota el envio:
- `reservarDisparaElAvisoAlCorreoDelHuesped`: rojo `expected: <1> but was: <0>` — no se
  avisaba a nadie.
- `unCorreoQueSaleMalNoTumbaLaReserva`: la reserva sobrevive a un Gmail que no responde.
- `unReintentoNoVuelveAEnviarElAviso`: rojo con 2 envios. **Este fallo mio: **: la
  idempotencia devuelve la reserva ya creada, pero como el aviso salia despues de la
  transaccion sin distinguir el caso, un reintento por fallo de red mandaba dos correos
  identicos. Se resolvio con un `boolean[] yaExistia` que la lambda de la transaccion deja
  puesto en la rama de idempotencia.

## Verificacion real (contenedores)
Reserva creada por HTTP con el stack de verdad y **sin Gmail configurado**:

```
RESERVA CREADA: H-D0162E05
```

Log del backend, con el motivo exacto del fallo del aviso:
```
reserva H-D0162E05 creada pero el aviso al huésped no salió:
  envío de correo deshabilitado (hotel.correo.habilitado=false)
```

Y consulta directa a la base, que es la prueba de que la venta no se perdio:
```
H-D0162E05 | ana.demo@ejemplo.com | 2031-06-10 -> 2031-06-12
total: 1
```

Es decir: el aviso fallo y **la reserva seguia ahi**. Ese era el riesgo, y queda medido.

## Pruebas
- Backend: **473** pruebas, 0 fallos, 0 errores, 1 omitida.
- Frontend sin cambios en esta ronda.

## Nota
`compose.ronda209.yaml` es andamiaje local de puertos y no va commiteado.
