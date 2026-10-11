# Ronda 224 — El panel avisa que la web no puede vender

## El problema

La ronda 218 dejó un aviso cuando faltaba el contacto. Pero había un segundo callejón sin
salida, más graves: **sin tipos de habitación la web no vende nada**. Medido en producción:

```
GET /api/disponibilidad → {"ofertas":[], ...}
GET /api/admin/tipos    → []            (no hay nada que ofrecer)
```

La portada muestra el estado vacío, el hotelero no tiene ningún aviso en el panel, y la
tarjeta "Habitaciones" parece una puerta más en vez de una asignatura pendiente.

## El cambio

`PaginaAdminPanel.tsx` — la puerta del panel lee `/api/admin/tipos` y, si no hay ninguno,
avisa con enlace a "Crear habitaciones".

Es el mismo patrón que el aviso de contacto de R218, deliberadamente: los dos se leen igual
y se arreglan desde el mismo sitio. Sin SCSS nuevo, reutiliza `Aviso`.

Decisiones:

- **Cuenta tipos, no habitaciones ni tarifas.** Sin tipo no puede haber nada detrás; es el
  primer eslabón y el diagnóstico honesto. Añadir "te faltan tarifas" sería adivinar un
  estado que el hotelero puede estar corrigiendo ahora mismo.
- **Si la lectura falla, no avisa.** `sinVenta` se queda en `null`: un fallo de red no es
  "no tienes habitaciones".
- **Evalúa `Array.isArray` antes de `.length`.** La respuesta se trata como `unknown`: si
  algún día devuelve otra forma, el aviso no revienta ni afirma nada.

## Pruebas

TDD RED → GREEN:

```
Unable to find an element with the text: /tu web no puede vender/i
```

Cinco casos: avisa sin tipos, no avisa con tipos, los tres de contacto de R218 siguen
cubiertos.

```
Test Files  31 passed (31)
Tests       214 passed (214)
Type Errors no errors
```

Build:

```
dist/assets/index-DyHpyRQ1.js   399.41 kB │ gzip: 114.78 kB
✓ built in 6m
```

## Verificación visual

`panel-escritorio.png` — los dos avisos conviven en el orden correcto: primero "Tu web no
puede vender todavía" (el bloqueo duro) y después "Los huéspedes no pueden contactarte". El
de venta va primero porque sin él el de contacto es secundario.

`aviso-movil.png` — `boundingBox` `x:16, width:343` sobre viewport de 390: ancho completo
con márgenes, sin desborde. Mismo criterio de medida que R218, que es como se descartó allí
un falso fallo de responsive.

## Nota

Los avisos se alimentan de dos lecturas distintas a la misma pantalla. Si más adelante se
quiere una sola "lista de pendientes", estas dos tarjetas son el material para juntarla;
por ahora dos avisos claros son menos código que un checklist con estado.