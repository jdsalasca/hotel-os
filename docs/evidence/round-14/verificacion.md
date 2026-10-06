# Ronda 14 — Identidad operativa del hotel

Fecha: 2026-10-06. Rama `develop`.

## Entregable

El panel tiene una pantalla de datos del hotel para marca, contacto y horarios operativos. La web
pública muestra el nombre configurado en la cabecera y el contacto en el pie. Si la API falla o el
hotel no ha configurado nada, la web usa el nombre genérico en lugar de romperse o inventar datos.

La pantalla deja claro su alcance: no decide precios, inventario ni reservas.

## Separación de capas

- `HotelConfigController` traduce HTTP y errores.
- `HotelConfigService` valida claves, formatos y rangos, y guarda todo en una transacción.
- `HotelConfigRepository` lee y escribe `hotel_config`.
- El servicio publica solo la lista cerrada de identidad; claves internas como
  `inventario_esperado` nunca salen por `/api/hotel`.

Validaciones:

- Claves desconocidas se rechazan, incluido cualquier intento de guardar un secreto.
- Correo, teléfono, dirección, horas `HH:mm`, política, zona horaria IANA y moneda ISO 4217.
- Guardado atómico: o entran todos los valores válidos o no entra ninguno.

## Verificación real

```text
.\mvnw.cmd test
Tests run: 164, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

```text
npx tsc --noEmit
TSC_SIN_ERRORES
```

```text
docker compose -f compose.yaml -f compose.capturas.yaml up --build --force-recreate \
  --abort-on-container-exit capturas
capturas completas sin errores de consola ni de API
```

Casos del controlador:

- Guardar la identidad responde 200 y la web pública la lee.
- La web pública no expone `inventario_esperado`.
- Clave desconocida responde 400.
- Correo, hora y zona inválidos responden 400.
- Sin sesión responde 401 y con rol no administrador responde 403.

## Interfaz verificada a mano

- `docs/screenshots/escritorio-10-hotel.png`: formulario completo en dos columnas, botón de
  guardado sin estirarse y enlace de retorno.
- `docs/screenshots/movil-10-hotel.png`: el mismo formulario conserva jerarquía y controles en
  390 px.
- La cabecera y el pie usan el nombre configurado; mientras no haya datos, conservan el nombre
  genérico. También se aprovechó para eliminar un estilo inline del pie.