# Ronda 95 - Contratos sin sesión, todos en orden

Fecha: 2026-10-08.

## Dolor
Con dos agentes escribiendo rutas, un endpoint nuevo podía quedar sin regla y filtrar un
500 o un HTML donde el front espera JSON.

## Cambio
Ninguno de código: barrido de contratos contra prod.
- Públicas 200: health, amenidades, lugares.
- Con sesión ausente, 401 JSON en español (`la sesión venció o no hay sesión`) en yo,
  mis-reservas (+hilo), admin/sesion y admin/reservas: ni un 500 ni HTML.
- OAuth admin redirige 302 a Google (cliente vigente).
- Búsqueda mala: 400. Prod sincronizado a origin (c8f499e).

## Nota de coordinación
Esta ronda iba a ser la 94, pero ese número era del colega (su Etapa D): restauré su
archivo byte por byte desde el diff y la mía pasa a 95 sin tocar nada suyo.
