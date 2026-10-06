# Ronda 10 — Planes tarifarios y precios por noche desde el panel

Fecha: 2026-10-06. Rama `develop`.

## El hueco

La API tenía `POST /api/admin/planes` y `POST /api/admin/tarifas` desde la ronda 4, pero **no había
forma de leer lo que el hotel había fijado**: los endpoints de solo lectura no existían. En la
práctica, configurar precios exigía curl contra el contenedor. Y el criterio del proyecto es
justo el contrario: *el hotel registra habitaciones y tipos desde la aplicación, sin SQL a mano*.

## Backend

Dos lecturas, apoyadas en consultas que ya existían en `TarifaRepository`:

```java
public List<PlanTarifario> listarPlanes()                       // planesActivos()
public List<TarifaNoche> nochesDe(long planId, long tipoId, ...) // nochesDelPeriodo(...)
```

`nochesDe` valida el intervalo: `hasta` debe ser posterior a `desde` y el periodo no puede pasar de
un año. Sin eso el panel podía pedir diez años y vaciar la tabla en el navegador.

Pruebas primero, en rojo (`cannot find symbol: method nochesDe`), y luego implementadas:
`InventarioServiceTest` pasa de 13 a 17 pruebas. Cubren el listado, la noche cerrada devuelta
**marcada como cerrada** (no como si no existiera), el periodo invertido y el periodo desmedido.

```
.\mvnw.cmd package  ->  Tests run: 142, Failures: 0, Errors: 0
```

## Frontend

En `PaginaAdminInventario`:

- Formulario de plan tarifario (código, nombre, moneda ISO 4217 validada con `pattern`).
- Rejilla de **precios por noche** del mes elegido: una fila por noche con precio, estado
  (`Fijada`, `No vendible`, `Sin precio`) y casilla para cerrar la noche.
- Solo se envían las noches **cambiadas**: el botón compara el borrador contra lo guardado y avisa
  si no hay nada que guardar, en vez de machacar 31 filas.
- Al cambiar de plan, tipo o mes se relee la base y el borrador se rellena con lo guardado. Nunca se
  inventa un precio que el hotel no ha fijado.

## Dos bugs reales encontrados verificando

1. **`diasDelMes` devolvía una lista vacía.** `rangoMes` devuelve el primer día del mes *siguiente*,
   así que leer `slice(8, 10)` daba siempre `01` y el bucle `for (dia = 1; dia < 1)` no iteraba
   nunca. La rejilla aparecía sin ninguna noche.
2. **`toISOString` convertía a UTC.** En husos al este del UTC el día se desplaza hacia atrás, y en
   un precio eso significa que falta la última noche del mes: la oferta no se totaliza. Ahora las
   fechas se arman con la hora local (`isoLocal`), que además evita el mismo problema en las
   consultas de disponibilidad que ya usaban `rangoMes`.

## Verificación por HTTP contra los contenedores reales

```
planes:       [{"id":1,"codigo":"PES",...},{"id":2,"codigo":"R10",...}]
precio:       {"fecha":"2026-12-01","ratePlanId":2,"ok":true,"roomTypeId":2}
cerrar noche: {"fecha":"2026-12-02",...,"ok":true}
lectura:      [{"fecha":"2026-12-01","precioCents":180000,...,"cerrado":false},
               {"fecha":"2026-12-02","precioCents":180000,...,"cerrado":true}]
periodo invertido:  {"error":"'hasta' debe ser posterior a 'desde'"}
periodo de 10 años: {"error":"el periodo no puede superar un año"}
```

Y el efecto en la web pública, que es lo que importa:

```
/api/disponibilidad?llegada=2026-12-01&salida=2026-12-03
   habitacion 901  tipo=Doble prueba  total=360000 COP  noches=2

/api/disponibilidad?llegada=2026-12-01&salida=2026-12-05   -> 0 ofertas
```

La segunda es la correcta: la noche del 4 no tiene precio, así que no se ofrece un total
incompleto. El hotel puede comprobar en la web pública lo mismo que ve en el panel.

## Capturas

Dos nuevas, `*-07b-precios.png` en móvil y escritorio, generadas por el guion de verificación (que
falla ante cualquier error de consola o respuesta de API ≥ 400) y revisadas a mano. En móvil la
tabla se convierte en tarjetas como el resto de tablas del panel.

De paso, los botones que se estiraban al ancho del contenedor llevan `no-estirar` cuando el
formulario tiene una sola acción.

## Pendiente

- Calendario de **ocupación** día a día (hoy hay una comprobación por habitación del mes completo).
- Mapeos por canal, conciliación y recepción real de reservas de las OTAs.
- Facturación/recibos, configuración del hotel y auditoría de acciones administrativas.