# Ronda 13 — Mapeos declarados por canal

Fecha: 2026-10-06. Rama `develop`.

## Entregable

El panel puede declarar, ver y retirar el enlace entre inventario local e identificador externo de
Booking, Despegar o Airbnb. Registrar un mapeo no publica nada y no necesita credenciales: deja
constancia del enlace para que una sincronización futura no tenga que adivinarlo.

Reglas aplicadas en el servicio:

- Solo `BOOKING`, `DESPEGAR` y `AIRBNB` admiten mapeos.
- Exactamente una habitación o un tipo por mapeo; el plan tarifario es opcional.
- El recurso local debe existir.
- El identificador externo es obligatorio, sin espacios sobrantes y con un máximo operativo.
- Un identificador externo no puede apuntar a dos recursos del mismo canal, ni siquiera en carrera:
  hay comprobación previa y restricción única en la base.

La eliminación pide confirmación en pantalla y no toca habitaciones, tipos, planes ni reservas.
Repetir la eliminación devuelve 404 en lugar de un éxito falso.

## Separación de capas

- `IntegracionesController` convierte canal y cuerpo HTTP, y traduce errores a 201, 200, 400 o 404.
- `IntegracionesService` valida canal, recurso, plan, duplicados y forma del identificador.
- `OtaSyncRepository` ejecuta el SQL y devuelve mapeos enriquecidos con nombres locales.
- El panel no muestra solo IDs: muestra habitación, tipo, plan opcional e identificador externo.

## Verificación real

```text
.\mvnw.cmd test
Tests run: 159, Failures: 0, Errors: 0, Skipped: 0
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

- Crear un mapeo válido responde 201 y aparece en el panel con su nombre local.
- Sin recurso local responde 400.
- Con habitación inexistente responde 400.
- Duplicar un identificador externo en el mismo canal responde 400.
- Eliminar un mapeo responde 200 y desaparece del panel.
- Eliminar un mapeo inexistente responde 404.

## Interfaz verificada a mano

- `docs/screenshots/escritorio-08b-mapeo.png`: tarjeta del canal con el enlace creado, formulario de
  nuevo mapeo y aviso de que el registro no publica.
- `docs/screenshots/movil-08b-mapeo.png`: la misma tarjeta conserva jerarquía y controles
  utilizables en 390 px.
- El guion crea un identificador único, captura la tarjeta con el enlace visible y después lo
  elimina, así la base de desarrollo no acumula datos de prueba.

## Límite que sigue vigente

Esto no es conciliación ni importación real. Sin credenciales y aprobación de socio, los canales
siguen en `NO_CONFIGURADO` y ninguna reserva entra desde un proveedor. El indicador
`f1_mapeos_por_canal` tampoco se calcula porque la definición exige un total planificado que el
hotel aún no declara; inventar el denominador sería falsear el informe.