# Ronda 40 — El parte del día: quién llega y quién se va, sin SQL

## Lo que pedía el hotel

Consultar la ocupación por días. El calendario del panel muestra estados por noche, pero la
recepción no tenía su lista de la mañana: había que recorrer las reservas una por una o
preguntarle a la base.

## Lo que se entrega

`GET /api/admin/ocupacion/dia?fecha=YYYY-MM-DD` devuelve llegadas y salidas del día con código,
nombre, correo, huéspedes y habitación. Solo cuentan las vigentes: una cancelada no llega ni se
va. Un día sin movimiento trae listas vacías, no un 404 —el silencio también es información—.
Fecha mal formada, 400; sin sesión, 401.

```json
{ "fecha": "2027-05-12",
  "llegadas": [{ "codigo": "H-7A79A8F3", "nombre": "Ana Llegada",
                 "habitacion": "101", "huespedes": 2, "email": "hoy-llega@hotel.local" }],
  "salidas": [{ "codigo": "H-F7FC6C1D", "nombre": "Bruno Salida", "habitacion": "102", ... }] }
```

Y la pantalla «Hoy en el hotel» (`/admin/hoy`, enlace «Hoy» en el pie del panel): fecha del parte
con hoy por defecto y las dos listas con conteos. Días vacíos muestran su vacío con motivo, no
una pantalla en blanco.

## Verificación

```text
.\mvnw.cmd test
Tests run: 246, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS

docker compose build api web  (tsc --noEmit + Vite en verde)
node tools/operacion/verificar-despliegue.mjs
despliegue: rutas de proxy, cabeceras y CSP coherentes   exit=0
```

En el navegador, con dos reservas reales (una que llega y una que sale el 12 de mayo de 2027):
la pantalla muestra «Llegadas (1)» con Ana Llegada · Habitación 101 y «Salidas (1)» con Bruno
Salida · Habitación 102. Capturas: `parte-del-dia.png` (escritorio) y `parte-del-dia-movil.png`
(390 px, las listas envuelven sin desbordar).

## Nota de git

Sin push por regla vigente: `develop` va por delante de `origin/develop` (R34–R40).