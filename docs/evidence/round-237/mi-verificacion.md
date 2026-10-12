# Ronda 237 — Una sola lista de preparación (mi parte)

## Problema

El panel mostraba **dos listas de pendientes que se solapaban y se contradecían**:

- "Tu web tiene **3** cosas pendientes" — calculada en el navegador con 3 peticiones
  (`/api/hotel`, `/api/admin/tipos`, `/api/lugares`).
- "Pon tu hotel a punto (**5** pendientes)" — desde `/api/admin/preparacion`, calculada
  por el servidor de su propia base.

GPS y habitaciones salían en las dos, y los dos contadores no coincidían. El hotelero no
sabía cuál creer ni por dónde empezar. Además el contacto solo existía en la primera, así
que se podía completar la checklist y quedarse sin manera de que los huéspedes escribieran.

## Cambio

**Backend** (`PreparacionController.java`): el checklist pasa a calcular también el
contacto, con un canal solo (`contacto_email` **o** `contacto_telefono`) ya basta.

**Frontend** (`PaginaAdminPanel.tsx`): se borra la lista duplicada —el array `tareas`, su
`Aviso`, los tres `useEffect` de lectura y sus tres estados—. Queda **una sola lista**
(`ListaPreparacion`), que ya trae fallo/reintento resuelto.

**Tests**:
- `PreparacionTest.java`: test nuevo del contacto, y `@BeforeEach` de aislamiento (la base y
  el directorio de respaldos son `static`, así que lo que sembraba un test se lo encontraba
  el siguiente). Conteo 6 → 7.
- `PaginaAdminPanel.test.tsx`: tests nuevos —una sola lista, sin peticiones de más, y nada
  que mostrar cuando no queda pendiente.

## Verificación

### TDD

RED del backend, antes de implementar:

```
AssertionFailedError: expected: <7> but was: <6>
AssertionFailedError: el checklist debe incluir el contacto ==> expected: <true> but was: <false>
```

RED del front, antes de borrar el `Aviso`:

```
FAIL PaginaAdminPanel.test.tsx > una sola lista de pendientes, con su enlace cada uno
Tests  2 failed | 4 passed (6)
```

Durante la implementación cometí un error real: quité el `import { Link }` mientras el
componente todavía lo usaba seis veces en las tarjetas. La corrida dejó 6 tests en rojo; se
detectó y se reparó antes de medir nada.

También ajusté una expectativa mía mal puesta: el fixture tiene 7 puntos y uno hecho, así
que hay 6 pendientes, no 5. Se corrigió la expectativa, **no** el fixture.

### Pruebas

| Qué | Resultado |
| --- | --- |
| Dirigida `PaginaAdminPanel.test.tsx` | 6/6 |
| Suite frontend + typecheck | 249/249, `Type Errors: no errors` |
| Build Vite | `index-CQ30ZUkY.js`, 405.43 kB (gzip 116.16 kB) |
| `PreparacionTest` | 3/3 |

Salida completa en `mi-suite-front.log`, `mi-build.log`, `mi-backend-preparacion.log`.

### Navegador real (Chromium, stack aislado, endpoints sintéticos)

| Comprobación | Escritorio 1366×900 | Móvil 390×844 |
| --- | --- | --- |
| Una sola lista | sí | sí |
| Aviso duplicado "cosas pendientes" | no | no |
| Contacto dentro de la lista | sí | sí |
| Pendientes en pantalla | 6 | 6 |
| Enlaces | 5 (el respaldo no tiene URL) | 5 |
| Desbordamiento horizontal | no | no |

Con todo hecho: la lista no se muestra en absoluto (`pendientes: 0`), que es el comportamiento
correcto —un panel completo no necesita una lista vacía recordándolo.

Capturas (nombres nuevos, sin tocar las de otra sesión):
`lista-unica-escritorio.png`, `lista-unica-movil.png`, `lista-unica-completo.png`.

## Nota de convivencia

`docs/evidence/round-237/` tenía archivos de otra sesión y mi `panel-primera-vez.png` ya no
estaba. No borré nada de lo ajeno: mis capturas usan nombres distintos.

## Lo que sigue

El hotelero ahora ve una lista y un número que se corresponden. Sigue pendiente lo de
siempre: cargar en producción el GPS, los tipos de habitación y el contacto.
