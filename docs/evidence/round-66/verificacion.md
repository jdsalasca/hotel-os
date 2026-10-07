# Ronda 66 — Las altas no revientan y el alta es guiada (cierre Ronda 62)

Fecha: 2026-10-07.

## Dolor

1. `POST /api/admin/tipos` con `nombre: null` reventaba con NPE (`nombre.trim()` sin
   comprobar) → 500 en vez de 400.
2. Código de tipo o plan repetido → `SQLITE_CONSTRAINT_UNIQUE` cruda (500).
3. Habitación con `roomTypeId` inexistente → `SQLITE_CONSTRAINT_FOREIGNKEY` cruda (500).
4. `POST /api/admin/habitaciones/{id}/estado` con estado nulo/vacío/inválido → NPE o
   `IllegalArgumentException` sin mensaje útil.
5. Moneda solo aceptaba mayúsculas exactas (`cop` → 400) y aceptaba no-ISO de 3 letras
   (`COL` colaba); el panel ya manda select cerrado, el backend no lo garantizaba.
6. El panel mostraba 5 formularios sueltos en una página de 844 líneas: el hotelero no
   sabía por dónde empezar.

## Cambio

Backend (TDD: 6 tests nuevos en `InventarioServiceTest`, vistos fallar antes del fix):
- `crearTipo` exige nombre no vacío y traduce UNIQUE a
  `ya existe un tipo con el código X`.
- `crearHabitacion` comprueba que el tipo existe (`tipo de habitación no encontrado`) y
  traduce UNIQUE/FK a 400 con mensaje.
- `crearPlan` normaliza la moneda (`trim().toUpperCase()`) y la valida contra
  `java.util.Currency` (ISO 4217 real): `cop` → `COP`, `COL` → 400. Duplicado → 400.
- Controller: cuerpo ausente → 400; estado nulo/vacío/desconocido → 400
  `estado inválido: use ACTIVA o FUERA_DE_SERVICIO`. Nada se escribe cuando se rechaza.

Frontend (`PaginaAdminInventario.tsx` + `.asistente` en SCSS con tokens):
- Asistente en 4 pasos (Tipo → Habitaciones → Servicios → Plan) con `ol` + `aria-current`,
  foco al título del paso al avanzar, Anterior/Siguiente y pasos 2-3 deshabilitados hasta
  que exista un tipo. Solo se muestra el formulario del paso actual.
- Bloqueo, planes/descuentos, calendario y precios quedan fuera del asistente, intactos.

## Verificación real

```text
# Worktree limpio en HEAD + solo este diff (para no mezclar el WIP del colega):
InventarioServiceTest: Tests run: 37, Failures: 0, Errors: 0
Suite menos EscaladoSqliteTest: Tests run: 304, Failures: 0, Errors: 0, Skipped: 1
(EscaladoSqliteTest excluido solo por tiempo; en la corrida completa previa todo lo
anterior a él iba verde. Ver salida guardada en el chat de la ronda.)

# Checkout principal con el fix del colega (Ronda 63) ya integrado:
InventarioServiceTest: Tests run: 37, Failures: 0, Errors: 0, Skipped: 0
Suite menos EscaladoSqliteTest: 241 corridos (reportes top-level), 0 fallos, 0 errores,
1 omitida (la omitida preexistente de PermisosDeVolumenTest). Incluye LugaresTest del
colega en verde. BUILD SUCCESS.

docker compose build web  →  tsc --noEmit limpio + vite build ok (exit 0)
Playwright contra web:5174 + api:8080 (admin demo):
- login admin/admin → /admin/inventario muestra «Alta del hotel, paso a paso»
- clic Siguiente → «Paso 2: Habitaciones», foco en el título, aria-current="step"
- consola: solo 401 de chequeos de sesión + 1×403 en /api/amenidades porque la API
  en ejecución es anterior al commit que lo hizo público (en HEAD ya es permitAll)
Capturas: asistente-desktop.png, asistente-movil.png (esta carpeta)
```

## Archivos

- Tocados: `InventarioService.java`, `TarifaService.java`, `InventarioAdminController.java`,
  `InventarioServiceTest.java`, `PaginaAdminInventario.tsx`, `_componentes.scss`,
  `docs/plan.md`.
- Nuevos: esta carpeta (`verificacion.md`, `asistente-desktop.png`, `asistente-movil.png`).

## Riesgos y límites

- `cambiarEstado` sobre habitación inexistente sigue siendo 400 (no 404): contrato
  existente, no se cambió en esta ronda.
- La edición masiva de tarifas con preview (Etapa C resto) sigue pendiente.
- Trabajo del colega respetado: su WIP (lugares/V14/SecurityConfig) se verificó aparte
  en worktree limpio y no se tocó; su commit de Ronda 63 ya está en HEAD.
